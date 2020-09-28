#!/bin/bash

echo "---------------------------"
echo "Update configuration with args:"
KEY_PAIR="$1"
echo "KEY_PAIR=$KEY_PAIR"
NODE_RANGE_START="$2"
echo "NODE_RANGE_START=$NODE_RANGE_START"
NODE_RANGE_END="$3"
echo "NODE_RANGE_END=$NODE_RANGE_END"
RUN_TAG="$4"
echo "RUN_TAG=$RUN_TAG"
STANDARD_VARS=$5
ADVANCED_VARS_RABBIT=$5
ADVANCED_VARS_RA=$5
ADVANCED_VARS_ATEN=$5
echo "---------------------------"

set -e

ROOT_PATH=$(pwd)
cd $ROOT_PATH/rabbitmq

for NODE in $(seq $NODE_RANGE_START $NODE_RANGE_END)
do
    ansible-playbook update-rabbitmq-config.yml --private-key=~/.ssh/$KEY_PAIR.pem --ssh-common-args='-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null' \
    --tags configuration \
    --extra-vars "node=$NODE" \
    --extra-vars "run_tag=$RUN_TAG" \
    --extra-vars '{"standard_config":['"${STANDARD_VARS}"']}' \
    --extra-vars '{"advanced_config_rabbit":['"${ADVANCED_VARS_RABBIT}"']}' \
    --extra-vars '{"advanced_config_ra":['"${ADVANCED_VARS_RA}"']}' \
    --extra-vars '{"advanced_config_aten":['"${ADVANCED_VARS_ATEN}"']}'
done