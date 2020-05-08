import sys
import io
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
        status_id = f"{unique_conf.technology}{unique_conf.node_number}"
        self._deploy_status[status_id] = "started"

        node_number = unique_conf.node_number
        deploy_threads = list()
        deploy_primary = threading.Thread(target=self.deploy_single_broker, args=(status_id, unique_conf, common_conf, node_number))
        deploy_threads.append(deploy_primary)

        ds_node = 0
        if common_conf.federation_enabled:
            ds_node = int(unique_conf.node_number) + 100
            print(f"FED ENABLED: {ds_node}")
            deploy_ds = threading.Thread(target=self.deploy_single_broker, args=(status_id, unique_conf, common_conf, ds_node))
            deploy_threads.append(deploy_ds)

        for dt in deploy_threads:
            dt.start()
        
        for dt in deploy_threads:
            dt.join()

        if common_conf.federation_enabled:
            self.add_upstream_hosts(status_id, common_conf, ds_node, ds_node, unique_conf.node_number, unique_conf.node_number)


    def deploy_single_broker(self, status_id, unique_conf, common_conf, node_number):
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
                            str(node_number), 
                            common_conf.run_tag, 
                            common_conf.broker_sg, 
                            common_conf.subnet, 
                            unique_conf.technology, 
                            unique_conf.tenancy, 
                            unique_conf.threads_per_core, 
                            unique_conf.vars_file, 
                            unique_conf.data_volume,
                            unique_conf.logs_volume,
                            unique_conf.quorum_volume,
                            unique_conf.wal_volume,
                            unique_conf.volume1_size,
                            unique_conf.volume1_mountpoint,
                            unique_conf.volume2_size,
                            unique_conf.volume2_mountpoint,
                            unique_conf.volume3_size,
                            unique_conf.volume3_mountpoint,
                            volume_type], cwd="../deploy/aws")

        if exit_code != 0:
            console_out(self.actor, f"deploy {node_number} failed with exit code {exit_code}")
            self._deploy_status[status_id] = "failed"   
        else:
            self._deploy_status[status_id] = "success"

    def deploy_rabbitmq_cluster(self, unique_conf, common_conf):
        master_node = int(unique_conf.node_number)
        node_range_start = master_node
        node_range_end = master_node + int(unique_conf.cluster_size) - 1

        status_id = f"{unique_conf.technology}{master_node}"
        self._deploy_status[status_id] = "started"

        deploy_threads = list()
        deploy_primary = threading.Thread(target=self.deploy_single_rabbitmq_cluster, args=(status_id, unique_conf, common_conf, master_node, node_range_start, node_range_end))
        deploy_threads.append(deploy_primary)

        if common_conf.federation_enabled:
            ds_master_node = int(unique_conf.node_number) + 100
            ds_node_range_start = master_node
            ds_node_range_end = master_node + int(unique_conf.cluster_size) - 1

            print(f"FED ENABLED: {ds_master_node} {ds_node_range_start} {ds_node_range_end}")

            deploy_ds = threading.Thread(target=self.deploy_single_rabbitmq_cluster, args=(status_id, unique_conf, common_conf, ds_master_node, ds_node_range_start, ds_node_range_end))
            deploy_threads.append(deploy_ds)

        for dt in deploy_threads:
            dt.start()
        
        for dt in deploy_threads:
            dt.join()

        if common_conf.federation_enabled:
            self.add_upstream_hosts(common_conf, ds_node_range_start, ds_node_range_end, node_range_start, node_range_end)

        

    def deploy_single_rabbitmq_cluster(self, status_id, unique_conf, common_conf, master_node, node_range_start, node_range_end):
        volume_type = unique_conf.volume.split("-")[1]

        exit_code = subprocess.call(["bash", "deploy-rmq-cluster-instances.sh", 
                                common_conf.ami, 
                                str(unique_conf.cluster_size), 
                                unique_conf.core_count, 
                                unique_conf.instance, 
                                common_conf.key_pair, 
                                common_conf.loadgen_instance, 
                                common_conf.loadgen_sg, 
                                str(master_node), 
                                common_conf.run_tag, 
                                common_conf.broker_sg, 
                                common_conf.subnet, 
                                unique_conf.tenancy, 
                                unique_conf.threads_per_core, 
                                unique_conf.volume1_size,
                                unique_conf.volume2_size,
                                unique_conf.volume3_size,
                                volume_type], cwd="../deploy/aws")
        if exit_code != 0:
            console_out(self.actor, f"deploy {master_node} failed with exit code {exit_code}")
            self._deploy_status[status_id] = "failed" 
            return  
        
        # deploy master
        self.deploy_master(status_id, unique_conf, common_conf, volume_type, master_node, node_range_start, node_range_end)

        # deploy joinees in parallel
        joinee_threads = list()
        for node in range(node_range_start+1, node_range_end+1):
            deploy = threading.Thread(target=self.deploy_joinee, args=(status_id, unique_conf, common_conf, volume_type, node, node_range_start, node_range_end))
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
    
    
    def deploy_master(self, status_id, unique_conf, common_conf, volume_type, node, node_range_start, node_range_end):
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
                                "master", 
                                common_conf.run_tag, 
                                common_conf.broker_sg, 
                                common_conf.subnet, 
                                unique_conf.tenancy, 
                                unique_conf.threads_per_core, 
                                unique_conf.vars_file, 
                                unique_conf.data_volume,
                                unique_conf.logs_volume,
                                unique_conf.quorum_volume,
                                unique_conf.wal_volume,
                                unique_conf.volume1_size,
                                unique_conf.volume1_mountpoint,
                                unique_conf.volume2_size,
                                unique_conf.volume2_mountpoint,
                                unique_conf.volume3_size,
                                unique_conf.volume3_mountpoint,
                                volume_type], cwd="../deploy/aws")
        if exit_code != 0:
            console_out(self.actor, f"deploy of master rabbitmq{node} failed with exit code {exit_code}")
            self._deploy_status[status_id] = "failed"
    
    def deploy_joinee(self, status_id, unique_conf, common_conf, volume_type, node, node_range_start, node_range_end):
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
                                unique_conf.data_volume,
                                unique_conf.logs_volume,
                                unique_conf.quorum_volume,
                                unique_conf.wal_volume,
                                unique_conf.volume1_size,
                                unique_conf.volume1_mountpoint,
                                unique_conf.volume2_size,
                                unique_conf.volume2_mountpoint,
                                unique_conf.volume3_size,
                                unique_conf.volume3_mountpoint,
                                volume_type], cwd="../deploy/aws")    
        if exit_code != 0:
            console_out(self.actor, f"deploy of joinee rabbitmq{node} failed with exit code {exit_code}")
            self._deploy_status[status_id] = "failed"   
    
    def add_upstream_hosts(self, status_id, common_conf, 
                            downstream_range_start, 
                            downstream_range_end,
                            upstream_range_start, 
                            upstream_range_end):
        exit_code = subprocess.call(["bash", "add-upstream-hosts.sh", 
                            str(downstream_range_end),
                            str(downstream_range_start), 
                            common_conf.key_pair,
                            common_conf.run_tag,
                            str(upstream_range_end),
                            str(upstream_range_start)], cwd="../deploy/aws")  

        if exit_code != 0:
            console_out(self.actor, f"add-upstream-hosts failed with exit code {exit_code}")
            self._deploy_status[status_id] = "failed"   
        else:
            self._deploy_status[status_id] = "success"

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

    def get_logs(self, common_conf, logs_volume, start_node, end_node):
        target_dir = "logs/" + datetime.now().strftime("%Y%m%d%H%M")
        subprocess.call(["bash", "get-logs.sh",
                        common_conf.key_pair,
                        logs_volume,
                        str(start_node),
                        str(end_node),
                        str(common_conf.run_tag),
                        "rabbitmq",
                        target_dir])

    def update_broker_config(self, common_conf, start_node, end_node, broker_config):
        quorum_commands_soft_limit = "0"
        if "quorum_commands_soft_limit" in broker_config:
            quorum_commands_soft_limit = broker_config["quorum_commands_soft_limit"]
        
        wal_max_batch_size = "0"
        if "wal_max_batch_size" in broker_config:
            wal_max_batch_size = broker_config["wal_max_batch_size"]

        subprocess.call(["bash", "update-rabbitmq-config.sh",
                        common_conf.key_pair,
                        str(start_node),
                        str(end_node),
                        str(common_conf.run_tag),
                        quorum_commands_soft_limit,
                        wal_max_batch_size], cwd="../deploy/aws")
