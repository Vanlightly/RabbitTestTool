#!/bin/bash

# # $1 key pair
# # $2 node
# # $3 technology

# set -e

# echo "copying topologies and jar file"
# LOADGEN_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_loadgen_$3$2" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
# scp -i "~/.ssh/$1" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" "../../../target/rabbittesttool-1.0-SNAPSHOT-jar-with-dependencies.jar" ubuntu@$LOADGEN_IP:.
# scp -i "~/.ssh/$1" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -r "../../topologies" ubuntu@$LOADGEN_IP:.
# echo "copying complete"

echo "---------------------------"
echo "Update benchmark with args:"
KEY_PAIR="$1"
echo "KEY_PAIR=$KEY_PAIR"
NODE_NUMBER="$2"
echo "NODE_NUMBER=$NODE_NUMBER"
TECHNOLOGY="$3"
echo "TECHNOLOGY=$TECHNOLOGY"
RUN_TAG="$4"
echo "RUN_TAG=$RUN_TAG"
echo "---------------------------"

set -e

echo "Loadgen Node $NODE_NUMBER: Copying topologies and jar file"
TAG="benchmarking_loadgen_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}"
LOADGEN_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=$TAG" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)

ssh -i "~/.ssh/${KEY_PAIR}.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" ubuntu@$LOADGEN_IP rm -rf *
scp -i "~/.ssh/${KEY_PAIR}.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" "../../benchmark/target/rabbittesttool-1.0-SNAPSHOT-jar-with-dependencies.jar" ubuntu@$LOADGEN_IP:.
scp -i "~/.ssh/${KEY_PAIR}.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -r "../../benchmark/topologies" ubuntu@$LOADGEN_IP:.
scp -i "~/.ssh/${KEY_PAIR}.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -r "../../benchmark/policies" ubuntu@$LOADGEN_IP:.
echo "Loadgen Node $NODE_NUMBER: Copying complete"