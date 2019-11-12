import sys
import subprocess
import threading
import time
import uuid
import os.path
from random import randint
from collections import namedtuple

class Runner:
    def __init__(self):
        self.benchmark_status = ""
    
    def run_benchmark(self, ba, sa, run_tag, topology, policies):
        status_id = ba.technology + ba.node

        broker_user = "benchmark"
        broker_password = sa.password

        nodes = ""
        for x in range(int(ba.cluster_size)):
            comma = ","
            if x == 0:
                comma = ""

            node_number = int(ba.node) + x
            nodes = f"{nodes}{comma}rabbit@rabbitmq{node_number}"

        self.benchmark_status[status_id] = "started"
        exit_code = subprocess.call(["bash", "run-logged-aws-benchmark.sh", ba.node, sa.key_pair, ba.technology, ba.broker_version, ba.instance, ba.volume, ba.filesystem, sa.hosting, ba.tenancy, sa.password, sa.postgres_url, sa.postgres_user, sa.postgres_pwd, topology, sa.run_id, broker_user, broker_password, run_tag, ba.core_count, ba.threads_per_core, ba.config_tag, str(ba.cluster_size), ba.no_tcp_delay, policies, str(sa.override_step_seconds), str(sa.override_step_repeat), nodes, str(sa.override_step_msg_limit), sa.override_broker_hosts, ba.try_connect_local])
        if exit_code != 0:
            print(f"Benchmark {ba.node} failed")
            self.benchmark_status[status_id] = "failed"
        else:
            self.benchmark_status[status_id] = "success"

    def run_background_load_across_runs(self, ba1_list, sharedArgs, run_tag, parallel_count):
        bg_threads = list()
        for p in range(parallel_count):
            ba1 = ba1_list[p]
            t1 = threading.Thread(target=run_background_load, args=(ba1, sharedArgs, run_tag,))
            bg_threads.append(t1)

        for bt in bg_threads:
            bt.start()

        time.sleep(10)
        print(f"Delaying start of benchmark by {ba1_list[0].bg_delay} seconds")
        time.sleep(ba1_list[0].bg_delay)

    def run_background_load(self, ba, sa, run_tag):
        global benchmark_status
        print(f"Starting background load for {ba.node}")
        status_id = ba.technology + ba.node

        broker_user = "benchmark"
        broker_password = sa.password
        topology = ba.bg_topology_file
        policies = ba.bg_policies_file
        step_seconds = str(ba.bg_step_seconds)
        step_repeat = str(ba.bg_step_repeat)

        nodes = ""
        for x in range(int(ba.cluster_size)):
            comma = ","
            if x == 0:
                comma = ""

            node_number = int(ba.node) + x
            nodes = f"{nodes}{comma}{node_number}"

        self.benchmark_status[status_id] = "started"
        subprocess.Popen(["bash", "run-background-load-aws.sh", broker_user, broker_password, str(ba.cluster_size), sa.key_pair, ba.node, nodes, policies, step_seconds, step_repeat, run_tag, ba.technology, topology, ba.broker_version])