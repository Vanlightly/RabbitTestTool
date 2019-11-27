import sys
import subprocess
import threading
import time
import uuid
import os.path
from datetime import datetime
from random import randint
from UniqueConfiguration import UniqueConfiguration
from CommonConfiguration import CommonConfiguration
from printer import console_out, console_out_exception

class Deployer:
    def __init__(self):
        self._deploy_status = dict()
        self.actor = "DEPLOYER"

    def deploy(self, runner, configurations, common_conf):
        if common_conf.run_tag == "none":
            common_conf.run_tag = str(randint(1, 99999))
        
        self.parallel_deploy(configurations, common_conf)
        
        if common_conf.background_topology_file != "none":
            runner.run_background_load_across_runs(configurations, common_conf)

    def get_deploy_status(self):
        return self._deploy_status

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

    def teardown_all(self, configurations, key_pair, run_tag, no_destroy):
        try:
            console_out(self.actor, f"Getting logs")
            start_node, end_node = self.get_start_end_nodes(configurations)
            self.get_logs(key_pair, run_tag, start_node, end_node)
            console_out(self.actor, f"Logs retrieved")
        except Exception as e:
            console_out_exception(self.actor, "Failed retrieving logs", e)
        
        if no_destroy:
            console_out(self.actor, "No teardown as --no-destroy set to true")
        else:
            console_out(self.actor, "Terminating all servers")
            
            for config_tag in configurations:
                console_out(self.actor, f"TEARDOWN FOR configuration {config_tag}")
                unique_conf_list = configurations[config_tag]
                for p in range(len(unique_conf_list)):
                    unique_conf = unique_conf_list[p]

                    for n in range(0, unique_conf.cluster_size):
                        node_num = int(unique_conf.node_number) + n
                        console_out(self.actor, f"TEARDOWN FOR node {node_num}")
                        self.teardown(unique_conf.technology, str(node_num), run_tag, no_destroy)
                console_out(self.actor, "All servers terminated")
            exit(1)    

    def get_start_end_nodes(self, configurations):
        start_node = 0
        last_node = 0
        counter = 0
        for config_tag in configurations:
            unique_conf_list = configurations[config_tag]
            for p in range(len(unique_conf_list)):
                unique_conf = unique_conf_list[p]

                if counter == 0:
                    start_node = unique_conf.node_number
                
                last_node = int(unique_conf.node_number) + int(unique_conf.cluster_size) - 1
                counter += 1

        return start_node, last_node

   
    def parallel_deploy(self, configurations, common_conf):
        d_threads = list()

        for config_tag in configurations:
            unique_conf_list = configurations[config_tag]
            for i in range(len(unique_conf_list)):
                unique_conf = unique_conf_list[i]
                if common_conf.no_deploy:
                    deploy = threading.Thread(target=self.update_single, args=(unique_conf, common_conf,))
                else:
                    if unique_conf.cluster_size == 1:
                        deploy = threading.Thread(target=self.deploy_single, args=(unique_conf, common_conf,))
                    else:
                        deploy = threading.Thread(target=self.deploy_rabbitmq_cluster, args=(unique_conf, common_conf,))

                d_threads.append(deploy)

        for dt in d_threads:
            dt.start()
        
        for dt in d_threads:
            dt.join()
        
        for config_tag in configurations:
            unique_conf_list = configurations[config_tag]
            
            for p in range(len(unique_conf_list)):
                unique_conf = unique_conf_list[p]
                status_id1 = unique_conf.technology + unique_conf.node_number
            
                if self._deploy_status[status_id1] != "success":
                    console_out(self.actor, f"Deployment failed for node {unique_conf.technology}{unique_conf.node_number}")
                    if not common_conf.no_deploy:
                        self.teardown_all(configurations, common_conf.key_pair, common_conf.run_tag, False)

    def get_logs(self, key_pair, run_tag, start_node, end_node):
        target_dir = "logs/" + datetime.now().strftime("%Y%m%d%H%M")
        subprocess.call(["bash", "get-logs.sh", 
                        key_pair, 
                        str(start_node),
                        str(end_node),
                        str(run_tag),
                        "rabbitmq", 
                        target_dir ])