#!/bin/bash

set -e

CLUSTER_SIZE=$1
echo "CLUSTER_SIZE=$CLUSTER_SIZE"
NODE_NUMBER=$2
echo "NODE_NUMBER=$NODE_NUMBER"
RUN_TAG=$3
echo "RUN_TAG=$RUN_TAG"
TECHNOLOGY=$4
echo "TECHNOLOGY=$TECHNOLOGY"

BROKER_IPS=""
LAST_NODE=($NODE_NUMBER + $CLUSTER_SIZE - 1)

for NODE in $(seq $NODE_NUMBER $LAST_NODE)
do
    BROKER_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_${TECHNOLOGY}${NODE}_${RUN_TAG}" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)

    if [[ $NODE != $LAST_NODE ]]; then
        BROKER_IPS+="${BROKER_IP}:5672,"
    else
        BROKER_IPS+="${BROKER_IP}:5672"
    fi
done

echo "BROKER_IPS=$BROKER_IPS"
