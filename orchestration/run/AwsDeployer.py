import sys
import subprocess
import threading
import time
import uuid
import os.path
from datetime import datetime
from random import randint
from Deployer import Deployer
from UniqueConfiguration import UniqueConfiguration
from CommonConfiguration import CommonConfiguration
from printer import console_out, console_out_exception

class AwsDeployer(Deployer):
    def __init__(self):
        super().__init__()

    def update_single(self, unique_conf, common_conf):
        status_id = unique_conf.technology + unique_conf.node_number
        exit_code = subprocess.call(["bash", "update-benchmark.sh", 
                        common_conf.key_pair, 
                        unique_conf.node_number, 
                        unique_conf.technology, 
                        common_conf.run_tag], cwd="../deploy/aws")
        if exit_code != 0:
            console_out(self.actor, f"update {unique_conf.node_number} failed with exit code {exit_code}")
            self._deploy_status[status_id] = "failed"   
        else:
            self._deploy_status[status_id] = "success"

    def deploy_single(self, unique_conf, common_conf):
        status_id = unique_conf.technology + unique_conf.node_number
        self._deploy_status[status_id] = "started"
        volume_type = unique_conf.volume.split("-")[1]
        exit_code = subprocess.call(["bash", "deploy-single-broker.sh", 
                            common_conf.ami, 
                            unique_conf.broker_version, 
                            unique_conf.core_count,
                            unique_conf.filesystem, 
                            unique_conf.generic_unix_url,
                            unique_conf.instance, 
                            common_conf.key_pair, 
                            common_conf.loadgen_instance, 
                            common_conf.loadgen_sg, 
                            common_conf.log_level,
                            unique_conf.node_number, 
                            common_conf.run_tag, 
                            common_conf.broker_sg, 
                            common_conf.subnet, 
                            unique_conf.technology, 
                            unique_conf.tenancy, 
                            unique_conf.threads_per_core, 
                            unique_conf.vars_file, 
                            unique_conf.volume_size, 
                            volume_type], cwd="../deploy/aws")

        if exit_code != 0:
            console_out(self.actor, f"deploy {unique_conf.node_number} failed with exit code {exit_code}")
            self._deploy_status[status_id] = "failed"   
        else:
            self._deploy_status[status_id] = "success"

    def deploy_rabbitmq_cluster(self, unique_conf, common_conf):
        status_id = unique_conf.technology + unique_conf.node_number
        self._deploy_status[status_id] = "started"
        volume_type = unique_conf.volume.split("-")[1]
        
        exit_code = subprocess.call(["bash", "deploy-rmq-cluster-instances.sh", 
                                common_conf.ami, 
                                str(unique_conf.cluster_size), 
                                unique_conf.core_count, 
                                unique_conf.instance, 
                                common_conf.key_pair, 
                                common_conf.loadgen_instance, 
                                common_conf.loadgen_sg, 
                                unique_conf.node_number, 
                                common_conf.run_tag, 
                                common_conf.broker_sg, 
                                common_conf.subnet, 
                                unique_conf.tenancy, 
                                unique_conf.threads_per_core, 
                                unique_conf.volume_size, 
                                volume_type], cwd="../deploy/aws")
        if exit_code != 0:
            console_out(self.actor, f"deploy {unique_conf.node_number} failed with exit code {exit_code}")
            self._deploy_status[status_id] = "failed" 
            return  
        
        master_node = int(unique_conf.node_number)
        node_range_start = master_node
        node_range_end = master_node + int(unique_conf.cluster_size) - 1
        
        # deploy master
        exit_code = subprocess.call(["bash", "deploy-rmq-cluster-broker.sh", 
                                common_conf.ami, 
                                unique_conf.broker_version, 
                                unique_conf.core_count, 
                                unique_conf.filesystem, 
                                unique_conf.generic_unix_url,
                                unique_conf.instance, 
                                common_conf.key_pair, 
                                common_conf.log_level,
                                str(master_node), 
                                str(node_range_end), 
                                str(node_range_start), 
                                "master", 
                                common_conf.run_tag, 
                                common_conf.broker_sg, 
                                common_conf.subnet, 
                                unique_conf.tenancy, 
                                unique_conf.threads_per_core, 
                                unique_conf.vars_file, 
                                unique_conf.volume_size, 
                                volume_type], cwd="../deploy/aws")

        if exit_code != 0:
            console_out(self.actor, f"deploy {unique_conf.node_number} failed with exit code {exit_code}")
            self._deploy_status[status_id] = "failed"   
            return

        # deploy joinees
        joinee_threads = list()
        for node in range(node_range_start+1, node_range_end+1):
            deploy = threading.Thread(target=self.deploy_joinee, args=(unique_conf, common_conf, status_id, volume_type, node, node_range_start, node_range_end))
            joinee_threads.append(deploy)

        for jt in joinee_threads:
            jt.start()
        
        for jt in joinee_threads:
            jt.join()

        # deploy benchmark
        if self._deploy_status[status_id] != "failed":
            exit_code = subprocess.call(["bash", "deploy-benchmark.sh", 
                                common_conf.key_pair, 
                                str(master_node), 
                                "rabbitmq", 
                                common_conf.run_tag], cwd="../deploy/aws")

            if exit_code != 0:
                console_out(self.actor, f"deploy {unique_conf.node_number} failed with exit code {exit_code}")
                self._deploy_status[status_id] = "failed"   
            else:
                self._deploy_status[status_id] = "success"
    
    

    def deploy_joinee(self, unique_conf, common_conf, status_id, volume_type, node, node_range_start, node_range_end):
        exit_code = subprocess.call(["bash", "deploy-rmq-cluster-broker.sh", 
                                common_conf.ami, 
                                unique_conf.broker_version, 
                                unique_conf.core_count, 
                                unique_conf.filesystem, 
                                unique_conf.generic_unix_url,
                                unique_conf.instance, 
                                common_conf.key_pair, 
                                common_conf.log_level,
                                str(node), 
                                str(node_range_end), 
                                str(node_range_start), 
                                "joinee", 
                                common_conf.run_tag, 
                                common_conf.broker_sg, 
                                common_conf.subnet, 
                                unique_conf.tenancy, 
                                unique_conf.threads_per_core, 
                                unique_conf.vars_file, 
                                unique_conf.volume_size, 
                                volume_type], cwd="../deploy/aws")    
        if exit_code != 0:
            console_out(self.actor, f"deploy of joinee rabbitmq{node} failed with exit code {exit_code}")
            self._deploy_status[status_id] = "failed"   
    
    def teardown(self, technology, node, run_tag, no_destroy):
        if no_destroy:
            console_out(self.actor, "No teardown as --no-destroy set to true")
        else:
            terminated = False
            while not terminated:
                exit_code = subprocess.call(["bash", "terminate-instances.sh", technology, node, run_tag], cwd="../deploy/aws")
                if exit_code == 0:
                    terminated = True
                else:
                    console_out(self.actor, "teardown failed, will retry in 1 minute")
                    time.sleep(60)

    def get_logs(self, common_conf, start_node, end_node):
        target_dir = "logs/" + datetime.now().strftime("%Y%m%d%H%M")
        subprocess.call(["bash", "get-logs.sh",
                        common_conf.key_pair,
                        str(start_node),
                        str(end_node),
                        str(common_conf.run_tag),
                        "rabbitmq",
                        target_dir ])