#!/bin/bash

echo "------------------------------"
echo "Deploying RabbitMQ cluster with args:"
AMI="$1"
echo "AMI=$AMI"
CLUSTER_SIZE="$2"
echo "CLUSTER_SIZE=$CLUSTER_SIZE"
CORE_COUNT="$3"
echo "CORE_COUNT=$CORE_COUNT"
INSTANCE="$4"
echo "INSTANCE=$INSTANCE"
KEY_PAIR="$5"
echo "KEY_PAIR=$KEY_PAIR"
LG_INSTANCE="$6"
echo "LG_INSTANCE=$LG_INSTANCE"
LG_SG="$7"
echo "LG_SG=$LG_SG"
NODE_NUMBER_START="$8"
echo "NODE_NUMBER_START=$NODE_NUMBER_START"
RUN_TAG="$9"
echo "RUN_TAG=$RUN_TAG"
SG="${10}"
echo "SG=$SG"
SN="${11}"
echo "SN=$SN"
TENANCY="${12}"
echo "TENANCY=$TENANCY"
TPC="${13}"
echo "TPC=$TPC"
VOL1_SIZE="${14}"
echo "VOL1_SIZE=$VOL1_SIZE"
VOL2_SIZE="${15}"
echo "VOL2_SIZE=$VOL2_SIZE"
VOL3_SIZE="${16}"
echo "VOL3_SIZE=$VOL3_SIZE"
VOL_TYPE="${17}"
echo "VOL_TYPE=$VOL_TYPE"
echo "------------------------------"

set -e

ROOT_PATH=$(pwd)

INFLUX_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_metrics" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
INFLUX_URL="http://$INFLUX_IP:8086"
echo "Node $NODE_NUMBER_START: InfluxDB url is $INFLUX_URL"

LAST_NODE=$(($CLUSTER_SIZE + $NODE_NUMBER_START - 1))
echo "LAST_NODE is $LAST_NODE"

# deploy broker and loadgen servers
for NODE in $(seq $NODE_NUMBER_START $LAST_NODE)
do
    TAG="benchmarking_rabbitmq${NODE}_${RUN_TAG}"
    RUNNING="$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=$TAG" "Name=instance-state-name,Values=running" --query "Reservations[*].Instances[*].InstanceId" --output=text)"
    if [ -z $RUNNING ]; then
        echo "Node $NODE: Creating instance for rabbitmq$NODE"

        if (( $NODE == $NODE_NUMBER_START )); then
            LG_INCLUDED="true"
        else 
            LG_INCLUDED="false"
        fi

        # if its a local storage instance then do not add extra ebs volume
        if [[ $INSTANCE == c5d* ]] || [[ $INSTANCE == i3* ]]; then
            bash deploy-local-storage-instance.sh $AMI $CORE_COUNT $INSTANCE $KEY_PAIR $LG_INSTANCE $LG_SG $NODE $RUN_TAG $SG $SN "rabbitmq" $TENANCY $TPC
        else
            bash deploy-ebs-instance.sh $AMI $CORE_COUNT $INSTANCE $KEY_PAIR $LG_INCLUDED $LG_INSTANCE $LG_SG $NODE $RUN_TAG $SG $SN "rabbitmq" $TENANCY $TPC $VOL1_SIZE $VOL2_SIZE $VOL3_SIZE $VOL_TYPE
        fi
    else
        echo "Node $NODE: Instance already exists, skipping EC2 instance creation"
    fi
done

# wait for servers to come online
for NODE in $(seq $NODE_NUMBER_START $LAST_NODE)
do
    TAG="benchmarking_rabbitmq${NODE}_${RUN_TAG}"
    RUNNING="$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=$TAG" "Name=instance-state-name,Values=running" --query "Reservations[*].Instances[*].InstanceId" --output=text)"
    while [ -z $RUNNING ]
    do
        echo "Node $NODE: Broker rabbitmq$NODE not ready yet, waiting 5 seconds"
        sleep 5
        RUNNING="$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=$TAG" "Name=instance-state-name,Values=running" --query "Reservations[*].Instances[*].InstanceId" --output=text)"
        echo $RUNNING
    done
done

echo "Node $NODE: Waiting 1 minute for rabbitmq EC2 instances to initialize"
sleep 60