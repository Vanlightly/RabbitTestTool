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

def update_single(ba, sa, run_tag):
    global deploy_status
    status_id = ba.technology + ba.node
    exit_code = subprocess.call(["bash", "update-benchmark.sh", sa.key_pair, ba.node, ba.technology, run_tag], cwd="../deploy/aws")
    if exit_code != 0:
        print(f"update {ba.node} failed with exit code {exit_code}")
        deploy_status[status_id] = "failed"   
    else:
        deploy_status[status_id] = "success"

def deploy_single(ba, sa, run_tag):
    global deploy_status
    status_id = ba.technology + ba.node
    deploy_status[status_id] = "started"
    volume_type = ba.volume.split("-")[1]
    exit_code = subprocess.call(["bash", "deploy-single-broker.sh", sa.ami, ba.broker_version, ba.core_count, ba.filesystem, ba.instance, sa.key_pair, sa.loadgen_instance, sa.loadgen_sg, ba.node, run_tag, sa.broker_sg, sa.subnet, ba.technology, ba.tenancy, ba.threads_per_core, ba.vars_file, ba.volume_size, volume_type], cwd="../deploy/aws")

    if exit_code != 0:
        print(f"deploy {ba.node} failed with exit code {exit_code}")
        deploy_status[status_id] = "failed"   
    else:
        deploy_status[status_id] = "success"

def deploy_rabbitmq_cluster(ba, sa, run_tag):
    global deploy_status
    status_id = ba.technology + ba.node
    deploy_status[status_id] = "started"
    volume_type = ba.volume.split("-")[1]
    
    exit_code = subprocess.call(["bash", "deploy-rmq-cluster-instances.sh", sa.ami, str(ba.cluster_size), ba.core_count, ba.instance, sa.key_pair, sa.loadgen_instance, sa.loadgen_sg, ba.node, run_tag, sa.broker_sg, sa.subnet, ba.tenancy, ba.threads_per_core, ba.volume_size, volume_type], cwd="../deploy/aws")
    if exit_code != 0:
        print(f"deploy {ba.node} failed with exit code {exit_code}")
        deploy_status[status_id] = "failed" 
        return  
    
    master_node = int(ba.node)
    node_range_start = master_node
    node_range_end = master_node + int(ba.cluster_size) - 1
    
    # deploy master
    exit_code = subprocess.call(["bash", "deploy-rmq-cluster-broker.sh", sa.ami, ba.broker_version, ba.core_count, ba.filesystem, ba.instance, sa.key_pair, str(master_node), str(node_range_end), str(node_range_start), "master", run_tag, sa.broker_sg, sa.subnet, ba.tenancy, ba.threads_per_core, ba.vars_file, ba.volume_size, volume_type], cwd="../deploy/aws")

    if exit_code != 0:
        print(f"deploy {ba.node} failed with exit code {exit_code}")
        deploy_status[status_id] = "failed"   
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
    if deploy_status[status_id] != "failed":
        exit_code = subprocess.call(["bash", "deploy-benchmark.sh", sa.key_pair, str(master_node), "rabbitmq", run_tag], cwd="../deploy/aws")

        if exit_code != 0:
            print(f"deploy {ba.node} failed with exit code {exit_code}")
            deploy_status[status_id] = "failed"   
        else:
            deploy_status[status_id] = "success"
    
    

def deploy_joinee(ba, sa, run_tag, status_id, volume_type, node, node_range_start, node_range_end):
    exit_code = subprocess.call(["bash", "deploy-rmq-cluster-broker.sh", sa.ami, ba.broker_version, ba.core_count, ba.filesystem, ba.instance, sa.key_pair, str(node), str(node_range_end), str(node_range_start), "joinee", run_tag, sa.broker_sg, sa.subnet, ba.tenancy, ba.threads_per_core, ba.vars_file, ba.volume_size, volume_type], cwd="../deploy/aws")    
    if exit_code != 0:
        print(f"deploy of joinee rabbitmq{node} failed with exit code {exit_code}")
        deploy_status[status_id] = "failed"   
    

def teardown(technology, node, run_tag, no_destroy):
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

def teardown_all(broker1_args_list, broker2_args_list, no_destroy):
    if no_destroy:
        print("No teardown as --no-destroy set to true")
    else:
        print("Terminating all servers")
        for p in range(len(broker1_args_list)):
            ba1 = broker1_args_list[p]
            ba2 = broker2_args_list[p]
            
            for n in range(0, ba1.cluster_size):
                node_num = int(ba1.node) + n
                teardown(ba1.technology, str(node_num), run_tag, no_destroy)

            for n in range(0, ba2.cluster_size):
                node_num = int(ba2.node) + n
                teardown(ba2.technology, str(node_num), run_tag, no_destroy)
        print("All servers terminated")
        exit(1)

def run_benchmark(ba, sa, run_tag, topology, policy):
    global benchmark_status
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

    benchmark_status[status_id] = "started"
    exit_code = subprocess.call(["bash", "run-logged-aws-benchmark.sh", ba.node, sa.key_pair, ba.technology, ba.broker_version, ba.instance, ba.volume, ba.filesystem, sa.hosting, ba.tenancy, sa.password, sa.postgres_url, sa.postgres_user, sa.postgres_pwd, topology, sa.run_id, broker_user, broker_password, run_tag, ba.core_count, ba.threads_per_core, ba.config_tag, str(ba.cluster_size), ba.no_tcp_delay, policy, str(sa.override_step_seconds), str(sa.override_step_repeat),nodes,str(sa.override_step_msg_limit), override_broker_hosts, ba.try_connect_local])
    if exit_code != 0:
        print(f"Benchmark {ba.node} failed")
        benchmark_status[status_id] = "failed"
    else:
        benchmark_status[status_id] = "success"

def parallel_deploy(broker1_args_list, broker2_args_list, sa, run_tag, no_deploy):
    global deploy_status

    d_threads = list()
    for i in range(len(broker1_args_list)):
        ba1 = broker1_args_list[i]
        ba2 = broker2_args_list[i]
        
        if no_deploy:
            deploy1 = threading.Thread(target=update_single, args=(ba1, sa, run_tag,))
        else:
            if ba1.cluster_size == 1:
                deploy1 = threading.Thread(target=deploy_single, args=(ba1, sa, run_tag,))
            else:
                deploy1 = threading.Thread(target=deploy_rabbitmq_cluster, args=(ba1, sa, run_tag,))

        if no_deploy:
            deploy2 = threading.Thread(target=update_single, args=(ba2, sa, run_tag,))
        else:
            if ba2.cluster_size == 1:
                deploy2 = threading.Thread(target=deploy_single, args=(ba2, sa, run_tag,))
            else:
                deploy2 = threading.Thread(target=deploy_rabbitmq_cluster, args=(ba2, sa, run_tag,))

        d_threads.append(deploy1)
        d_threads.append(deploy2)

    for dt in d_threads:
        dt.start()
        time.sleep(5)
    
    for dt in d_threads:
        dt.join()

    deployment_succeeded = True
    for p in range(len(broker1_args_list)):
        ba1 = broker1_args_list[p]
        ba2 = broker2_args_list[p]
        status_id1 = ba1.technology + ba1.node
        status_id2 = ba2.technology + ba2.node
    
        if deploy_status[status_id1] != "success" or deploy_status[status_id2] != "success":
            print(f"One or both deployments failed for nodes {ba1.technology}{ba1.node}, {ba2.technology}{ba2.node}.")
            deployment_succeeded = False

    if not deployment_succeeded:
        if not no_deploy:
            teardown_all(broker1_args_list, broker2_args_list, True)


args = get_args(sys.argv)
new_instance_per_run = is_true(get_optional_arg(args, "--new-instance-per-run", "false"))
no_destroy = is_true(get_optional_arg(args, "--no-destroy", "false"))
no_deploy = is_true(get_optional_arg(args, "--no-deploy", "false"))
run_tag = get_optional_arg(args, "--run-tag", "none")

playlist_file = get_mandatory_arg(args, "--playlist-file")
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

config_tag1  = get_mandatory_arg(args, "--config-tag1")
policies_file1 = get_optional_arg(args, "--policies-file1", "none")
technology1 = get_mandatory_arg_validated(args, "--technology1", ["rabbitmq"])
cluster_size1 = int(get_optional_arg(args, "--cluster-size1", "1"))
version1 = get_mandatory_arg(args, "--version1")
instance1 = get_mandatory_arg(args, "--instance1")
volume1 = get_mandatory_arg_validated(args, "--volume1", ["ebs-io1","ebs-st1","ebs-gp2","local-nvme"])
volume_size1 = get_mandatory_arg(args, "--volume-size1")
fs1 = get_mandatory_arg_validated(args, "--filesystem1", ["ext4", "xfs"])
tenancy1 = get_mandatory_arg(args, "--tenancy1")
core_count1 = get_mandatory_arg(args, "--core-count1")
threads_per_core1 = get_mandatory_arg(args, "--threads-per-core1")
no_tcp_delay1 = get_optional_arg(args, "--no-tcp-delay1", "true")
try_connect_local1 = get_optional_arg(args, "--try-connect-local1", "false")
vars_file1 = get_optional_arg(args, "--vars-file1", f".variables/{technology1}-vars.yml")

config_tag2  = get_mandatory_arg(args, "--config-tag2")
policies_file2 = get_optional_arg(args, "--policies-file2", "none")
technology2 = get_mandatory_arg_validated(args, "--technology2", ["rabbitmq"])
cluster_size2 = int(get_optional_arg(args, "--cluster-size2", "1"))
version2 = get_mandatory_arg(args, "--version2")
instance2 = get_mandatory_arg(args, "--instance2")
volume2 = get_mandatory_arg_validated(args, "--volume2", ["ebs-io1","ebs-st1","ebs-gp2","local-nvme"])
volume_size2 = get_mandatory_arg(args, "--volume-size2")
fs2 = get_mandatory_arg_validated(args, "--filesystem2", ["ext4", "xfs"])
tenancy2 = get_mandatory_arg_validated(args, "--tenancy2", ["default","dedicated"])
core_count2 = get_mandatory_arg(args, "--core-count2")
threads_per_core2 = get_mandatory_arg(args, "--threads-per-core2")
no_tcp_delay2 = get_optional_arg(args, "--no-tcp-delay2", "true")
try_connect_local2 = get_optional_arg(args, "--try-connect-local2", "false")
vars_file2 = get_optional_arg(args, "--vars-file2", f".variables/{technology2}-vars.yml")

run_id = str(uuid.uuid4())
print(f"RUN ID = {run_id}")

BrokerArgs = namedtuple("BrokerArgs", "node technology broker_version cluster_size instance volume volume_size filesystem tenancy core_count threads_per_core no_tcp_delay config_tag vars_file try_connect_local")
SharedArgs = namedtuple("SharedArgs", "key_pair subnet ami broker_sg loadgen_sg loadgen_instance hosting password postgres_url postgres_user postgres_pwd run_id override_step_seconds override_step_repeat override_step_msg_limit override_broker_hosts")

print("Preparing broker configurations:")

ba1_list = list()
ba2_list = list()
for x in range(parallel_count):

    node_number1 = node_counter
    print(f" - configuration 1: {technology1}{node_number1}")
    ba1_args = BrokerArgs(str(node_number1), technology1, version1, cluster_size1, instance1, volume1, volume_size1, fs1, tenancy1, core_count1, threads_per_core1, no_tcp_delay1, config_tag1, vars_file1, try_connect_local1)
    ba1_list.append(ba1_args)
    node_counter += cluster_size1

    node_number2 = node_counter
    print(f" - configuration 2: {technology2}{node_number2}")
    ba2_args = BrokerArgs(str(node_number2), technology2, version2, cluster_size2, instance2, volume2, volume_size2, fs2, tenancy2, core_count2, threads_per_core2, no_tcp_delay2, config_tag2, vars_file2, try_connect_local2)    
    ba2_list.append(ba2_args)
    node_counter += cluster_size2

sharedArgs = SharedArgs(key_pair, subnet, ami_id, broker_sg, loadgen_sg, loadgen_instance, "aws", password, postgres_url, postgres_user, postgres_pwd, run_id, override_step_seconds, override_step_repeat, override_step_msg_limit, override_broker_hosts)

deploy_status = dict()
benchmark_status = dict()

if not os.path.exists(playlist_file):
    print("The supplied playlist file does not exist")
    exit(1)

pl_file = open(playlist_file, "r")
topologies = list()
policies = list()
for line in pl_file:
    linestr = line.replace("\n", "")
    if "," in linestr:
        topology = linestr.split(",")[0]
        policy = linestr.split(",")[1]
    else:
        topology = linestr
        policy = ""

    if not os.path.exists("../deploy/topologies/" + topology):
        print(f"The topology file {topology} does not exist")
        exit(1)
    
    if len(policy) > 0 and not os.path.exists("../deploy/policies/" + policy):
        print(f"The policy file {policy} does not exist")
        exit(1)

    topologies.append(topology)
    policies.append(policy)

print(policies)

if policies_file1 != "none" and not os.path.exists("../deploy/policies/" + policies_file1):
    print("The supplied policies file 1 does not exist")
    exit(1)

if policies_file2 != "none" and not os.path.exists("../deploy/policies/" + policies_file2):
    print("The supplied policies file 2 does not exist")
    exit(1)

print(f"{len(topologies)} topologies in {playlist_file} playlist")

if len(topologies) > 0:

    if not new_instance_per_run:
        if run_tag == "none":
            run_tag = str(randint(1, 99999))
        parallel_deploy(ba1_list, ba2_list, sharedArgs, run_tag, no_deploy)

    for i in range(repeat_count):
        print(f"Starting run {i+1}")
        
        if new_instance_per_run:
            if run_tag == "none":
                run_tag = str(randint(1, 99999))
            parallel_deploy(ba1_list, ba2_list, sharedArgs, run_tag, no_deploy)
        
        top_counter = 0
        for topology in topologies:
            print(f"Started {topology}")

            policy1 = policies_file1
            policy2 = policies_file2
            if len(policies[top_counter]) > 0:
                if policies_file1 == "none":
                    policy1 = policies[top_counter]
                if policies_file2 == "none":
                    policy2 = policies[top_counter]

            print(f"top_counter {top_counter}")
            print(f"policies_file1 = {policy1}")
            print(f"policies_file2 = {policy2}")
            top_counter+=1

            b_threads = list()
            for p in range(parallel_count):
                ba1 = ba1_list[p]
                ba2 = ba2_list[p]
                t1 = threading.Thread(target=run_benchmark, args=(ba1, sharedArgs, run_tag, topology, policy1,))
                t2 = threading.Thread(target=run_benchmark, args=(ba2, sharedArgs, run_tag, topology, policy2,))
                b_threads.append(t1)
                b_threads.append(t2)

            for bt in b_threads:
                bt.start()
            
            try:
                for bt in b_threads:
                    bt.join()
            except KeyboardInterrupt:
                print("Aborting run...")
                teardown_all(ba1_list, ba2_list, True)
                exit(1)
            
            for p in range(parallel_count):
                ba1 = ba1_list[p]
                ba2 = ba2_list[p]
                status_id1 = ba1.technology + ba1.node
                status_id2 = ba2.technology + ba2.node
                if benchmark_status[status_id1] != "success" or benchmark_status[status_id2] != "success":
                    print(f"One or both benchmarks failed for nodes {ba1.node},{ba2.node} and topology {topology}")
                    teardown_all(ba1_list, ba2_list, no_deploy)
                    exit(1)

            print(f"Finished {topology}")
            time.sleep(gap_seconds)

        if new_instance_per_run:
            teardown_all(ba1_list, ba2_list, no_destroy)

    if not new_instance_per_run:
        teardown_all(ba1_list, ba2_list, no_destroy)
else:
    print("No topologies to process")

print(f"RUN {run_id} COMPLETE")