#!/usr/bin/env python

import sys
import subprocess
import threading
import time
import uuid
import os.path
from random import randint
from collections import namedtuple

from command_args import get_args, get_optional_arg, get_mandatory_arg, get_mandatory_arg_no_print, is_true, get_mandatory_arg_validated

class Deployer:
    def __init__(self):
        self.deploy_status = ""

    def update_single(self, ba, sa, run_tag):
        status_id = ba.technology + ba.node
        exit_code = subprocess.call(["bash", "update-benchmark.sh", sa.key_pair, ba.node, ba.technology, run_tag], cwd="../deploy/aws")
        if exit_code != 0:
            print(f"update {ba.node} failed with exit code {exit_code}")
            self.deploy_status[status_id] = "failed"   
        else:
            self.deploy_status[status_id] = "success"

    def deploy_single(self, ba, sa, run_tag):
        status_id = ba.technology + ba.node
        self.deploy_status[status_id] = "started"
        volume_type = ba.volume.split("-")[1]
        exit_code = subprocess.call(["bash", "deploy-single-broker.sh", sa.ami, ba.broker_version, ba.core_count,ba.filesystem, ba.instance, sa.key_pair, sa.loadgen_instance, sa.loadgen_sg, ba.node, run_tag, sa.broker_sg, sa.subnet, ba.technology, ba.tenancy, ba.threads_per_core, ba.vars_file, ba.volume_size, volume_type], cwd="../deploy/aws")

        if exit_code != 0:
            print(f"deploy {ba.node} failed with exit code {exit_code}")
            self.deploy_status[status_id] = "failed"   
        else:
            self.deploy_status[status_id] = "success"

    def deploy_rabbitmq_cluster(self, ba, sa, run_tag):
        status_id = ba.technology + ba.node
        self.deploy_status[status_id] = "started"
        volume_type = ba.volume.split("-")[1]
        
        exit_code = subprocess.call(["bash", "deploy-rmq-cluster-instances.sh", sa.ami, str(ba.cluster_size), ba.core_count, ba.instance, sa.key_pair, sa.loadgen_instance, sa.loadgen_sg, ba.node, run_tag, sa.broker_sg, sa.subnet, ba.tenancy, ba.threads_per_core, ba.volume_size, volume_type], cwd="../deploy/aws")
        if exit_code != 0:
            print(f"deploy {ba.node} failed with exit code {exit_code}")
            self.deploy_status[status_id] = "failed" 
            return  
        
        master_node = int(ba.node)
        node_range_start = master_node
        node_range_end = master_node + int(ba.cluster_size) - 1
        
        # deploy master
        exit_code = subprocess.call(["bash", "deploy-rmq-cluster-broker.sh", sa.ami, ba.broker_version, ba.core_count, ba.filesystem, ba.instance, sa.key_pair, str(master_node), str(node_range_end), str(node_range_start), "master", run_tag, sa.broker_sg, sa.subnet, ba.tenancy, ba.threads_per_core, ba.vars_file, ba.volume_size, volume_type], cwd="../deploy/aws")

        if exit_code != 0:
            print(f"deploy {ba.node} failed with exit code {exit_code}")
            self.deploy_status[status_id] = "failed"   
            return

        # deploy joinees
        joinee_threads = list()
        for node in range(node_range_start+1, node_range_end+1):
            deploy = threading.Thread(target=deploy_joinee, args=(ba, sa, run_tag, status_id, volume_type, node, node_range_start, node_range_end))
            joinee_threads.append(deploy)

        for jt in joinee_threads:
            jt.start()
        
        for jt in joinee_threads:
            jt.join()

        # deploy benchmark
        if self.deploy_status[status_id] != "failed":
            exit_code = subprocess.call(["bash", "deploy-benchmark.sh", sa.key_pair, str(master_node), "rabbitmq", run_tag], cwd="../deploy/aws")

            if exit_code != 0:
                print(f"deploy {ba.node} failed with exit code {exit_code}")
                self.deploy_status[status_id] = "failed"   
            else:
                self.deploy_status[status_id] = "success"
    
    

    def deploy_joinee(self, ba, sa, run_tag, status_id, volume_type, node, node_range_start, node_range_end):
        exit_code = subprocess.call(["bash", "deploy-rmq-cluster-broker.sh", sa.ami, ba.broker_version, ba.core_count, ba.filesystem, ba.instance, sa.key_pair, str(node), str(node_range_end), str(node_range_start), "joinee", run_tag, sa.broker_sg, sa.subnet, ba.tenancy, ba.threads_per_core, ba.vars_file, ba.volume_size, volume_type], cwd="../deploy/aws")    
        if exit_code != 0:
            print(f"deploy of joinee rabbitmq{node} failed with exit code {exit_code}")
            self.deploy_status[status_id] = "failed"   
    
    def teardown(self, technology, node, run_tag, no_destroy):
        if no_destroy:
            print("No teardown as --no-destroy set to true")
        else:
            terminated = False
            while not terminated:
                exit_code = subprocess.call(["bash", "terminate-instances.sh", technology, node, run_tag], cwd="../deploy/aws")
                if exit_code == 0:
                    terminated = True
                else:
                    print("teardown failed, will retry in 1 minute")
                    time.sleep(60)

    def teardown_all(self, broker1_args_list, run_tag, no_destroy):
        if no_destroy:
            print("No teardown as --no-destroy set to true")
        else:
            print("Terminating all servers")
            for p in range(len(broker1_args_list)):
                ba1 = ba1_list[p]

                for n in range(0, ba1.cluster_size):
                    node_num = int(ba1.node) + n
                    teardown(ba1.technology, str(node_num), run_tag, no_destroy)
            print("All servers terminated")
            exit(1)
   
    def parallel_deploy(self, broker1_args_list, sa, run_tag, no_deploy):
        d_threads = list()
        for i in range(len(broker1_args_list)):
            ba1 = broker1_args_list[i]
            if no_deploy:
                deploy1 = threading.Thread(target=update_single, args=(ba1, sa, run_tag,))
            else:
                if ba1.cluster_size == 1:
                    deploy1 = threading.Thread(target=deploy_single, args=(ba1, sa, run_tag,))
                else:
                    deploy1 = threading.Thread(target=deploy_rabbitmq_cluster, args=(ba1, sa, run_tag,))

            d_threads.append(deploy1)

        for dt in d_threads:
            dt.start()
        
        for dt in d_threads:
            dt.join()
        
        for p in range(len(broker1_args_list)):
            ba1 = broker1_args_list[p]
            status_id1 = ba1.technology + ba1.node
        
            if self.deploy_status[status_id1] != "success":
                print(f"Deployment failed for node {ba1.technology}{ba1.node}")
                if not no_deploy:
                    teardown_all(broker1_args_list, run_tag, False)
                    exit(1)