#!/usr/bin/env python

import os
import sys
import datetime
import re
import glob

def increment(ctr, broker):
    if broker in ctr:
        ctr[broker] = ctr[broker] + 1
    else:
        ctr[broker] = 1

log_dir = sys.argv[1]

brokers = set()

files = list()
# for entry in os.listdir(log_dir):
#     if os.path.isfile(os.path.join(log_dir, entry)):
#         file_path = os.path.join(log_dir, entry)
#         if ".log" in file_path:
#             files.append(file_path)

for filename in glob.iglob(log_dir + '**/*.log*', recursive=True):
    if not "crash.log" in filename and not "upgrade.log" in filename:
        broker_match = re.match(r".+(rabbit@[a-zA-Z0-9_\.-]+)\.log.*", filename)
        if not broker_match:
            print(f"Ignoring {filename}")
        else:
            files.append(filename)
            print(f"Analysing {filename}")
        

start_up_ctr = dict()
stop_ctr = dict()
node_down_ctr = dict()
node_up_ctr = dict()
become_leader_ctr = dict()
abdicate_ctr = dict()
leader_down_ctr = dict()
maybe_leader_down_ctr = dict()
leader_backup_ctr = dict()
setting_election_timeout_ctr = dict()
election_called_ctr = dict()
prevote_election_called_ctr = dict()
granting_prevote_ctr = dict()
detected_leader_ctr = dict()
granting_vote_for_ctr = dict()
terminating_ctr = dict()

for file in files:
    with open(file) as fp:
        broker_match = re.match(r".+(rabbit@[a-zA-Z0-9_\.-]+)\.log.*", file)
        if not broker_match:
            print("Ignoring: " + file)
            continue

        broker_name = broker_match.group(1)
        brokers.add(broker_name)

        for cnt, line in enumerate(fp):
            
            if line.startswith("2"):
                date_time_str = line[0:23]

            if "Server startup complete" in line:
                increment(start_up_ctr, broker_name)
            elif "Successfully stopped RabbitMQ" in line:
                increment(stop_ctr, broker_name)
            elif "rabbit on node" in line:
                if "down" in line:
                    increment(node_down_ctr, broker_name)
                elif "up" in line:
                    increment(node_up_ctr, broker_name)
            elif "candidate -> leader" in line:
                increment(become_leader_ctr, broker_name)
            elif "abdicates term" in line:
                increment(abdicate_ctr, broker_name)
            elif "Leader monitor down with shutdown" in line:
                increment(leader_down_ctr, broker_name)
            elif "may be down, setting pre-vote timeout" in line:
                increment(maybe_leader_down_ctr, broker_name)
            elif "is back up, cancelling pre-vote timeout" in line:
                increment(leader_backup_ctr, broker_name)
            elif "is not new, setting election timeout" in line:
                increment(setting_election_timeout_ctr, broker_name)
            elif ": election called for in term" in line:
                increment(election_called_ctr, broker_name)
            elif ": pre_vote election called for in term" in line:
                increment(prevote_election_called_ctr, broker_name)
            elif "granting pre-vote for" in line:
                increment(granting_prevote_ctr, broker_name)
            elif "detected a new leader" in line:
                increment(detected_leader_ctr, broker_name)
            elif "granting vote for" in line:
                increment(granting_vote_for_ctr, broker_name)
            elif "terminating with" in line:
                increment(terminating_ctr, broker_name)

brokers_list = list(brokers)

for broker in brokers_list:
    print("")
    print(f"{broker}")
    print(f"start_up_ctr: {start_up_ctr[broker]}")
    print(f"stop_ctr: {stop_ctr[broker]}")
    print(f"node_up_ctr: {node_up_ctr[broker]}")
    print(f"node_down_ctr: {node_down_ctr[broker]}")
    print(f"become_leader_ctr: {become_leader_ctr[broker]}")
    print(f"abdicate_ctr: {abdicate_ctr[broker]}")
    print(f"leader_down_ctr: {leader_down_ctr[broker]}")
    print(f"maybe_leader_down_ctr: {maybe_leader_down_ctr[broker]}")
    print(f"leader_backup_ctr: {leader_backup_ctr[broker]}")
    print(f"setting_election_timeout_ctr: {setting_election_timeout_ctr[broker]}")
    print(f"election_called_ctr: {election_called_ctr[broker]}")
    print(f"granting_prevote_ctr: {granting_prevote_ctr[broker]}")
    print(f"detected_leader_ctr: {detected_leader_ctr[broker]}")
    print(f"granting_vote_for_ctr: {granting_vote_for_ctr[broker]}")
    print(f"terminating_ctr: {terminating_ctr[broker]}")
    