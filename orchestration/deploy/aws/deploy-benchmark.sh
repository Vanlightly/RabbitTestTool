#!/bin/bash

echo "---------------------------"
echo "Deploy benchmark with args:"
KEY_PAIR="$1"
echo "KEY_PAIR=$KEY_PAIR"
NODE_NUMBER="$2"
echo "NODE_NUMBER=$NODE_NUMBER"
TECHNOLOGY="$3"
echo "TECHNOLOGY=$TECHNOLOGY"
RUN_TAG="$4"
echo "RUN_TAG=$RUN_TAG"
echo "---------------------------"

set -e

SUCCESS="false"

while [ "$SUCCESS" == "false" ]
do
    ansible-playbook install-benchmark.yml --private-key=~/.ssh/${KEY_PAIR}.pem --ssh-common-args='-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null' --extra-vars "node=$2" --extra-vars "technology=$3" --extra-vars "run_tag=$4"

    echo "Loadgen Node $NODE_NUMBER: Copying topologies and jar file"
    TAG="benchmarking_loadgen_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}"
    LOADGEN_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=$TAG" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
    echo "Loadgen Node $NODE_NUMBER: Checking server configured correctly. IP $LOADGEN_IP"
    
    RESULT=$(ssh -i "~/.ssh/${KEY_PAIR}.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" ubuntu@$LOADGEN_IP "ls | grep run_check")
    if [[ $RESULT == run_check ]]; then
        SUCCESS="true"
        echo "Loadgen Node $NODE_NUMBER: Server configured correctly."
    else
        echo "Loadgen Node $NODE_NUMBER: Benchmark ansible playbook skipped, retrying in 30 seconds"
        sleep 30
    fi
done

scp -i "~/.ssh/${KEY_PAIR}.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" "../../../benchmark/target/rabbittesttool-1.1-SNAPSHOT-jar-with-dependencies.jar" ubuntu@$LOADGEN_IP:.
scp -i "~/.ssh/${KEY_PAIR}.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -r "../../../benchmark/topologies" ubuntu@$LOADGEN_IP:.
scp -i "~/.ssh/${KEY_PAIR}.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" -r "../../../benchmark/policies" ubuntu@$LOADGEN_IP:.
echo "Loadgen Node $NODE_NUMBER: Copying complete"