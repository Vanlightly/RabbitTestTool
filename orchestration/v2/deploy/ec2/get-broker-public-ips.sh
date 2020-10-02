#!/bin/bash

set -e

CLUSTER_SIZE=$1
NODE_NUMBER=$2
RUN_TAG=$3
TECHNOLOGY=$4

BROKER_IPS=""
LAST_NODE=$(($NODE_NUMBER + $CLUSTER_SIZE - 1))

for NODE in $(seq $NODE_NUMBER $LAST_NODE)
do
    BROKER_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_${TECHNOLOGY}${NODE}_${RUN_TAG}" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
    
    if [[ $NODE == $NODE_NUMBER ]]; then
        BROKER_IPS="${BROKER_IP}"
    else
        BROKER_IPS="${BROKER_IPS},${BROKER_IP}"
    fi
done

echo "$BROKER_IPS"
