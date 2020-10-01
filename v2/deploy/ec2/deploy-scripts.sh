#!/bin/bash

set -e

KEY_PAIR=$1
NODE_NUMBER=$2
RUN_TAG=$3
TECHNOLOGY=$4

BROKER_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
scp -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" "remote-scripts/start.sh" ubuntu@$BROKER_IP:.
scp -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" "remote-scripts/restart.sh" ubuntu@$BROKER_IP:.
scp -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" "remote-scripts/stop.sh" ubuntu@$BROKER_IP:.
scp -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" "remote-scripts/tc.sh" ubuntu@$BROKER_IP:.