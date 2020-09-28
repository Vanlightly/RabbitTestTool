#!/bin/bash

VARIABLES_FILE=$1
source $VARIABLES_FILE

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
--extra-vars "@${SECRET_VARS_FILE}" \
--extra-vars "influx_url=$INFLUX_URL" \
--extra-vars "generic_unix_url=$GENERIC_UNIX_URL" \
--extra-vars "erlang_deb_file_url=$ERLANG_DEB_FILE_URL" \
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
--extra-vars "location_data=$VOLUME_DATA" \
--extra-vars "location_logs=$VOLUME_LOGS" \
--extra-vars "location_quorum=$VOLUME_QUORUM" \
--extra-vars "location_wal=$VOLUME_WAL" \
--extra-vars "node_role=$NODE_ROLE" \
--extra-vars '{"rabbitmq_hosts":'"${HOSTS}"'}' \
--extra-vars "rabbitmq_cluster_master=rabbitmq${NODE_RANGE_START}" \
--extra-vers "additional_erl_args=$ADDITIONAL_ERL_ARGS" \
--extra-vars '{"standard_config":['"${STANDARD_VARS}"']}' \
--extra-vars '{"advanced_config_rabbit":['"${ADVANCED_VARS_RABBIT}"']}' \
--extra-vars '{"advanced_config_ra":['"${ADVANCED_VARS_RA}"']}' \
--extra-vars '{"advanced_config_aten":['"${ADVANCED_VARS_ATEN}"']}' \
--extra-vars '{"env_config":['"${ENV_VARS}"']}' \
--extra-vars '{"rabbitmq_plugins":['"${RABBITMQ_PLUGINS}"']}' 