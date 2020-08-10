#!/usr/bin/env python

import os
import sys
import datetime
import re

def get_queue_name(line):
    match = re.match(".+queue \'([a-zA-Z0-9_\.-]+)\' in vhost.+", line)

    if not match:
        print("queue: " + line)
        exit(1)

    return match.group(1)

def get_vhost_name(line):
    match = re.match(".+in vhost '([a-zA-Z0-9_\\\/\.-]+)'", line)

    if not match:
        print("vhost: " + line)
        exit(1)

    return match.group(1)

def get_term(line):
    match = re.match(".+in term: ([0-9]+).*", line)

    if not match:
        match = re.match(".+in term ([0-9]+).*", line)
    
    if not match:
        match = re.match(".+for term ([0-9]+).*", line)
    
    if not match:
        print("term: " + line)
        exit(1)

    return match.group(1)


def get_abdicated_term(line):
    match = re.match(".+abdicates term: ([0-9]+).*", line)

    if not match:
        print("abdicates term: " + line)
        exit(1)

    return match.group(1)

log_dir = sys.argv[1]
target_queue = sys.argv[2]
target_vhost = sys.argv[3]

#Refunding.SendCustomsRefundMessage

# queue 'text' in vhost 'text/2001.0': detected a new leader {'text/2001.0_text.text','text@test-1234'} in 

# 2020-07-26 18:33:30.460 [notice] <0.1783.0> queue 'Refunding.SendCustomsRefundMessage' in vhost 'spainadaptertemp/2001.0': leader -> follower in term: 2574
# 2020-07-26 18:33:30.460 [info] <0.1783.0> queue 'Refunding.SendCustomsRefundMessage' in vhost 'spainadaptertemp/2001.0': detected a new leader {'spainadaptertemp/2001.0_Refunding.SendCustomsRefundMessage','rabbit@SA-MAS-0152'} in term 2574
# 2020-07-26 18:33:30.460 [info] <0.1783.0> queue 'Refunding.SendCustomsRefundMessage' in vhost 'spainadaptertemp/2001.0': granting vote for {'spainadaptertemp/2001.0_Refunding.SendCustomsRefundMessage','rabbit@SA-MAS-0152'} with last indexterm {15790,2573} for term 2574 previous term was 2574
# 2020-07-22 00:12:44.200 [info] <0.1999.0> queue 'Deduplication.TfsVoidMessage' in vhost 'spainadaptertemp/2001.0': Leader monitor down with shutdown, setting election timeout
# 2020-07-28 23:29:10.232 [notice] <0.2019.0> queue 'Refunding.SendCustomsRefundMessage' in vhost 'spainadaptertemp/2001.0': leader saw append_entries_reply from {'spainadaptertemp/2001.0_Refunding.SendCustomsRefundMessage','rabbit@SA-MAS-0151'} for term 2662 abdicates term: 2661!
# 2020-07-29 01:08:03.085 [info] <0.2852.0> queue 'Refunding.SendCustomsRefundMessage' in vhost 'spainadapter/2001.1': terminating with shutdown in state follower
# 2020-07-30 23:33:33.133 [info] <0.2083.0> queue 'Issuing.ReissueCustomsIssueMessage' in vhost 'spainadapter/2001.0': terminating with {timeout,{gen_batch_server,call,[ra_log_meta,{store,<<83,80,65,73,78,65,72,52,53,74,83,76,74,88,48,74,53,86>>,voted_for,{'spainadapter/2001.0_Issuing.ReissueCustomsIssueMessage','rabbit@SA-MAS-0152'}},30000]}} in state follower

events = list()
date_time_str = ""
brokers = set()

files = list()
for entry in os.listdir(log_dir):
    if os.path.isfile(os.path.join(log_dir, entry)):
        file_path = os.path.join(log_dir, entry)
        if ".log" in file_path:
            files.append(file_path)
        

for file in files:
    with open(file) as fp:
        broker_match = re.match(r".+(rabbit@[a-zA-Z0-9_\.-]+)\.log.*", file)
        if not broker_match:
            print("Could not extract broker name from log file name")

        broker_name = broker_match.group(1)
        brokers.add(broker_name)

        for cnt, line in enumerate(fp):
            
            if line.startswith("2"):
                date_time_str = line[0:23]

            if "Server startup complete" in line:
                events.append((date_time_str, "broker_start_up_complete", broker_name, "", ""))
            elif "Starting RabbitMQ" in line and "on Erlang" in line:
                events.append((date_time_str, "broker_start_up_begin", broker_name, "", ""))
            elif "RabbitMQ is asked to stop" in line:
                events.append((date_time_str, "broker_stop_begin", broker_name, "", ""))
            elif "Successfully stopped RabbitMQ" in line:
                events.append((date_time_str, "broker_stop_complete", broker_name, "", ""))
            elif "rabbit on node" in line:
                if "down" in line:
                    match = re.match(r".+rabbit on node '([a-zA-Z0-9_\.-]+@[a-zA-Z0-9_\.-]+)' down.*", line)
                    if not match:
                        print("down: " + line)
                        exit(1)
                    broker_down = match.group(1)
                    events.append((date_time_str, "broker_down", broker_name, "", broker_down))    
                elif "up" in line:
                    match = re.match(r".+rabbit on node '([a-zA-Z0-9_\.-]+@[a-zA-Z0-9_\.-]+)' up.*", line)
                    if not match:
                        print("up: " + line)
                        exit(1)
                    broker_up = match.group(1)
                    events.append((date_time_str, "broker_up", broker_name, "", broker_up))    
            elif "candidate -> leader" in line:
                queue = get_queue_name(line)
                vhost = get_vhost_name(line)
                term = get_term(line)
                if queue == target_queue and vhost == target_vhost:
                    vhost = get_vhost_name(line)
                    events.append((date_time_str, "become_leader", broker_name, queue, "", term))
            elif "abdicates term" in line:
                queue = get_queue_name(line)
                vhost = get_vhost_name(line)
                term = get_term(line)
                abdicated_term = get_abdicated_term(line)

                reason = ""
                match = re.match(r".+leader saw append_entries_reply from \{\'.+\','([a-zA-Z0-9_\.-]+@[a-zA-Z0-9_\.-]+)'\}.+", line)
                reason = "append_entries"
                if not match:
                    match = re.match(r".+leader saw request_vote_rpc from \{\'.+\','([a-zA-Z0-9_\.-]+@[a-zA-Z0-9_\.-]+)'\}.+", line)
                    reason = "request_vote"

                if not match:
                    match = re.match(r".+leader saw pre_vote_rpc from \{\'.+\','([a-zA-Z0-9_\.-]+@[a-zA-Z0-9_\.-]+)'\}.+", line)
                    reason = "pre_vote"
                
                if not match:
                    print("abdicates: " + line)
                    exit(1)

                other_broker = match.group(1)

                if queue == target_queue and vhost == target_vhost:
                    events.append((date_time_str, "abdicates_term " + abdicated_term, broker_name, queue, other_broker, term, reason))
            elif "Leader monitor down with shutdown" in line:
                queue = get_queue_name(line)
                vhost = get_vhost_name(line)
                if queue == target_queue and vhost == target_vhost:
                    events.append((date_time_str, "leader_down", broker_name, queue, "", ""))
            elif "detected a new leader" in line:
                queue = get_queue_name(line)
                vhost = get_vhost_name(line)
                term = get_term(line)
                if queue == target_queue and vhost == target_vhost:
                    match = re.match(r".+detected a new leader \{\'.+\','([a-zA-Z0-9_\.-]+@[a-zA-Z0-9_\.-]+)'\}.+", line)
                    if not match:
                        print("new leader: " + line)
                        exit(1)
                    new_leader = match.group(1)
                    events.append((date_time_str, "detect_leader", broker_name, queue, new_leader, term))
            elif "granting vote for" in line:
                queue = get_queue_name(line)
                vhost = get_vhost_name(line)
                term = get_term(line)
                if queue == target_queue and vhost == target_vhost:
                    match = re.match(r".+granting vote for \{\'.+\','([a-zA-Z0-9_\.-]+@[a-zA-Z0-9_\.-]+)'\}.+", line)
                    if not match:
                        print("vote: " + line)
                        exit(1)
                    votes_for_broker = match.group(1)
                    events.append((date_time_str, "votes_for", broker_name, queue, votes_for_broker, term))
            elif "terminating with" in line:
                queue = get_queue_name(line)
                vhost = get_vhost_name(line)

                if queue == target_queue and vhost == target_vhost:
                    match = re.match(r".+terminating with (.+) in state ([a-zA-Z]+).*", line)
                    if match:
                        full_reason = match.group(1)
                        state = match.group(2)

                    if not match:
                        match = re.match(r".+terminating with reason (.+)", line)
                        full_reason = match.group(1)
                        state = "?"
                    
                    if not match:
                        print("terminating: " + line)
                        exit(1)

                    if "timeout" in full_reason:
                        reason = "timeout"
                    elif "shutdown" in full_reason:
                        reason = "shutdown"
                    else:
                        print("Unexpected reason: " + full_state)
                        exit(1)


                    events.append((date_time_str, "terminating", broker_name, queue, reason, state))

events_sorted = sorted(events, key=lambda x: x[0])

brokers_list = list(brokers)
print("time,queue,vhost," + ",".join(brokers_list))

for event in events_sorted:
    ts = event[0]
    event_type = event[1]
    broker = event[2]
    queue = event[3]
    other_broker = event[4]


    whitespace = "".rjust(50 * brokers_list.index(broker), " ")

    if event_type == "broker_up" or event_type == "broker_down":
        value = f"{broker} sees {event_type} for {other_broker}"
    elif event_type.startswith("broker_start") or event_type.startswith("broker_stop"):
        value = f"{broker} {event_type}"
    elif event_type == "become_leader":
        value = f"{broker} {event_type} of term {event[5]}"
    elif event_type == "leader_down":
        value = f"{broker} {event_type}"
    elif event_type.startswith("abdicates_term"):
        value = f"{broker} {event_type} due to {event[6]} from {other_broker} term {event[5]}"        
    elif event_type == "terminating":
        value = f"{broker} {event_type} reason={event[4]} state={event[5]}"
    else:
        value = f"{broker} {event_type} {other_broker} term {event[5]}"

    values = list()
    values.append(ts)
    values.append(target_queue)
    values.append(target_vhost)
    broker_index = brokers_list.index(broker)
    for index in range(len(brokers_list)):
        if index == broker_index:
            values.append(value)
        else:
            values.append("")

    print(",".join(values))
    