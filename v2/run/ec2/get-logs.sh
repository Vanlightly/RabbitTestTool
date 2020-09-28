#!/bin/bash

KEY_PAIR=$1
echo "KEY_PAIR=$KEY_PAIR"
LOGS_VOLUME=$2
echo "LOGS_VOLUME=$LOGS_VOLUME"
NODE_NUMBER_START=$3
echo "NODE_NUMBER_START=$NODE_NUMBER_START"
NODE_NUMBER_END=$4
echo "NODE_NUMBER_END=$NODE_NUMBER_END"
RUN_TAG=$5
echo "RUN_TAG=$RUN_TAG"
TECHNOLOGY=$6
echo "TECHNOLOGY=$TECHNOLOGY"
TARGET_DIR=$7
echo "TARGET_DIR=$TARGET_DIR"

mkdir -p "$TARGET_DIR"

for NODE in $(seq $NODE_NUMBER_START $NODE_NUMBER_END)
do
    BROKER_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_${TECHNOLOGY}${NODE}_${RUN_TAG}" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
    #ssh -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" ubuntu@${BROKER_IP} sudo chmod -R 744 /${LOGS_VOLUME}/logs
    ssh -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" ubuntu@${BROKER_IP} sudo tar -zcvf rabbitmq${NODE}.tar.gz /${LOGS_VOLUME}/logs
    scp -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" ubuntu@${BROKER_IP}:rabbitmq${NODE}.tar.gz $TARGET_DIR/rabbitmq${NODE}.tar.gz
done