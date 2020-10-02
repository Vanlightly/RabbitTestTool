#!/bin/bash

echo "------------------------------"
echo "Deploying Local storage backed instance with args:"
AMI="$1"
echo "AMI=$AMI"
CORE_COUNT="$2"
echo "CORE_COUNT=$CORE_COUNT"
INSTANCE="$3"
echo "INSTANCE=$INSTANCE"
KEY_PAIR="$4"
echo "KEY_PAIR=$KEY_PAIR"
LG_INCLUDED="$5"
echo "LG_INCLUDED=$LG_INCLUDED"
LG_INSTANCE="$6"
echo "LG_INSTANCE=$LG_INSTANCE"
LG_SG="$7"
echo "LG_SG=$LG_SG"
NODE_NUMBER="$8"
echo "NODE_NUMBER=$NODE_NUMBER"
RUN_TAG="$9"
echo "RUN_TAG=$RUN_TAG"
SG="${10}"
echo "SG=$SG"
SN="${11}"
echo "SN=$SN"
TECHNOLOGY="${12}"
echo "TECHNOLOGY=$TECHNOLOGY"
TENANCY="${13}"
echo "TENANCY=$TENANCY"
TPC="${14}"
echo "TPC=$TPC"

echo "Node $NODE_NUMBER: Deploying $INSTANCE EC2 instance without EBS volume (local storage only)"
TAG="benchmarking_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}"

# deploy broker instance
aws ec2 run-instances --profile benchmarking \
--image-id $AMI \
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
        --image-id $AMI \
        --count 1 \
        --instance-type ${LG_INSTANCE} \
        --key-name $KEY_PAIR \
        --security-group-ids $LG_SG \
        --subnet-id $SN \
        --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$LG_TAG},{Key=inventorygroup,Value=$LG_TAG}]"
    fi
fi
