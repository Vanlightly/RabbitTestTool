#!/bin/bash

VARIABLES_FILE=$1
source $VARIABLES_FILE

LG_INCLUDED="$2"
NODE_NUMBER="$3"
SN="$4"
TECHNOLOGY=$5

echo "Node $NODE_NUMBER: Deploying $INSTANCE EC2 instance without EBS volume (local storage only)"
TAG="benchmarking_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}"

# deploy broker instance
aws ec2 run-instances --profile benchmarking \
--image-id $BROKER_AMI \
--count 1 \
--instance-type $INSTANCE \
--key-name $KEY_PAIR \
--security-group-ids $SG \
--subnet-id $SN \
--placement Tenancy=$TENANCY \
--tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$TAG},{Key=inventorygroup,Value=$TAG}]" "ResourceType=volume,Tags=[{Key=Name,Value=$TAG},{Key=inventorygroup,Value=$TAG}]" \
--cpu-options "CoreCount=${CORE_COUNT},ThreadsPerCore=${TPC}"

if [[ ${LG_INCLUDED} == "false" ]];then
    echo "Node $NODE_NUMBER: Not deploying loadgen"
else 
    # nodes above 100 are federation downstreams and do not have a separate benchmarker
    if (( $NODE_NUMBER < 100 )); then
        sleep $((RANDOM % 30))

        LG_TAG="benchmarking_loadgen_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}"

        echo "Node $NODE_NUMBER: Deploying loadgen"
        aws ec2 run-instances --profile benchmarking \
        --image-id $LOADGEN_AMI \
        --count 1 \
        --instance-type ${LG_INSTANCE} \
        --key-name $KEY_PAIR \
        --security-group-ids $LG_SG \
        --subnet-id $SN \
        --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$LG_TAG},{Key=inventorygroup,Value=$LG_TAG}]"
    fi
fi
