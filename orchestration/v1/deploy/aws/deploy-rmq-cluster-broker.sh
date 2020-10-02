#!/bin/bash

BROKER_VERSION="$1"
echo "BROKER_VERSION=$BROKER_VERSION"
CORE_COUNT="$2"
echo "CORE_COUNT=$CORE_COUNT"
FS="$3"
echo "FS=$FS"
GENERIC_UNIX_URL="$4"
echo "GENERIC_UNIX_URL=$GENERIC_UNIX_URL"
INFLUX_SUBPATH="$5"
echo "INFLUX_SUBPATH=$INFLUX_SUBPATH"
INSTANCE="$6"
echo "INSTANCE=$INSTANCE"
KEY_PAIR="$7"
echo "KEY_PAIR=$KEY_PAIR"
LOG_LEVEL="$8"
echo "LOG_LEVEL=$LOG_LEVEL"
NODE_NUMBER="$9"
echo "NODE_NUMBER=$NODE_NUMBER"
NODE_RANGE_END="${10}"
echo "NODE_RANGE_END=$NODE_RANGE_END"
NODE_RANGE_START="${11}"
echo "NODE_RANGE_START=$NODE_RANGE_START"
NODE_ROLE=${12}
echo "NODE_ROLE=$NODE_ROLE"
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

set -e

ROOT_PATH=$(pwd)

INFLUX_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_metrics" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
INFLUX_URL="http://$INFLUX_IP/$INFLUX_SUBPATH"
echo "Node $NODE_NUMBER: InfluxDB url is $INFLUX_URL"

# build the list of hosts in the cluster
HOSTS="["
for NODE in $(seq $NODE_RANGE_START $NODE_RANGE_END)
do
    TAG="benchmarking_rabbitmq${NODE}_${RUN_TAG}"
    NODE_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=$TAG" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
    HOSTS="${HOSTS}{\"ip\":\"${NODE_IP}\",\"host\":\"rabbitmq${NODE}\"}"

    if [[ $NODE != $NODE_RANGE_END ]]; then
        HOSTS="${HOSTS},"
    fi
done
HOSTS="${HOSTS}]"

echo "Hosts is $HOSTS"

VOL1_SIZE_ROUNDED=$(echo ${VOL1_SIZE%.*})
VOL2_SIZE_ROUNDED=$(echo ${VOL2_SIZE%.*})
VOL3_SIZE_ROUNDED=$(echo ${VOL3_SIZE%.*})

if (( $VOL1_SIZE_ROUNDED > 1999 )); then 
    VOL1_TB=$(($VOL1_SIZE_ROUNDED/1000))
    VOL1_TB_LESS=$(($VOL1_TB-1))
    VOL1_SIZE_LABEL="(${VOL1_TB}T)|(${VOL1_TB_LESS}\..*T)"
else
    VOL1_SIZE_LABEL="${VOL1_SIZE}G"
fi
echo "VOL1_SIZE_LABEL is $VOL1_SIZE_LABEL"

if (( $VOL2_SIZE_ROUNDED > 1999 )); then 
    VOL2_TB=$(($VOL2_SIZE_ROUNDED/1000))
    VOL2_TB_LESS=$(($VOL2_TB-1))
    VOL2_SIZE_LABEL="(${VOL2_TB}T)|(${VOL2_TB_LESS}\..*T)"
else
    VOL2_SIZE_LABEL="${VOL2_SIZE}G"
fi
echo "VOL2_SIZE_LABEL is $VOL2_SIZE_LABEL"

if (( $VOL3_SIZE_ROUNDED > 1999 )); then 
    VOL3_TB=$(($VOL3_SIZE_ROUNDED/1000))
    VOL3_TB_LESS=$(($VOL3_TB-1))
    VOL3_SIZE_LABEL="(${VOL3_TB}T)|(${VOL3_TB_LESS}\..*T)"
else
    VOL3_SIZE_LABEL="${VOL3_SIZE}G"
fi
echo "VOL3_SIZE_LABEL is $VOL3_SIZE_LABEL"

# provision server
echo "Node $NODE_NUMBER: Configuring rabbitmq EC2 instance $NODE_NUMBER with role of $NODE_ROLE"
cd $ROOT_PATH/rabbitmq

ansible-playbook install-rabbitmq.yml --private-key=~/.ssh/$KEY_PAIR.pem --ssh-common-args='-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null' \
--extra-vars "influx_url=$INFLUX_URL" \
--extra-vars "rabbitmq_version=${BROKER_VERSION}-1" \
--extra-vars "hostname=rabbitmq${NODE_NUMBER}" \
--extra-vars "filesystem=${FS}" \
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
--extra-vars "node_role=$NODE_ROLE" \
--extra-vars '{"rabbitmq_hosts":'"${HOSTS}"'}' \
--extra-vars "rabbitmq_cluster_master=rabbitmq${NODE_RANGE_START}" \
--extra-vars "log_level=${LOG_LEVEL}"