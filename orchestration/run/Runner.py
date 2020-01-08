import sys
import subprocess
import threading
import time
import uuid
import os.path
from random import randint
from collections import namedtuple
from printer import console_out

class Runner:
    def __init__(self):
        self._benchmark_status = dict()
        self.actor = "RUNNER"

    def get_benchmark_statuses(self):
        return self._benchmark_status

    def get_benchmark_status(self, status_id):
        return self._benchmark_status[status_id]
    
    def run_benchmark(self, unique_conf, common_conf, playlist_entry, policies, run_ordinal):
        status_id = unique_conf.technology + unique_conf.node_number

        nodes = ""
        for x in range(int(unique_conf.cluster_size)):
            comma = ","
            if x == 0:
                comma = ""

            node_number = int(unique_conf.node_number) + x
            nodes = f"{nodes}{comma}rabbit@rabbitmq{node_number}"

        self._benchmark_status[status_id] = "started"
        exit_code = subprocess.call(["bash", "run-logged-aws-benchmark.sh", 
                                unique_conf.node_number, 
                                common_conf.key_pair, 
                                unique_conf.technology, 
                                unique_conf.broker_version, 
                                unique_conf.instance, 
                                unique_conf.volume, 
                                unique_conf.filesystem, 
                                common_conf.hosting, 
                                unique_conf.tenancy, 
                                common_conf.password, 
                                common_conf.postgres_url, 
                                common_conf.postgres_user, 
                                common_conf.postgres_pwd, 
                                playlist_entry.topology, 
                                common_conf.run_id, 
                                common_conf.username, 
                                common_conf.password, 
                                common_conf.run_tag, 
                                unique_conf.core_count, 
                                unique_conf.threads_per_core, 
                                unique_conf.config_tag, 
                                str(unique_conf.cluster_size), 
                                unique_conf.no_tcp_delay, 
                                policies, 
                                str(common_conf.override_step_seconds), 
                                str(common_conf.override_step_repeat), 
                                nodes, 
                                str(common_conf.override_step_msg_limit), 
                                common_conf.override_broker_hosts, 
                                unique_conf.pub_connect_to_node,
                                unique_conf.con_connect_to_node,
                                common_conf.mode,
                                str(playlist_entry.grace_period_sec),
                                str(run_ordinal),
                                common_conf.tags,
                                playlist_entry.get_topology_variables(),
                                playlist_entry.get_policy_variables()])

        if exit_code != 0:
            console_out(self.actor, f"Benchmark {unique_conf.node_number} failed")
            self._benchmark_status[status_id] = "failed"
        else:
            self._benchmark_status[status_id] = "success"

    def run_background_load_across_runs(self, configurations, common_conf):
        bg_threads = list()

        for config_tag in configurations:
            unique_conf_list = configurations[config_tag]
            
            for p in range(common_conf.parallel_count):
                unique_conf = unique_conf_list[p]
                t1 = threading.Thread(target=self.run_background_load, args=(unique_conf, common_conf,))
                bg_threads.append(t1)

        for bt in bg_threads:
            bt.start()

        time.sleep(10)
        console_out(self.actor, f"Delaying start of benchmark by {common_conf.background_delay} seconds")
        time.sleep(common_conf.background_delay)

    def run_background_load(self, unique_conf, common_conf):
        console_out(self.actor, f"Starting background load for {unique_conf.node_number}")
        status_id = unique_conf.technology + unique_conf.node_number

        broker_user = "benchmark"
        broker_password = common_conf.password
        topology = common_conf.background_topology_file
        policies = common_conf.background_policies_file
        step_seconds = str(common_conf.background_step_seconds)
        step_repeat = str(common_conf.background_step_repeat)

        nodes = ""
        for x in range(int(unique_conf.cluster_size)):
            comma = ","
            if x == 0:
                comma = ""

            node_number = int(unique_conf.node_number) + x
            nodes = f"{nodes}{comma}{node_number}"

        self._benchmark_status[status_id] = "started"
        subprocess.Popen(["bash", "run-background-load-aws.sh", 
                        broker_user, 
                        broker_password, 
                        str(unique_conf.cluster_size), 
                        common_conf.key_pair, 
                        unique_conf.node_number, 
                        nodes, 
                        policies, 
                        step_seconds, 
                        step_repeat, 
                        common_conf.run_tag, 
                        unique_conf.technology, 
                        topology, 
                        unique_conf.broker_version])