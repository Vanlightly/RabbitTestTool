#!/bin/bash

set -e

KEY_PAIR=$1
echo "KEY_PAIR=$KEY_PAIR"
NODE_NUMBER=$2
echo "NODE_NUMBER=$NODE_NUMBER"
RUN_TAG=$3
echo "RUN_TAG=$RUN_TAG"
TECHNOLOGY=$4
echo "TECHNOLOGY=$TECHNOLOGY"
USER_NAME=$5
echo "USER_NAME=$USER_NAME"
USER_PASS=$6
echo "USER_PASS=*****"

echo BROKER_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)