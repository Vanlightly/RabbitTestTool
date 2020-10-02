#!/bin/bash

set -e

KEY_PAIR=$1
NODE_NUMBER=$2
CLUSTER_SIZE=$3
RUN_TAG=$4
CLIENT_DELAY=$5
START_DELAY=$6
INCREMENT=$7
STEPS=$8
DURATION_SEC=$9
BROKERS=${10}

DELAY=0
STEP=1
while [  $STEP -lt $STEPS ]; do
        
    echo "$DELAY"

    if [[ $BROKERS == "all" ]]; then
        bash traffic-control-add-to-all-nodes.sh benchmarking $NODE_NUMBER $CLUSTER_SIZE $RUN_TAG $CLIENT_DELAY $DELAY 0 none 0 none 0% 0% 0% 0%
    else
        bash traffic-control-add-to-one-node.sh benchmarking $NODE_NUMBER $NODE_NUMBER $CLUSTER_SIZE $RUN_TAG $CLIENT_DELAY $DELAY 0 none 0 none 0% 0% 0% 0%
    fi

    sleep $DURATION_SEC
    ADD=$(( $STEP*$INCREMENT ))
    DELAY=$(( $START_DELAY + $ADD ))
    STEP=$(( $STEP + 1 ))
done

START_DELAY=$(( $DELAY - $INCREMENT ))
STEP=0
while [  $STEP -lt $STEPS ]; do
        
    echo "$DELAY"

    if [[ $BROKERS == "all" ]]; then
        bash traffic-control-add-to-all-nodes.sh benchmarking $NODE_NUMBER $CLUSTER_SIZE $RUN_TAG $CLIENT_DELAY $DELAY 0 none 0 none 0% 0% 0% 0%
    else
        bash traffic-control-add-to-one-node.sh benchmarking $NODE_NUMBER $NODE_NUMBER $CLUSTER_SIZE $RUN_TAG $CLIENT_DELAY $DELAY 0 none 0 none 0% 0% 0% 0%
    fi

    sleep $DURATION_SEC
    ADD=$(( $STEP*$INCREMENT ))
    DELAY=$(( $START_DELAY - $ADD ))
    STEP=$(( $STEP + 1 ))
done