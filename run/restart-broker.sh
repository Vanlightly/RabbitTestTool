#!/bin/bash

set -e

KEY_PAIR=$1
#echo "KEY_PAIR=$KEY_PAIR"
NODE_NUMBER=$2
#echo "NODE_NUMBER=$NODE_NUMBER"
RUN_TAG=$3
#echo "RUN_TAG=$RUN_TAG"
TECHNOLOGY=$4
#echo "TECHNOLOGY=$TECHNOLOGY"

BROKER_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
echo "-----------------"
echo "BROKER_ACTIONS: Restarting node at $BROKER_IP"

scp -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" "remote-scripts/restart.sh" ubuntu@$BROKER_IP:.
ssh -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" ubuntu@$BROKER_IP sudo bash restart.sh

echo "BROKER_ACTIONS: RabbitMQ node restarted on $BROKER_IP"
echo "-----------------"