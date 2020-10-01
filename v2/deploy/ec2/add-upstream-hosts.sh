#!/bin/bash

echo "Adding upstream hosts to host file on downstream servers"

VARIABLES_FILE=$1
source $VARIABLES_FILE

set -e

ROOT_PATH=$(pwd)

# build the list of downstream hosts in the cluster
UPSTREAM_HOSTS="["
for NODE in $(seq $UPSTREAM_NODE_RANGE_START $UPSTREAM_NODE_RANGE_END)
do
    TAG="benchmarking_rabbitmq${NODE}_${RUN_TAG}"
    NODE_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=$TAG" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
    UPSTREAM_HOSTS="${UPSTREAM_HOSTS}{\"ip\":\"${NODE_IP}\",\"host\":\"rabbitmq${NODE}\"}"

    if [[ $NODE != $UPSTREAM_NODE_RANGE_END ]]; then
        UPSTREAM_HOSTS="${UPSTREAM_HOSTS},"
    fi
done
UPSTREAM_HOSTS="${UPSTREAM_HOSTS}]"

# echo "Downstream hosts are $DOWNSTREAM_HOSTS"
echo "Upstream hosts are $UPSTREAM_HOSTS"


cd $ROOT_PATH/rabbitmq

for NODE in $(seq $DOWNSTREAM_NODE_RANGE_START $DOWNSTREAM_NODE_RANGE_END)
do
    ansible-playbook add-upstream-hosts.yml --private-key=~/.ssh/$KEY_PAIR.pem --ssh-common-args='-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null' \
    --extra-vars "node=$NODE" \
    --extra-vars "run_tag=$RUN_TAG" \
    --extra-vars '{"upstream_hosts":'"${UPSTREAM_HOSTS}"'}'
done