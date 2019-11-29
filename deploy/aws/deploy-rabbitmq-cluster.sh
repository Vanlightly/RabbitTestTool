#!/bin/bash

echo "------------------------------"
echo "Deploying RabbitMQ cluster with args:"
AMI="$1"
echo "AMI=$AMI"
BROKER_VERSION="$2"
echo "BROKER_VERSION=$BROKER_VERSION"
CLUSTER_SIZE="$3"
echo "CLUSTER_SIZE=$CLUSTER_SIZE"
CORE_COUNT="$4"
echo "CORE_COUNT=$CORE_COUNT"
FS="$5"
echo "FS=$FS"
GENERIC_UNIX_URL="$6"
echo "GENERIC_UNIX_URL=$GENERIC_UNIX_URL"
INSTANCE="$7"
echo "INSTANCE=$INSTANCE"
KEY_PAIR="$8"
echo "KEY_PAIR=$KEY_PAIR"
LOG_LEVEL="$9"
echo "LOG_LEVEL=$LOG_LEVEL"
LG_INSTANCE="${10}"
echo "LG_INSTANCE=$LG_INSTANCE"
LG_SG="${11}"
echo "LG_SG=$LG_SG"
NODE_NUMBER_START="${12}"
echo "NODE_NUMBER_START=$NODE_NUMBER_START"
RUN_TAG="${13}"
echo "RUN_TAG=$RUN_TAG"
SG="${14}"
echo "SG=$SG"
SN="${15}"
echo "SN=$SN"
TENANCY="${16}"
echo "TENANCY=$TENANCY"
TPC="${17}"
echo "TPC=$TPC"
VARS_FILE="${18}"
echo "VARS_FILE=$VARS_FILE"
VOL_SIZE="${19}"
echo "VOL_SIZE=$VOL_SIZE"
VOL_TYPE="${20}"
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
        echo "Node $NODE: Creating rabbitmq server $NODE with version ${BROKER_VERSION}"

        if (( $NODE == $NODE_NUMBER_START )); then
            LG_INCLUDED="true"
        else 
            LG_INCLUDED="false"
        fi

        # if its a local storage instance then do not add extra ebs volume
        if [[ $INSTANCE == c5d* ]] || [[ $INSTANCE == i3* ]]; then
            bash deploy-local-storage-instance.sh $AMI $CORE_COUNT $INSTANCE $KEY_PAIR $LG_INSTANCE $LG_SG $NODE $RUN_TAG $SG $SN "rabbitmq" $TENANCY $TPC
        else
            bash deploy-ebs-instance.sh $AMI $CORE_COUNT $INSTANCE $KEY_PAIR $LG_INCLUDED $LG_INSTANCE $LG_SG $NODE $RUN_TAG $SG $SN "rabbitmq" $TENANCY $TPC $VOL_SIZE $VOL_TYPE
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

# build the list of hosts in the cluster
HOSTS="["
for NODE in $(seq $NODE_NUMBER_START $LAST_NODE)
do
    TAG="benchmarking_rabbitmq${NODE}_${RUN_TAG}"
    NODE_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=$TAG" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
    HOSTS="${HOSTS}{\"ip\":\"${NODE_IP}\",\"host\":\"rabbitmq${NODE}\"}"

    if [[ $NODE != $LAST_NODE ]]; then
        HOSTS="${HOSTS},"
    fi
done
HOSTS="${HOSTS}]"

echo "Hosts is $HOSTS"

if (( $VOL_SIZE > 1999 )); then 
    VOLUME_SIZE_LABEL=$(($VOL_SIZE/1000))T
else
    VOLUME_SIZE_LABEL="${VOL_SIZE}G"
fi
echo "VOLUME_SIZE_LABEL is $VOLUME_SIZE_LABEL"

# provision servers
for NODE in $(seq $NODE_NUMBER_START $LAST_NODE)
do
    echo "Node $NODE: Configuring rabbitmq EC2 instance $NODE"
    cd $ROOT_PATH/rabbitmq

    if [[ $NODE == $NODE_NUMBER_START ]]; then
        NODE_ROLE=master
    else
        NODE_ROLE=joinee
    fi

    echo "NODE_ROLE is $NODE_ROLE"

    ansible-playbook install-rabbitmq.yml --private-key=~/.ssh/$KEY_PAIR.pem --ssh-common-args='-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null' \
    --extra-vars "influx_url=$INFLUX_URL" \
    --extra-vars "rabbitmq_version=${BROKER_VERSION}-1" \
    --extra-vars "hostname=rabbitmq${NODE}" \
    --extra-vars "filesystem=${FS}" \
    --extra-vars "node=$NODE" \
    --extra-vars "volume_size=$VOL_SIZE" \
    --extra-vars "run_tag=${RUN_TAG}" \
    --extra-vars "volume_size_label=$VOLUME_SIZE_LABEL" \
    --extra-vars "generic_unix_url=$GENERIC_UNIX_URL" \
    --extra-vars "@${VARS_FILE}" \
    --extra-vars "node_role=$NODE_ROLE" \
    --extra-vars '{"rabbitmq_hosts":'"${HOSTS}"'}' \
    --extra-vars "rabbitmq_cluster_master=rabbitmq${NODE_NUMBER_START}" \
    --extra-vars "log_level=${LOG_LEVEL}" 
done

cd ..
bash deploy-benchmark.sh $KEY_PAIR $NODE_NUMBER_START "rabbitmq" $RUN_TAG