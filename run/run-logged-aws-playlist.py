#!/usr/bin/env python

import sys
import threading
import time
import os.path
from random import randint
from command_args import get_args
from Deployer import Deployer
from Runner import Runner
from UniqueConfiguration import UniqueConfiguration
from CommonConfiguration import CommonConfiguration

args = get_args(sys.argv)
common_conf = CommonConfiguration(args)

print(f"RUN ID = {common_conf.run_id}")

print("Preparing broker configurations:")

configurations = dict()
for config_number in range(1, common_conf.config_count+1):
    unique_conf_list = list()
    for _ in range(common_conf.parallel_count):
        node_number = common_conf.node_counter
        unique_conf = UniqueConfiguration(args, str(config_number)) 
        unique_conf.set_node_number(str(node_number))

        if unique_conf.policies_file != "none" and not os.path.exists("../deploy/policies/" + unique_conf.policies_file):
            print("The supplied policies file does not exist")
            exit(1)

        unique_conf_list.append(unique_conf)
        common_conf.node_counter += unique_conf.cluster_size

    configurations[config_number] = unique_conf_list

if not os.path.exists(common_conf.playlist_file):
    print("The supplied playlist file does not exist")
    exit(1)

pl_file = open(common_conf.playlist_file, "r")
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

print(f"{len(topologies)} topologies in {common_conf.playlist_file} playlist")

runner = Runner()
deployer = Deployer()

if len(topologies) > 0:

    # deploy unless deployment configured to redeploy on each repeat run
    if not common_conf.new_instance_per_run:
        deployer.deploy(runner, configurations, common_conf)

    # repeat ([deploy], run benchmark, [teardown]) according to configuration
    for i in range(common_conf.repeat_count):
        print(f"Starting run {i+1}")

        if common_conf.new_instance_per_run:
            deployer.deploy(runner, configurations, common_conf)
        
        top_counter = 0

        # run each topology benchmark
        for topology in topologies:
            print(f"Started {topology}")

            # run all instances of the topology benchmark
            b_threads = list()
            for config_tag in configurations:
                
                unique_conf_list = configurations[config_tag]

                # take policy from playlist or argument
                policy = unique_conf_list[0].policies_file
                if len(policies[top_counter]) > 0:
                    if unique_conf_list[0].policies_file == "none":
                        policy = policies[top_counter]

                # run all parallel executions of a single benchmark configuration
                for p in range(common_conf.parallel_count):
                    unique_conf = unique_conf_list[p]
                    t1 = threading.Thread(target=runner.run_benchmark, args=(unique_conf, common_conf, topology, policy,))
                    b_threads.append(t1)

            for bt in b_threads:
                bt.start()
            
            try:
                for bt in b_threads:
                    bt.join()
            except KeyboardInterrupt:
                print("Aborting run...")
                deployer.teardown_all(configurations, common_conf.run_tag, common_conf.no_destroy)
                exit(1)
            
            # check if any benchmark instance failed and if so then teardown everything unless configured no to
            for config_tag in configurations:
                unique_conf_list = configurations[config_tag]
            
                for p in range(common_conf.parallel_count):
                    unique_conf = unique_conf_list[p]
                    status_id1 = unique_conf.technology + unique_conf.node_number
                    if runner.get_benchmark_status(status_id1) != "success":
                        print(f"Benchmark failed for node {unique_conf.node_number} and topology {topology}")
                        deployer.teardown_all(configurations, common_conf.run_tag, common_conf.no_destroy)
                        exit(1)

            # wait for configuration gap seconds
            print(f"Finished {topology}")
            time.sleep(common_conf.gap_seconds)
            top_counter+=1

        if common_conf.new_instance_per_run:
            deployer.teardown_all(configurations, common_conf.run_tag, common_conf.no_destroy)

    if not common_conf.new_instance_per_run:
        for p in range(common_conf.parallel_count):
            deployer.teardown_all(configurations, common_conf.run_tag, common_conf.no_destroy)
else:
    print("No topologies to process")

print(f"RUN {common_conf.run_id} COMPLETE")