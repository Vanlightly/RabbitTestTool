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
TARGET_IPS=$5
DELAY=$6
DELAY_JITTER=$7
DELAY_DIST=$8
BANDWIDTH=$9
PACKET_LOSS_MODE=${10}
PACKET_LOSS_ARG1=${11}
PACKET_LOSS_ARG2=${12}
PACKET_LOSS_ARG3=${13}
PACKET_LOSS_ARG4=${14}

BROKER_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
ssh -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" ubuntu@$BROKER_IP sudo bash tc.sh \
$TARGET_IPS \
$DELAY \
$DELAY_JITTER \
$DELAY_DIST \
$BANDWIDTH \
$PACKET_LOSS_MODE \
$PACKET_LOSS_ARG1 \
$PACKET_LOSS_ARG2 \
$PACKET_LOSS_ARG3 \
$PACKET_LOSS_ARG4