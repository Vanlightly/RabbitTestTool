#!/bin/bash

set -e

KEY_PAIR=$1
NODE_NUMBER=$2
CLUSTER_SIZE=$3
RUN_TAG=$4
DELAY=$5
DELAY_JITTER=$6
DELAY_DIST=$7
BANDWIDTH=$8
PACKET_LOSS_MODE=$9
PACKET_LOSS_ARG1=${10}
PACKET_LOSS_ARG2=${11}
PACKET_LOSS_ARG3=${12}
PACKET_LOSS_ARG4=${13}

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
            BROKER_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_rabbitmq${OTHER_NODE}_${RUN_TAG}" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
            TARGET_IPS="${TARGET_IPS}${BROKER_IP}"

            if [[ $COUNTER != $LIMIT ]]; then
                TARGET_IPS="${TARGET_IPS},"
            fi
        fi
    done

    echo "Applying traffic control to node $NODE with target ips $TARGET_IPS"
    bash apply-traffic-control.sh \
    $KEY_PAIR \
    $NODE \
    $RUN_TAG \
    rabbitmq \
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
done