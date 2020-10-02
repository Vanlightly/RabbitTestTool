#!/usr/bin/env python

import sys
import datetime

file = sys.argv[1]

conns_opened = 0
conns_closed = 0

conns_opened_delta = 0
conns_closed_delta = 0
heartsbeats_missed_delta = 0
connection_limit_reached_delta = 0
node_down_delta = 0
node_up_delta = 0

last_printed = datetime.datetime.strptime("2000-01-01 00:00:00", '%Y-%m-%d %H:%M:%S')

with open(file) as fp:
    for cnt, line in enumerate(fp):
        
        if "Starting RabbitMQ" in line:
            conns_opened = 0
            conns_closed = 0
            conns_opened_delta = 0
            conns_closed_delta = 0
            heartsbeats_missed_delta = 0
            connection_limit_reached_delta = 0
            node_down_delta = 0
            node_up_delta = 0
        elif "missed heartbeats" in line:
            heartsbeats_missed_delta += 1
        elif "connection limit" in line:
            connection_limit_reached_delta += 1
        elif "accepting AMQP connection" in line:
            conns_opened +=1
            conns_opened_delta += 1
        elif "closing AMQP connection" in line:
            conns_closed +=1
            conns_closed_delta += 1
        elif "rabbit on node" in line:
                if "down" in line:
                    node_down_delta += 1
                elif "up" in line:
                    node_up_delta += 1

        if line.startswith("20"):
            date_time_str = line[0:19]
            dt = datetime.datetime.strptime(date_time_str, '%Y-%m-%d %H:%M:%S')

            if (dt - last_printed).total_seconds() > 10.0:
                print(f"{date_time_str} - opened: {conns_opened_delta} closed: {conns_closed_delta} missed heartbeats: {heartsbeats_missed_delta} conn limit: {connection_limit_reached_delta} node up: {node_up_delta} node down: {node_down_delta}")
                last_printed = dt
                conns_opened_delta = 0
                conns_closed_delta = 0
                heartsbeats_missed_delta = 0
                connection_limit_reached_delta = 0
                node_down_delta = 0
                node_up_delta = 0
