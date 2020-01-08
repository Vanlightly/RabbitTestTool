import sys
import io
import subprocess
import threading
import time
import uuid
import os.path
import requests
import json
from random import randint
from UniqueConfiguration import UniqueConfiguration
from CommonConfiguration import CommonConfiguration
from printer import console_out

class BrokerActions:
    def __init__(self, deployer):
        self._action_status = dict()
        self._deployer = deployer
        self.actor = "BROKER_ACTIONS"

    def wait_for_msg_trigger(self, configurations, common_conf, trigger_at):
        # iterate over configurations
        for config_tag in configurations:
            unique_conf_list = configurations[config_tag]
            
            # iterate over configurations
            for p in range(len(unique_conf_list)):
                unique_conf = unique_conf_list[p]
                console_out(self.actor, f"Checking message total on node {unique_conf.node_number}")
                broker_ip = self.get_broker_ip(unique_conf.technology, unique_conf.node_number, common_conf.run_tag, common_conf.key_pair)
                msg_total = 0
                while(msg_total < trigger_at):
                    msg_total = self.get_cluster_message_total(broker_ip, common_conf.username, common_conf.password)
                    console_out(self.actor, f"Trigger at {trigger_at}. Currently {msg_total} messages on node {unique_conf.node_number}")
                    time.sleep(10)
                console_out(self.actor, f"Reached msg trigger on node {unique_conf.node_number}")

    def restart_all_brokers(self, configurations, common_conf):
        r_threads = list()
        for config_tag in configurations:
            console_out(self.actor, f"BROKER RESTART FOR configuration {config_tag}")
            unique_conf_list = configurations[config_tag]
            # iterate over configurations
            for p in range(len(unique_conf_list)):
                unique_conf = unique_conf_list[p]
                # iterate over nodes of this configuration
                for n in range(unique_conf.cluster_size):
                    node = int(unique_conf.node_number) + n
                    restart = threading.Thread(target=self.restart_broker, args=(unique_conf.technology, str(node), common_conf.run_tag, common_conf.key_pair,))
                    r_threads.append(restart)

        for rt in r_threads:
            rt.start()
        
        for rt in r_threads:
            rt.join()
        
        for config_tag in configurations:
            unique_conf_list = configurations[config_tag]
            
            for p in range(len(unique_conf_list)):
                unique_conf = unique_conf_list[p]
                for n in range(unique_conf.cluster_size):
                    node = int(unique_conf.node_number) + n
                    status_id = f"{unique_conf.technology}{node}"
                
                    if self._action_status[status_id] != "success":
                        console_out(self.actor, f"Broker restart failed for node {unique_conf.technology}{node}")
                        if not common_conf.no_deploy:
                            self._deployer.teardown_all(configurations, common_conf.run_tag, False)

    def restart_one_broker(self, configurations, common_conf):
        r_threads = list()
        for config_tag in configurations:
            console_out(self.actor, f"BROKER RESTART FOR configuration {config_tag}")
            unique_conf_list = configurations[config_tag]
            # iterate over configurations
            for p in range(len(unique_conf_list)):
                unique_conf = unique_conf_list[p]
                restart = threading.Thread(target=self.restart_broker, args=(unique_conf.technology, str(unique_conf.node_number), common_conf.run_tag, common_conf.key_pair,))
                r_threads.append(restart)

        for rt in r_threads:
            rt.start()
        
        for rt in r_threads:
            rt.join()
        
        for config_tag in configurations:
            unique_conf_list = configurations[config_tag]
            
            for p in range(len(unique_conf_list)):
                unique_conf = unique_conf_list[p]
                status_id = f"{unique_conf.technology}{unique_conf.node_number}"
                if self._action_status[status_id] != "success":
                    console_out(self.actor, f"Broker restart failed for node {unique_conf.technology}{unique_conf.node_number}")
                    if not common_conf.no_deploy:
                        self._deployer.teardown_all(configurations, common_conf.run_tag, False)
                

    def restart_broker(self, technology, node, run_tag, key_pair):
        status_id = technology + node
        exit_code = subprocess.call(["bash", "restart-broker.sh", 
                        key_pair, 
                        node, 
                        run_tag,
                        technology])
        
        if exit_code != 0:
            console_out(self.actor, f"Restart of broker on node {node} failed with exit code {exit_code}")
            self._action_status[status_id] = "failed"   
        else:
            self._action_status[status_id] = "success"

    def get_broker_ip(self, technology, node, run_tag, key_pair):
        broker_ip = ""
        attempts = 0
        while broker_ip == "" and attempts < 3:
            attempts += 1
            process = subprocess.Popen(["bash", "get_broker_ip.sh", 
                            key_pair, 
                            node, 
                            run_tag,
                            technology], stdout=subprocess.PIPE)
            
            for line in io.TextIOWrapper(process.stdout, encoding="utf-8"):
                if not line:
                    break
                
                if line.startswith("BROKER_IP="):
                    broker_ip = line.rstrip().replace("BROKER_IP=","")
                    break

            if broker_ip == "":
                time.sleep(5)

        return broker_ip

    def get_cluster_message_total(self, broker_ip, username, password):
        res = requests.get(f"http://{broker_ip}:15672/api/overview",
                auth=(username,password))

        overview_json = res.json()
        queue_totals = overview_json["queue_totals"]
        
        if "messages" in queue_totals:
            return queue_totals["messages"]
        else:
            return 0