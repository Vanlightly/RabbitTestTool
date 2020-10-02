#!/bin/bash

# $1 technology
# $2 node
# $3 run tag

BROKER_INSTANCE_ID=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_$1$2_$3" --query "Reservations[*].Instances[*].InstanceId" --output=text)
LOADGEN_INSTANCE_ID=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_loadgen_$1$2_$3" --query "Reservations[*].Instances[*].InstanceId" --output=text)
aws ec2 terminate-instances --profile benchmarking --instance-ids $BROKER_INSTANCE_ID $LOADGEN_INSTANCE_ID