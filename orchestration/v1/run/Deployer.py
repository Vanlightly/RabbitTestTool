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

        # if common_conf.background_topology_file != "none":
        #     runner.run_background_load_across_runs(configurations, common_conf)

    def get_deploy_status(self):
        return self._deploy_status

    def update_single(self, unique_conf, common_conf):
        raise NotImplementedError

    def deploy_single(self, unique_conf, common_conf):
        raise NotImplementedError

    def deploy_rabbitmq_cluster(self, unique_conf, common_conf):
        raise NotImplementedError

    def teardown_ec2(self, technology, node, run_tag, no_destroy):
        raise NotImplementedError

    def teardown_managed_k8s(self, unique_conf, no_destroy):
        raise NotImplementedError

    def teardown_loadgen(self, unique_conf, common_conf, no_destroy):
        pass

    def teardown_all(self, configurations, common_conf, no_destroy):
        if no_destroy:
            console_out(self.actor, "No teardown as --no-destroy set to true")
            self.get_logs_of_all_configs(common_conf, configurations)
        else:
            console_out(self.actor, "Terminating all servers")
            self.get_logs_of_all_configs(common_conf, configurations)

            for config_tag in configurations:
                console_out(self.actor, f"TEARDOWN FOR configuration {config_tag}")
                unique_conf_list = configurations[config_tag]
                for p in range(len(unique_conf_list)):
                    unique_conf = unique_conf_list[p]

                    if unique_conf.deployment == "ec2":
                        for n in range(0, unique_conf.cluster_size):
                            node_num = int(unique_conf.node_number) + n
                            console_out(self.actor, f"TEARDOWN FOR node {node_num}")
                            self.teardown_ec2(unique_conf.technology,
                                        str(node_num),
                                        common_conf.run_tag,
                                        no_destroy)
                        console_out(self.actor, f"TEARDOWN FOR {unique_conf.config_tag} loadgen")
                        self.teardown_loadgen(unique_conf,
                                            common_conf,
                                            no_destroy)
                    elif unique_conf.deployment == "eks" or unique_conf.deployment == "gke":
                        self.teardown_managed_k8s(unique_conf, no_destroy)
                    else:
                        raise Exception(f"Invalid deployment type: {unique_conf.deployment}")
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

    def get_start_end_nodes_of_config(self, unique_conf):
        start_node = unique_conf.node_number
        last_node = int(unique_conf.node_number) + int(unique_conf.cluster_size) - 1
        
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
                        self.teardown_all(configurations, common_conf, False)

    def get_logs_of_all_configs(self, common_conf, configurations):
        for config_tag in configurations:
            unique_conf_list = configurations[config_tag]
            for p in range(len(unique_conf_list)):
                unique_conf = unique_conf_list[p]

                if unique_conf.deployment == "ec2":
                    try:
                        start_node, end_node = self.get_start_end_nodes_of_config(unique_conf)
                        self.get_logs(common_conf, unique_conf.logs_volume, start_node, end_node)
                    except Exception as e:
                        console_out_exception(self.actor, "Failed retrieving logs", e)
                elif unique_conf.deployment == "eks" or unique_conf.deployment == "gke":
                    console_out(self.actor, "Log gathering not yet supported for EKS/GKE")
                else:
                    raise Exception(f"Invalid deployment type: {unique_conf.deployment}")
                
    
    def get_logs(self, common_conf, logs_volume, start_node, end_node):
        raise NotImplementedError

    def update_broker_config_on_all(self, configurations, common_conf, broker_config):
        for config_tag in configurations:
            unique_conf_list = configurations[config_tag]
            for p in range(len(unique_conf_list)):
                unique_conf = unique_conf_list[p]

                start_node, end_node = self.get_start_end_nodes_of_config(unique_conf)
                self.update_broker_config(common_conf, start_node, end_node, broker_config)
