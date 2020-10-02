#!/bin/bash

echo "------------------------------"
echo "Deploying RabbitMQ cluster"

VARIABLES_FILE=$1
source $VARIABLES_FILE

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
            bash deploy-local-storage-instance.sh $VARIABLES_FILE $LG_INCLUDED $NODE $SN "rabbitmq"
        else
            bash deploy-ebs-instance.sh $VARIABLES_FILE $LG_INCLUDED $NODE $SN "rabbitmq"
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