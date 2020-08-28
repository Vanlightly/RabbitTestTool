#!/bin/bash

set -e

KEY_PAIR=$1
FIRST_NODE_NUMBER=$2
TARGET_NODE_NUMBER=$3
CLUSTER_SIZE=$4
RUN_TAG=$5
CLIENT_DELAY=$6
DELAY=$7
DELAY_JITTER=$8
DELAY_DIST=$9
BANDWIDTH=${10}
PACKET_LOSS_MODE=${11}
PACKET_LOSS_ARG1=${12}
PACKET_LOSS_ARG2=${13}
PACKET_LOSS_ARG3=${14}
PACKET_LOSS_ARG4=${15}

TARGET_IPS=""

COUNTER=0
LIMIT=$(( $CLUSTER_SIZE - 1 ))
LAST_NODE=$(( $FIRST_NODE_NUMBER + $CLUSTER_SIZE - 1 ))
for OTHER_NODE in $(seq $FIRST_NODE_NUMBER $LAST_NODE)
do
    if [[ $TARGET_NODE_NUMBER != $OTHER_NODE ]]; then
        COUNTER=$(( $COUNTER + 1 ))
        BROKER_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_rabbitmq${OTHER_NODE}_${RUN_TAG}" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
        TARGET_IPS="${TARGET_IPS}${BROKER_IP}"

        if [[ $COUNTER != $LIMIT ]]; then
            TARGET_IPS="${TARGET_IPS},"
        fi
    fi
done

echo "Applying traffic control to node $TARGET_NODE_NUMBER with target ips $TARGET_IPS"
bash apply-traffic-control.sh \
$KEY_PAIR \
$FIRST_NODE_NUMBER \
$TARGET_NODE_NUMBER \
$RUN_TAG \
rabbitmq \
$TARGET_IPS \
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