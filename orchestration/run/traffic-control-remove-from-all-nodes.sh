#!/bin/bash

KEY_PAIR=$1
NODE_NUMBER=$2
CLUSTER_SIZE=$3
RUN_TAG=$4

LAST_NODE=$(( $NODE_NUMBER + $CLUSTER_SIZE - 1 ))
for NODE in $(seq $NODE_NUMBER $LAST_NODE)
do
    BROKER_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_rabbitmq${NODE}_${RUN_TAG}" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
    ssh -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" ubuntu@$BROKER_IP sudo tc qdisc del dev ens5 root > /dev/null 2>&1
    echo "Removed traffic control from $BROKER_IP"
done
