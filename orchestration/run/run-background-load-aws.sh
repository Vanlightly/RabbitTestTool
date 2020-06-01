#!/bin/bash

set -e

BROKER_USER=$1
echo "BROKER_USER=$BROKER_USER"
BROKER_PASSWORD=$2
echo "BROKER_PASSWORD=*****"
CLUSTER_SIZE=$3
echo "CLUSTER_SIZE=$CLUSTER_SIZE"
KEY_PAIR=$4
echo "KEY_PAIR=$KEY_PAIR"
NODE_NUMBER=$5
echo "NODE_NUMBER=$NODE_NUMBER"
NODES=$6
echo "NODES=$NODES"
POLICY=$7
echo "POLICY=$POLICY"
OVERRIDE_STEP_SECONDS=$8
echo "OVERRIDE_STEP_SECONDS=$OVERRIDE_STEP_SECONDS"
OVERRIDE_STEP_REPEAT=$9
echo "OVERRIDE_STEP_REPEAT=$OVERRIDE_STEP_REPEAT"
RUN_TAG=${10}
echo "RUN_TAG=$RUN_TAG"
TECHNOLOGY=${11}
echo "TECHNOLOGY=$TECHNOLOGY"
TOPOLOGY=${12}
echo "TOPOLOGY=$TOPOLOGY"
VERSION=${13}
echo "VERSION=$VERSION"

LAST_NODE=$(($NODE_NUMBER + $CLUSTER_SIZE - 1))

INFLUX_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_metrics" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
echo "Will connect to InfluxDB at ip $INFLUX_IP"

BROKER_IPS=""
for NODE in $(seq $NODE_NUMBER $LAST_NODE)
do
    BROKER_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_${TECHNOLOGY}${NODE}_${RUN_TAG}" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)

    if [[ $NODE != $LAST_NODE ]]; then
        BROKER_IPS+="${BROKER_IP}:5672,"
    else
        BROKER_IPS+="${BROKER_IP}:5672"
    fi
done

LOADGEN_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_loadgen_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)

echo "Starting background load from instance at ip $LOADGEN_IP against cluster with ips $BROKER_IPS"

ssh -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -q ubuntu@$LOADGEN_IP java -Xms4096m -Xmx8192m -jar rabbittesttool-1.1-SNAPSHOT-jar-with-dependencies.jar \
--mode benchmark \
--topology "./topologies/${TOPOLOGY}" \
--policies "./policies/${POLICY}" \
--technology "$TECHNOLOGY" \
--version "$VERSION" \
--broker-hosts "$BROKER_IPS" \
--broker-mgmt-port 15672 \
--broker-port 5672 \
--broker-user "$BROKER_USER" \
--broker-password "$BROKER_PASSWORD" \
--broker-vhost benchmark \
--override-step-seconds "$OVERRIDE_STEP_SECONDS" \
--override-step-repeat "$OVERRIDE_STEP_REPEAT" \
--print-live-stats false

#&> /dev/null

echo "Background load completed on instance at ip $LOADGEN_IP against cluster with ips $BROKER_IPs"