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
QUORUM_COMMANDS_SOFT_LIMIT="$5"
echo "QUORUM_COMMANDS_SOFT_LIMIT=$QUORUM_COMMANDS_SOFT_LIMIT"
WAL_MAX_BATCH_SIZE="$6"
echo "WAL_MAX_BATCH_SIZE=$WAL_MAX_BATCH_SIZE"
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
    `if [ $QUORUM_COMMANDS_SOFT_LIMIT -ne 0 ]; then echo "--extra-vars quorum_commands_soft_limit=$QUORUM_COMMANDS_SOFT_LIMIT"; fi` \
    `if [ $WAL_MAX_BATCH_SIZE -ne 0 ]; then echo "--extra-vars wal_max_batch_size=$WAL_MAX_BATCH_SIZE"; fi`
done