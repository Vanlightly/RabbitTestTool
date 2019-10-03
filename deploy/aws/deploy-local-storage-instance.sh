#!/bin/bash

AMI="$1"
CORE_COUNT="$2"
INSTANCE="$3"
KEY_PAIR="$4"
LG_INSTANCE="$5"
LG_SG="$6"
NODE_NUMBER="$7"
RUN_TAG="$9"
SG="${10}"
SN="${11}"
TECHNOLOGY="${12}"
TENANCY="${13}"
TPC="${14}"

sleep $((RANDOM % 30))
echo "Node $NODE_NUMBER: Deploying $INSTANCE EC2 instance without EBS volume (local storage only)"
TAG="benchmarking_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}"

# deploy broker instance
aws ec2 run-instances \
--image-id $AMI \
--count 1 \
--instance-type $INSTANCE \
--key-name $KEY_PAIR \
--security-group-ids $SG \
--subnet-id $SN \
--placement Tenancy=$TENANCY \
--tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$TAG},{Key=inventorygroup,Value=$TAG}]" "ResourceType=volume,Tags=[{Key=Name,Value=$TAG},{Key=inventorygroup,Value=$TAG}]" \
--cpu-options "CoreCount=${CORE_COUNT},ThreadsPerCore=${TPC}"

sleep $((RANDOM % 30))

LG_TAG="benchmarking_loadgen_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}"

echo "Node $NODE_NUMBER: Deploying loadgen"
aws ec2 run-instances \
--image-id $AMI \
--count 1 \
--instance-type ${INSTANCE} \
--key-name $KEY_PAIR \
--security-group-ids $SG \
--subnet-id $SN \
--tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$LG_TAG},{Key=inventorygroup,Value=$LG_TAG}]"

