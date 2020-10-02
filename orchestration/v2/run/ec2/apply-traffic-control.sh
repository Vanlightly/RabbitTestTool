#!/bin/bash

set -e

VARIABLES_FILE=$1
source $VARIABLES_FILE

TAG="benchmarking_loadgen_${TECHNOLOGY}${FIRST_NODE}_${RUN_TAG}"
LOADGEN_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=$TAG" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
BROKER_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)

echo "Applying traffic control. BROKER_IP=$BROKER_IP LOADGEN_IP=$LOADGEN_IP"
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