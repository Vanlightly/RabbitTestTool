#!/bin/bash

KEY_PAIR=$1
NODE_NUMBER=$2
RUN_TAG=$3
TECHNOLOGY=$4

BROKER_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
echo "Removing traffic control. BROKER_IP=$BROKER_IP"
ssh -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" ubuntu@$BROKER_IP sudo tc qdisc del dev ens5 root > /dev/null 2>&1