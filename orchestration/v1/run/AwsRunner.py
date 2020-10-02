import sys
import io
import subprocess
import threading
import time
import uuid
import os.path
from random import randint
from collections import namedtuple
from printer import console_out
from Runner import Runner

class AwsRunner(Runner):
    def __init__(self):
        super().__init__()

    def run_benchmark(self, unique_conf, common_conf, playlist_entry, policies, run_ordinal):
        status_id = unique_conf.technology + unique_conf.node_number

        federation_args = ""
        if common_conf.federation_enabled:
            ds_node_number = int(unique_conf.node_number) + 100 + x
            ds_broker_ips = self.get_broker_ips(unique_conf.technology, ds_node_number, unique_conf.cluster_size, common_conf.run_tag)
            federation_args += f"--downstream-broker-hosts {ds_broker_ips}"

        script = "run-logged-aws-benchmark.sh"
        # TODO: make these contexts not use hard-coded regions and cluster names
        if unique_conf.deployment == "eks":
            context = f"{unique_conf.deployment_user}@benchmarking-eks.eu-west-1.eksctl.io"
            script = "run-logged-aws-k8s-benchmark.sh"
        elif unique_conf.deployment == "gke":
            context = f"gke_{unique_conf.deployment_user}_europe-west4-a_benchmarking-gke"
            script = "run-logged-aws-k8s-benchmark.sh"
        else:
            context = "none"

        cluster_name = f"rmq-{unique_conf.deployment}"

        self._benchmark_status[status_id] = "started"
        exit_code = subprocess.call(["bash", script, 
                                unique_conf.node_number, 
                                common_conf.key_pair, 
                                unique_conf.technology, 
                                unique_conf.broker_version, 
                                unique_conf.instance, 
                                unique_conf.volume1_type, 
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
                                str(common_conf.override_step_msg_limit), 
                                common_conf.override_broker_hosts, 
                                unique_conf.pub_connect_to_node,
                                unique_conf.con_connect_to_node,
                                str(unique_conf.pub_heartbeat_sec),
                                str(unique_conf.con_heartbeat_sec),
                                common_conf.mode,
                                str(common_conf.grace_period_sec),
                                common_conf.warmUpSeconds,
                                common_conf.checks,
                                str(run_ordinal),
                                common_conf.tags,
                                common_conf.attempts,
                                common_conf.influx_subpath,
                                playlist_entry.get_topology_variables(),
                                playlist_entry.get_policy_variables(),
                                federation_args,
                                context,
                                cluster_name,
                                unique_conf.memory_gb])

        if exit_code != 0:
            console_out(self.actor, f"Benchmark {unique_conf.node_number} failed")
            self._benchmark_status[status_id] = "failed"
        else:
            self._benchmark_status[status_id] = "success"

    def run_background_load(self, unique_conf, common_conf, topology, policies, step_seconds, step_repeat, delay_seconds):
        if delay_seconds > 0:
            console_out(self.actor, f"Delaying start of background load by {delay_seconds} seconds for {unique_conf.node_number}")
            time.sleep(delay_seconds)

        console_out(self.actor, f"Starting background load for {unique_conf.node_number}")
        status_id = unique_conf.technology + unique_conf.node_number

        broker_user = "benchmark"
        broker_password = common_conf.password

        if policies == "":
            policies = "none"

        subprocess.call(["bash", "run-background-load-aws.sh", 
                        broker_user, 
                        broker_password, 
                        str(unique_conf.cluster_size), 
                        common_conf.key_pair, 
                        unique_conf.node_number, 
                        policies, 
                        str(step_seconds), 
                        str(step_repeat), 
                        common_conf.run_tag, 
                        unique_conf.technology, 
                        topology, 
                        unique_conf.broker_version])


    def get_broker_ips(self, technology, node, cluster_size, run_tag):
        broker_ips = ""
        attempts = 0
        while broker_ips == "" and attempts < 3:
            attempts += 1
            process = subprocess.Popen(["bash", "get_broker_ips.sh", 
                            str(cluster_size), 
                            str(node), 
                            run_tag,
                            technology], stdout=subprocess.PIPE)
            
            for line in io.TextIOWrapper(process.stdout, encoding="utf-8"):
                if not line:
                    break
                
                if line.startswith("BROKER_IPS="):
                    broker_ips = line.rstrip().replace("BROKER_IPS=","")
                    break

            if broker_ips == "":
                time.sleep(5)

        return broker_ips