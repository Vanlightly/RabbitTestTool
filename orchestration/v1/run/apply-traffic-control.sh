#!/bin/bash

set -e

KEY_PAIR=$1
#echo "KEY_PAIR=$KEY_PAIR"
FIRST_NODE=$2
NODE_NUMBER=$3
#echo "NODE_NUMBER=$NODE_NUMBER"
RUN_TAG=$4
#echo "RUN_TAG=$RUN_TAG"
TECHNOLOGY=$5
#echo "TECHNOLOGY=$TECHNOLOGY"
TARGET_IPS=$6
CLIENT_DELAY=$7
DELAY=$8
DELAY_JITTER=$9
DELAY_DIST=${10}
BANDWIDTH=${11}
PACKET_LOSS_MODE=${12}
PACKET_LOSS_ARG1=${13}
PACKET_LOSS_ARG2=${14}
PACKET_LOSS_ARG3=${15}
PACKET_LOSS_ARG4=${16}

TAG="benchmarking_loadgen_${TECHNOLOGY}${FIRST_NODE}_${RUN_TAG}"
LOADGEN_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=$TAG" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
echo "LOADGEN_IP=$LOADGEN_IP"

BROKER_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
ssh -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" ubuntu@$BROKER_IP sudo bash tc.sh \
$TARGET_IPS \
$LOADGEN_IP \
$CLIENT_DELAY \
$DELAY \
$DELAY_JITTER \
$DELAY_DIST \
$BANDWIDTH \
$PACKET_LOSS_MODE \
$PACKET_LOSS_ARG1 \
$PACKET_LOSS_ARG2 \
$PACKET_LOSS_ARG3 \
$PACKET_LOSS_ARG4