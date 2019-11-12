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
from Deployer import Deployer
from Runner import Runner

args = get_args(sys.argv)
no_destroy = is_true(get_optional_arg(args, "--no-destroy", "false"))
no_deploy = is_true(get_optional_arg(args, "--no-deploy", "false"))
run_tag = get_optional_arg(args, "--run-tag", "none")

fill_topology = get_mandatory_arg(args, "--fill-topology")
drain_topology = get_mandatory_arg(args, "--drain-topology")
policies_file = get_optional_arg(args, "--policies-file", "none")
background_policies_file = get_optional_arg(args, "--bg-policies-file", "none")
background_topology_file = get_optional_arg(args, "--bg-topology-file", "none")
background_delay = int(get_optional_arg(args, "--bg-delay", "0"))
background_step_seconds = int(get_optional_arg(args, "--bg-step-seconds", "0"))
background_step_repeat = int(get_optional_arg(args, "--bg-step-repeat", "0"))
ami_id = get_mandatory_arg(args, "--ami")
broker_sg = get_mandatory_arg(args, "--broker-sg")
loadgen_sg = get_mandatory_arg(args, "--loadgen-sg")
loadgen_instance = get_mandatory_arg(args, "--loadgen-instance")
subnet = get_mandatory_arg(args, "--subnet")
key_pair = get_mandatory_arg(args, "--keypair")
password = get_mandatory_arg(args, "--password")
gap_seconds = int(get_mandatory_arg(args, "--gap-seconds"))
repeat_count = int(get_optional_arg(args, "--repeat", "1"))
parallel_count = int(get_optional_arg(args, "--parallel", "1"))
override_step_seconds = int(get_optional_arg(args, "--override-step-seconds", "0"))
override_step_repeat = int(get_optional_arg(args, "--override-step-repeat", "0"))
override_step_msg_limit = int(get_optional_arg(args, "--override-step-msg-limit", "0"))
override_broker_hosts = get_optional_arg(args, "--override-broker-hosts", "")
postgres_url = get_mandatory_arg(args, "--postgres-jdbc-url")
postgres_user = get_mandatory_arg(args, "--postgres-user")
postgres_pwd = get_mandatory_arg_no_print(args, "--postgres-password")
node_counter = int(get_optional_arg(args, "--start-node-num-from", "1"))

config_tag1  = get_mandatory_arg(args, "--config-tag")
technology1 = get_mandatory_arg_validated(args, "--technology", ["rabbitmq"])
cluster_size1 = int(get_optional_arg(args, "--cluster-size", "1"))
version1 = get_mandatory_arg(args, "--version")
instance1 = get_mandatory_arg(args, "--instance")
volume1 = get_mandatory_arg_validated(args, "--volume", ["ebs-io1","ebs-st1","ebs-gp2","local-nvme"])
volume_size1 = get_mandatory_arg(args, "--volume-size")
fs1 = get_mandatory_arg_validated(args, "--filesystem", ["ext4", "xfs"])
tenancy1 = get_mandatory_arg_validated(args, "--tenancy", ["default","dedicated"])
core_count1 = get_mandatory_arg(args, "--core-count")
no_tcp_delay1 = get_optional_arg(args, "--no-tcp-delay", "true")
try_connect_local1 = get_optional_arg(args, "--try-connect-local", "false")
threads_per_core1 = get_mandatory_arg(args, "--threads-per-core")
vars_file = get_optional_arg(args, "--vars-file", f".variables/{technology1}-vars.yml")

run_id = str(uuid.uuid4())
print(f"RUN ID = {run_id}")

BrokerArgs = namedtuple("BrokerArgs", "node technology broker_version cluster_size instance volume volume_size filesystem tenancy core_count threads_per_core no_tcp_delay config_tag vars_file bg_topology_file bg_policies_file bg_delay bg_step_seconds bg_step_repeat, try_connect_local")
SharedArgs = namedtuple("SharedArgs", "key_pair subnet ami broker_sg loadgen_sg loadgen_instance hosting password postgres_url postgres_user postgres_pwd run_id override_step_seconds override_step_repeat override_step_msg_limit override_broker_hosts")

print("Preparing broker configurations:")

number_modifer = cluster_size1
ba1_list = list()
for x in range(parallel_count):
    node_number1 = node_counter
    print(f" - configuration 1: {technology1}{node_number1}")
    ba1_args = BrokerArgs(str(node_number1), technology1, version1, cluster_size1, instance1, volume1, volume_size1, fs1, tenancy1, core_count1, threads_per_core1, no_tcp_delay1, config_tag1, vars_file, background_topology_file, background_policies_file, background_delay, background_step_seconds, background_step_repeat, try_connect_local1)
    ba1_list.append(ba1_args)
    node_counter += cluster_size1

sharedArgs = SharedArgs(key_pair, subnet, ami_id, broker_sg, loadgen_sg, loadgen_instance, "aws", password, postgres_url, postgres_user, postgres_pwd, run_id, override_step_seconds, override_step_repeat, override_step_msg_limit, override_broker_hosts)

if not os.path.exists(playlist_file):
    print("The supplied playlist file does not exist")
    exit(1)

if policies_file != "none" and not os.path.exists("../deploy/policies/" + policies_file):
    print("The supplied policies file does not exist")
    exit(1)

pl_file = open(playlist_file, "r")
topologies = list()
for line in pl_file:
    topology = line.replace("\n", "")
    if not os.path.exists("../deploy/topologies/" + topology):
        print(f"The topology file {topology} does not exist")
        exit(1)
    topologies.append(topology)

print(f"{len(topologies)} topologies in {playlist_file} playlist")

runner = Runner()
deployer = Deployer()

if len(topologies) > 0:

    if not new_instance_per_run:
        if run_tag == "none":
            run_tag = str(randint(1, 99999))
        deployer.parallel_deploy(ba1_list, sharedArgs, run_tag, no_deploy)
        if background_topology_file != "none":
            runner.run_background_load_across_runs(ba1_list, sharedArgs, run_tag, parallel_count)

    for i in range(repeat_count):
        print(f"Starting run {i+1}")

        if new_instance_per_run:
            if run_tag == "none":
                run_tag = str(randint(1, 99999))
            deployer.parallel_deploy(ba1_list, sharedArgs, run_tag, no_deploy)
            if background_topology_file != "none":
                runner.run_background_load_across_runs(ba1_list, sharedArgs, run_tag, parallel_count, background_topology_file, background_policies_file, background_delay)
        
        for topology in topologies:
            print(f"Started {topology}")

            b_threads = list()
            for p in range(parallel_count):
                ba1 = ba1_list[p]
                t1 = threading.Thread(target=runner.run_benchmark, args=(ba1, sharedArgs, run_tag, topology,policies_file,))
                b_threads.append(t1)

            for bt in b_threads:
                bt.start()
            
            try:
                for bt in b_threads:
                    bt.join()
            except KeyboardInterrupt:
                print("Aborting run...")
                deployer.teardown_all(ba1_list, run_tag, no_destroy)
                exit(1)
            
            for p in range(parallel_count):
                ba1 = ba1_list[p]
                status_id1 = ba1.technology + ba1.node
                if runner.get_benchmark_status(status_id1) != "success":
                    print(f"Benchmark failed for node {ba1.node} and topology {topology}")
                    deployer.teardown_all(ba1_list, run_tag, no_deploy)
                    exit(1)

            print(f"Finished {topology}")
            time.sleep(gap_seconds)

        if new_instance_per_run:
            deployer.teardown_all(ba1_list, run_tag, no_destroy)

    if not new_instance_per_run:
        for p in range(parallel_count):
            deployer.teardown_all(ba1_list, run_tag, no_destroy)
else:
    print("No topologies to process")

print(f"RUN {run_id} COMPLETE")