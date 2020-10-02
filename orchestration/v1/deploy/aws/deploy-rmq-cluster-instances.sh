#!/bin/bash

echo "------------------------------"
echo "Deploying RabbitMQ cluster with args:"
AMI="$1"
echo "AMI=$AMI"
ARM_AMI="$2"
echo "ARM_AMI=$ARM_AMI"
CLUSTER_SIZE="$3"
echo "CLUSTER_SIZE=$CLUSTER_SIZE"
CORE_COUNT="$4"
echo "CORE_COUNT=$CORE_COUNT"
INFLUX_SUBPATH="$5"
echo "INFLUX_SUBPATH=$INFLUX_SUBPATH"
INSTANCE="$6"
echo "INSTANCE=$INSTANCE"
KEY_PAIR="$7"
echo "KEY_PAIR=$KEY_PAIR"
LG_INSTANCE="$8"
echo "LG_INSTANCE=$LG_INSTANCE"
LG_SG="$9"
echo "LG_SG=$LG_SG"
NODE_NUMBER_START="${10}"
echo "NODE_NUMBER_START=$NODE_NUMBER_START"
RUN_TAG="${11}"
echo "RUN_TAG=$RUN_TAG"
SG="${12}"
echo "SG=$SG"
SUBNETS="${13}"
echo "SUBNETS=$SUBNETS"
TENANCY="${14}"
echo "TENANCY=$TENANCY"
TPC="${15}"
echo "TPC=$TPC"
VOL1_IOPS_PER_GB="${16}"
echo "VOL1_IOPS_PER_GB=$VOL1_IOPS_PER_GB"
VOL2_IOPS_PER_GB="${17}"
echo "VOL2_IOPS_PER_GB=$VOL2_IOPS_PER_GB"
VOL3_IOPS_PER_GB="${18}"
echo "VOL3_IOPS_PER_GB=$VOL3_IOPS_PER_GB"
VOL1_SIZE="${19}"
echo "VOL1_SIZE=$VOL1_SIZE"
VOL2_SIZE="${20}"
echo "VOL2_SIZE=$VOL2_SIZE"
VOL3_SIZE="${21}"
echo "VOL3_SIZE=$VOL3_SIZE"
VOL1_TYPE="${22}"
echo "VOL1_TYPE=$VOL1_TYPE"
VOL2_TYPE="${23}"
echo "VOL2_TYPE=$VOL2_TYPE"
VOL3_TYPE="${24}"
echo "VOL3_TYPE=$VOL3_TYPE"
echo "------------------------------"

set -e

ROOT_PATH=$(pwd)

INFLUX_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_metrics" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
INFLUX_URL="http://$INFLUX_IP/$INFLUX_SUBPATH"
echo "Node $NODE_NUMBER_START: InfluxDB url is $INFLUX_URL"

LAST_NODE=$(($CLUSTER_SIZE + $NODE_NUMBER_START - 1))
echo "LAST_NODE is $LAST_NODE"

# deploy broker and loadgen servers

IFS=', ' read -r -a array <<< "$SUBNETS"

COUNTER=0
SN_COUNT="${#array[@]}"
for NODE in $(seq $NODE_NUMBER_START $LAST_NODE)
do
    TAG="benchmarking_rabbitmq${NODE}_${RUN_TAG}"
    RUNNING="$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=$TAG" "Name=instance-state-name,Values=running" --query "Reservations[*].Instances[*].InstanceId" --output=text)"
    if [ -z $RUNNING ]; then

        SN=$(echo "${array[$COUNTER]}")
        echo "Node $NODE: Creating instance for rabbitmq$NODE in subnet ${array[$COUNTER]}"

        if (( $NODE == $NODE_NUMBER_START )); then
            LG_INCLUDED="true"
        else 
            LG_INCLUDED="false"
        fi

        # if its a local storage instance then do not add extra ebs volume
        if [[ $INSTANCE == c5d* ]] || [[ $INSTANCE == i3* ]] || [[ $INSTANCE == z1d* ]]; then
            bash deploy-local-storage-instance.sh $AMI $CORE_COUNT $INSTANCE $KEY_PAIR $LG_INCLUDED $LG_INSTANCE $LG_SG $NODE $RUN_TAG $SG $SN "rabbitmq" $TENANCY $TPC
        else
            bash deploy-ebs-instance.sh $AMI $ARM_AMI $CORE_COUNT $INSTANCE $KEY_PAIR $LG_INCLUDED $LG_INSTANCE $LG_SG $NODE $RUN_TAG $SG $SN "rabbitmq" $TENANCY $TPC $VOL1_IOPS_PER_GB $VOL2_IOPS_PER_GB $VOL3_IOPS_PER_GB $VOL1_SIZE $VOL2_SIZE $VOL3_SIZE $VOL1_TYPE $VOL2_TYPE $VOL3_TYPE
        fi
    else
        echo "Node $NODE: Instance already exists, skipping EC2 instance creation"
    fi

    if (( $COUNTER >= $SN_COUNT - 1 )); then
        COUNTER=0
    else
        COUNTER=$(( COUNTER + 1 ))
    fi
done

# wait for servers to come online
for NODE in $(seq $NODE_NUMBER_START $LAST_NODE)
do
    TAG="benchmarking_rabbitmq${NODE}_${RUN_TAG}"
    RUNNING="$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=$TAG" "Name=instance-state-name,Values=running" --query "Reservations[*].Instances[*].InstanceId" --output=text)"
    while [ -z $RUNNING ]
    do
        echo "Node $NODE: Broker rabbitmq$NODE not ready yet, waiting 5 seconds"
        sleep 5
        RUNNING="$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=$TAG" "Name=instance-state-name,Values=running" --query "Reservations[*].Instances[*].InstanceId" --output=text)"
        echo $RUNNING
    done
done

echo "Node $NODE: Waiting 1 minute for rabbitmq EC2 instances to initialize"
sleep 60