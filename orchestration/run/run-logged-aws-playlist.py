#!/usr/bin/env python

import sys
import threading
import time
import os.path
import json
from random import randint
from command_args import get_args
from Deployer import Deployer
from Runner import Runner
from BrokerActions import BrokerActions
from UniqueConfiguration import UniqueConfiguration
from CommonConfiguration import CommonConfiguration
from PlaylistEntry import PlaylistEntry
from printer import console_out

def get_variables(entry_json, common_attr_json, vars_field):
    variables = dict()

    # add any variables from common attributes first (least precedence)
    if vars_field in common_attr_json:
        vars_json = common_attr_json[vars_field]
        for key in vars_json:
            value = vars_json[key]
            variables[key] = value

    
    # add any variables from entry second (highest precedence)
    if vars_field in entry_json:
        vars_json = entry_json[vars_field]
        for key in vars_json:
            value = vars_json[key]
            variables[key] = value

    return variables

def get_entry_optional_field(entry_json, common_json, field, default):
    if field in entry_json:
        return entry_json[field]
    elif field in common_json:
        return common_json[field]
    else:
        return default
        
def get_entry_mandatory_field(entry_json, common_json, field):
    if field in entry_json:
        return entry_json[field]
    else:
        if field not in common_json:
            console_out("RUNNER", f"Playlist field {field} is required")
            exit(1)
        
        return common_json[field]

def get_playlist_entries(playlist_file):
    pl_file = open(playlist_file, "r")
    playlist_json = json.loads(pl_file.read())
    common_attr = playlist_json["commonAttributes"]

    playlist_entries = list()

    # load topologies to run and check topology and policy files exist
    for playlist_entry in playlist_json['benchmarks']:
        entry = PlaylistEntry()
        entry.topology = get_entry_mandatory_field(playlist_entry, common_attr, "topology")
        entry.topology_variables = get_variables(playlist_entry, common_attr, "topologyVariables")
        entry.policy = get_entry_mandatory_field(playlist_entry, common_attr, "policy")
        entry.policy_variables = get_variables(playlist_entry, common_attr, "policyVariables")
        
        entry.has_broker_actions = get_entry_optional_field(playlist_entry, common_attr, "hasBrokerActions", False)
        if entry.has_broker_actions:
            entry.broker_action = get_entry_mandatory_field(playlist_entry, common_attr, "brokerAction")
            entry.trigger_type = get_entry_mandatory_field(playlist_entry, common_attr, "triggerType")
            entry.trigger_at = get_entry_mandatory_field(playlist_entry, common_attr, "triggerAt")

        entry.grace_period_sec = get_entry_optional_field(playlist_entry, common_attr, "gracePeriodSec", 0)
        
        if not os.path.exists("../benchmark/topologies/" + entry.topology):
            console_out("RUNNER", f"The topology file {entry.topology} does not exist")
            exit(1)
        
        if len(entry.policy) > 0 and not os.path.exists("../benchmark/policies/" + entry.policy):
            console_out("RUNNER", f"The policy file {entry.policy} does not exist")
            exit(1)

        playlist_entries.append(entry)

    return playlist_entries

args = get_args(sys.argv)
common_conf = CommonConfiguration(args)

console_out("RUNNER", f"RUN ID = {common_conf.run_id}")

console_out("RUNNER", "Preparing broker configurations:")

configurations = dict()
start_node = common_conf.node_counter

for config_number in range(1, common_conf.config_count+1):
    unique_conf_list = list()
    for _ in range(common_conf.parallel_count):
        node_number = common_conf.node_counter
        unique_conf = UniqueConfiguration(args, str(config_number)) 
        unique_conf.set_node_number(str(node_number))

        if unique_conf.policies_file != "none" and not os.path.exists("../deploy/policies/" + unique_conf.policies_file):
            console_out("RUNNER", "The supplied policies file does not exist")
            exit(1)

        unique_conf_list.append(unique_conf)
        common_conf.node_counter += unique_conf.cluster_size

    configurations[config_number] = unique_conf_list

end_node = common_conf.node_counter-1

if not os.path.exists(common_conf.playlist_file):
    console_out("RUNNER", "The supplied playlist file does not exist")
    exit(1)

playlist_entries = get_playlist_entries(common_conf.playlist_file)

console_out("RUNNER", f"{len(playlist_entries)} entries in {common_conf.playlist_file} playlist")

runner = Runner()
deployer = Deployer()
broker_actions = BrokerActions(deployer)

if len(playlist_entries) == 0:
    console_out("RUNNER", "No playlist entries to run")
    exit(1)

# deploy unless deployment configured to redeploy on each repeat run
if not common_conf.new_instance_per_run:
    deployer.deploy(runner, configurations, common_conf)

# repeat ([deploy], run benchmark, [teardown]) according to configuration
for i in range(common_conf.repeat_count):
    console_out("RUNNER", f"Starting run {i+1}")

    if common_conf.new_instance_per_run:
        deployer.deploy(runner, configurations, common_conf)
    
    # run each topology benchmark
    run_ordinal = 1
    for top_counter in range(0, len(playlist_entries)):
        entry = playlist_entries[top_counter]
        console_out("RUNNER", f"Started {entry.topology}")

        # run all instances of the topology benchmark
        b_threads = list()
        for config_tag in configurations:
            
            unique_conf_list = configurations[config_tag]

            # take policy from playlist or argument
            policy = unique_conf_list[0].policies_file
            if len(entry.policy) > 0:
                if unique_conf_list[0].policies_file == "none":
                    policy = entry.policy

            # run all parallel executions of a single benchmark configuration
            for p in range(common_conf.parallel_count):
                unique_conf = unique_conf_list[p]
                t1 = threading.Thread(target=runner.run_benchmark, args=(unique_conf, common_conf, entry, policy, run_ordinal,))
                b_threads.append(t1)

        for bt in b_threads:
            bt.start()
        
        # if there are broker actions, such as restarting the cluster, do that here
        if entry.has_broker_actions:
            # wait for trigger condition
            if entry.trigger_type == "seconds":
                console_out("RUNNER", f"Will perform broker action in {entry.trigger_at} seconds")
                time.sleep(entry.trigger_at)
            elif entry.trigger_type == "msgs":
                console_out("RUNNER", f"Will perform broker action at {entry.trigger_at} messages")
                broker_actions.wait_for_msg_trigger(configurations, common_conf, entry.trigger_at)
            else:
                console_out("RUNNER", "Unsupported trigger type")
                exit(1)
                

            # perform action
            if entry.broker_action == "restart-cluster":
                console_out("RUNNER", "Restarting all clusters...")
                broker_actions.restart_all_brokers(configurations, common_conf)
            elif entry.broker_action == "restart-broker":
                console_out("RUNNER", "Restarting one broker per cluster...")
                broker_actions.restart_one_broker(configurations, common_conf)

        # wait for the benchmark thread to complete
        try:
            for bt in b_threads:
                bt.join()
        except KeyboardInterrupt:
            console_out("RUNNER", "Aborting run...")
            deployer.teardown_all(configurations, common_conf.key_pair, common_conf.run_tag, common_conf.no_destroy)
            exit(1)
        
        # check if any benchmark instance failed and if so then teardown everything unless configured not to
        for config_tag in configurations:
            unique_conf_list = configurations[config_tag]
        
            for p in range(common_conf.parallel_count):
                unique_conf = unique_conf_list[p]
                status_id1 = unique_conf.technology + unique_conf.node_number
                if runner.get_benchmark_status(status_id1) != "success":
                    console_out("RUNNER", f"Benchmark failed for node {unique_conf.node_number} and topology {entry.topology}")
                    deployer.teardown_all(configurations, common_conf.key_pair, common_conf.run_tag, common_conf.no_destroy)
                    exit(1)

        # wait for configuration gap seconds
        console_out("RUNNER", f"Finished {entry.topology}")
        time.sleep(common_conf.gap_seconds)
        console_out("RUNNER", "Restarting all clusters before next topology...")
        broker_actions.restart_all_brokers(configurations, common_conf)
        time.sleep(60)
        run_ordinal += 1
        

    if common_conf.new_instance_per_run:
        deployer.teardown_all(configurations, common_conf.key_pair, common_conf.run_tag, common_conf.no_destroy)

if not common_conf.new_instance_per_run:
    for p in range(common_conf.parallel_count):
        deployer.teardown_all(configurations, common_conf.key_pair, common_conf.run_tag, common_conf.no_destroy)

console_out("RUNNER", f"RUN {common_conf.run_id} COMPLETE")