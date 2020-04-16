#!/bin/bash

echo "---------------------------"
echo "Deploy single broker with args:"
AMI="$1"
echo "AMI=$AMI"
BROKER_VERSION="$2"
echo "BROKER_VERSION=$BROKER_VERSION"
CORE_COUNT="$3"
echo "CORE_COUNT=$CORE_COUNT"
FS="$4"
echo "FS=$FS"
GENERIC_UNIX_URL="$5"
echo "GENERIC_UNIX_URL=$GENERIC_UNIX_URL"
INSTANCE="$6"
echo "INSTANCE=$INSTANCE"
KEY_PAIR="$7"
echo "KEY_PAIR=$KEY_PAIR"
LG_INSTANCE="$8"
echo "LG_INSTANCE=$LG_INSTANCE"
LG_SG="$9"
echo "LG_SG=$LG_SG"
LOG_LEVEL="${10}"
echo "LOG_LEVEL=$LOG_LEVEL"
NODE_NUMBER="${11}"
echo "NODE_NUMBER=$NODE_NUMBER"
RUN_TAG="${12}"
echo "RUN_TAG=$RUN_TAG"
SG="${13}"
echo "SG=$SG"
SN="${14}"
echo "SN=$SN"
TECHNOLOGY="${15}"
echo "TECHNOLOGY=$TECHNOLOGY"
TENANCY="${16}"
echo "TENANCY=$TENANCY"
TPC="${17}"
echo "TPC=$TPC"
VARS_FILE="${18}"
echo "VARS_FILE=$VARS_FILE"
VOLUME_DATA="${19}"
echo "VOLUME_DATA=$VOLUME_DATA"
VOLUME_LOGS="${20}"
echo "VOLUME_LOGS=$VOLUME_LOGS"
VOLUME_QUORUM="${21}"
echo "VOLUME_QUORUM=$VOLUME_QUORUM"
VOLUME_WAL="${22}"
echo "VOLUME_WAL=$VOLUME_WAL"
VOL1_SIZE="${23}"
echo "VOL1_SIZE=$VOL1_SIZE"
VOL1_MOUNTPOINT="${24}"
echo "VOL1_MOUNTPOINT=$VOL1_MOUNTPOINT"
VOL2_SIZE="${25}"
echo "VOL2_SIZE=$VOL2_SIZE"
VOL2_MOUNTPOINT="${26}"
echo "VOL2_MOUNTPOINT=$VOL2_MOUNTPOINT"
VOL3_SIZE="${27}"
echo "VOL3_SIZE=$VOL3_SIZE"
VOL3_MOUNTPOINT="${28}"
echo "VOL3_MOUNTPOINT=$VOL3_MOUNTPOINT"
VOL_TYPE="${29}"
echo "VOL_TYPE=$VOL_TYPE"
echo "---------------------------"

set -e

ROOT_PATH=$(pwd)

INFLUX_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_metrics" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
INFLUX_URL="http://$INFLUX_IP:8086"
echo "Node $NODE_NUMBER: InfluxDB url is $INFLUX_URL"

TAG=benchmarking_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}
RUNNING="$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=$TAG" "Name=instance-state-name,Values=running" --query "Reservations[*].Instances[*].InstanceId" --output=text)"
if [ -z $RUNNING ]; then
    echo "Node $NODE_NUMBER: Creating $TECHNOLOGY server $NODE_NUMBER with version $BROKER_VERSION"

    # if its a local storage instance then do not add extra ebs volume
    if [[ $INSTANCE == c5d* ]] || [[ $INSTANCE == i3* ]]; then
        bash deploy-local-storage-instance.sh $AMI $CORE_COUNT $INSTANCE $KEY_PAIR $LG_INSTANCE $LG_SG $NODE_NUMBER $RUN_TAG $SG $SN "rabbitmq" $TENANCY $TPC
    else
        bash deploy-ebs-instance.sh $AMI $CORE_COUNT $INSTANCE $KEY_PAIR "true" $LG_INSTANCE $LG_SG $NODE_NUMBER $RUN_TAG $SG $SN $TECHNOLOGY $TENANCY $TPC $VOL1_SIZE $VOL2_SIZE $VOL3_SIZE $VOL_TYPE
    fi
else
    echo "Node $NODE_NUMBER: Instance already exists, skipping EC2 instance creation"
fi

while [ -z $RUNNING ]
do
    echo "Node $NODE_NUMBER: Broker ${TECHNOLOGY}${NODE_NUMBER} not ready yet, waiting 5 seconds"
    sleep 5
    RUNNING="$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=$TAG" "Name=instance-state-name,Values=running" --query "Reservations[*].Instances[*].InstanceId" --output=text)"
    echo $RUNNING
done

echo "Node $NODE_NUMBER: Waiting 1 minute for $TECHNOLOGY EC2 instance $NODE_NUMBER to initialize"
sleep 60

echo "Node $NODE_NUMBER: Configuring $TECHNOLOGY EC2 instance $NODE_NUMBER"
cd $ROOT_PATH/$TECHNOLOGY

if (( $VOL1_SIZE > 1999 )); then 
    VOL1_SIZE_LABEL=$(($VOL1_SIZE/1000))T
else
    VOL1_SIZE_LABEL="${VOL1_SIZE}G"
fi
echo "VOL1_SIZE_LABEL is $VOL1_SIZE_LABEL"

if (( $VOL2_SIZE > 1999 )); then 
    VOL2_SIZE_LABEL=$(($VOL2_SIZE/1000))T
else
    VOL2_SIZE_LABEL="${VOL2_SIZE}G"
fi
echo "VOL2_SIZE_LABEL is $VOL2_SIZE_LABEL"

if (( $VOL3_SIZE > 1999 )); then 
    VOL3_SIZE_LABEL=$(($VOL3_SIZE/1000))T
else
    VOL3_SIZE_LABEL="${VOL3_SIZE}G"
fi
echo "VOL3_SIZE_LABEL is $VOL3_SIZE_LABEL"

SUCCESS="false"

while [ "$SUCCESS" == "false" ]
do
    ansible-playbook install-${TECHNOLOGY}.yml --private-key=~/.ssh/${KEY_PAIR}.pem --ssh-common-args='-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null' \
    --extra-vars "influx_url=$INFLUX_URL" \
    --extra-vars "${TECHNOLOGY}_version=${BROKER_VERSION}-1" \
    --extra-vars "hostname=$TECHNOLOGY$NODE_NUMBER" \
    --extra-vars "filesystem=$FS" \
    --extra-vars "node=$NODE_NUMBER" \
    --extra-vars "run_tag=$RUN_TAG" \
    --extra-vars "volume1=$VOL1_SIZE" \
    --extra-vars "volume1_size_label=$VOL1_SIZE_LABEL" \
    --extra-vars "volume1_mountpoint=$VOL1_MOUNTPOINT" \
    --extra-vars "volume2=$VOL2_SIZE" \
    --extra-vars "volume2_size_label=$VOL2_SIZE_LABEL" \
    --extra-vars "volume2_mountpoint=$VOL2_MOUNTPOINT" \
    --extra-vars "volume3=$VOL3_SIZE" \
    --extra-vars "volume3_size_label=$VOL3_SIZE_LABEL" \
    --extra-vars "volume3_mountpoint=$VOL3_MOUNTPOINT" \
    --extra-vars "data_volume=$VOLUME_DATA" \
    --extra-vars "logs_volume=$VOLUME_LOGS" \
    --extra-vars "quorum_volume=$VOLUME_QUORUM" \
    --extra-vars "wal_volume=$VOLUME_WAL" \
    --extra-vars "generic_unix_url=$GENERIC_UNIX_URL" \
    --extra-vars "@${VARS_FILE}" \
    --extra-vars "log_level=${LOG_LEVEL}"
       
    
    BROKER_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=$TAG" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
    echo "Node $NODE_NUMBER: Checking server configured correctly. IP $BROKER_IP"
    RESULT=$(ssh -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" ubuntu@$BROKER_IP "ls | grep run_check")
    if [[ $RESULT == run_check ]]; then
        SUCCESS="true"
        echo "Node $NODE_NUMBER: Server configured correctly."
    else
        echo "Node $NODE_NUMBER: Broker ansible playbook skipped, retrying in 30 seconds"
        sleep 30
    fi
done

# nodes above 100 are federation downstreams and do not have a separate benchmarker
if (( $NODE_NUMBER < 100 )); then
    cd ..
    bash deploy-benchmark.sh $KEY_PAIR $NODE_NUMBER $TECHNOLOGY $RUN_TAG
fi