#!/bin/bash

set -e

KEY_PAIR=$1
NODE_NUMBER=$2
CLUSTER_SIZE=$3
RUN_TAG=$4
CLIENT_DELAY=$5
DELAY=$6
DELAY_JITTER=$7
DELAY_DIST=$8
BANDWIDTH=$9
PACKET_LOSS_MODE=${10}
PACKET_LOSS_ARG1=${11}
PACKET_LOSS_ARG2=${12}
PACKET_LOSS_ARG3=${13}
PACKET_LOSS_ARG4=${14}

LAST_NODE=$(( $NODE_NUMBER + $CLUSTER_SIZE - 1 ))
for NODE in $(seq $NODE_NUMBER $LAST_NODE)
do
    TARGET_IPS=""

    COUNTER=0
    LIMIT=$(( $CLUSTER_SIZE - 1 ))
    for OTHER_NODE in $(seq $NODE_NUMBER $LAST_NODE)
    do
        if [[ $NODE != $OTHER_NODE ]]; then
            COUNTER=$(( $COUNTER + 1 ))
            BROKER_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_rabbitmq${OTHER_NODE}_${RUN_TAG}" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
            TARGET_IPS="${TARGET_IPS}${BROKER_IP}"

            if [[ $COUNTER != $LIMIT ]]; then
                TARGET_IPS="${TARGET_IPS},"
            fi
        fi
    done

    echo "Applying traffic control to node $NODE with target ips $TARGET_IPS"
    bash apply-traffic-control.sh \
    $KEY_PAIR \
    $NODE_NUMBER \
    $NODE \
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
done