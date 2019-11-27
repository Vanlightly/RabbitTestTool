#!/bin/bash

set -e

KEY_PAIR=$1
echo "KEY_PAIR=$KEY_PAIR"
NODE_NUMBER_START=$2
echo "NODE_NUMBER_START=$NODE_NUMBER_START"
NODE_NUMBER_END=$3
echo "NODE_NUMBER_END=$NODE_NUMBER_END"
RUN_TAG=$4
echo "RUN_TAG=$RUN_TAG"
TECHNOLOGY=$5
echo "TECHNOLOGY=$TECHNOLOGY"
TARGET_DIR=$6
echo "TARGET_DIR=$TARGET_DIR"

mkdir "$TARGET_DIR"

for NODE in $(seq $NODE_NUMBER_START $NODE_NUMBER_END)
do
    BROKER_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_${TECHNOLOGY}${NODE}_${RUN_TAG}" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
    ssh -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" ubuntu@${BROKER_IP} tar -zcvf rabbitmq${NODE}.tar.gz /rabbitmq/var/log/rabbitmq
    scp -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" ubuntu@${BROKER_IP}:rabbitmq${NODE}.tar.gz $TARGET_DIR/rabbitmq${NODE}.tar.gz
done