#!/bin/bash

VARIABLES_FILE=$1
source $VARIABLES_FILE

LG_INCLUDED="$2"
NODE_NUMBER="$3"
SN="$4"
TECHNOLOGY=$5

VOL1_IOPS=$(($VOL1_SIZE * $VOL1_IOPS_PER_GB))
VOL2_IOPS=$(($VOL2_SIZE * $VOL1_IOPS_PER_GB))
VOL3_IOPS=$(($VOL3_SIZE * $VOL1_IOPS_PER_GB))

echo "Node $NODE_NUMBER: Deploying EBS backed $INSTANCE EC2 instance"
TAG=benchmarking_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}

# deploy broker instance
BLOCK_DEVICE1=""
BLOCK_DEVICE2=""
BLOCK_DEVICE3=""

if [[ $VOL1_TYPE == "io1" ]];then
    BLOCK_DEVICE1="DeviceName=/dev/sdb,Ebs={VolumeType=io1,Iops=$VOL1_IOPS,VolumeSize=$VOL1_SIZE,DeleteOnTermination=true}"
else 
    BLOCK_DEVICE1="DeviceName=/dev/sdb,Ebs={VolumeType=${VOL1_TYPE},VolumeSize=$VOL1_SIZE,DeleteOnTermination=true}"
fi

if (( $VOL2_SIZE > 0 ));then
    if [[ $VOL2_TYPE == "io1" ]];then
        BLOCK_DEVICE2="DeviceName=/dev/sdc,Ebs={VolumeType=io1,Iops=$VOL2_IOPS,VolumeSize=$VOL2_SIZE,DeleteOnTermination=true}"
    else
        BLOCK_DEVICE2="DeviceName=/dev/sdc,Ebs={VolumeType=${VOL2_TYPE},VolumeSize=$VOL2_SIZE,DeleteOnTermination=true}"
    fi
fi

if (( $VOL3_SIZE > 0 ));then
    if [[ $VOL3_TYPE == "io1" ]];then
        BLOCK_DEVICE3="DeviceName=/dev/sdd,Ebs={VolumeType=io1,Iops=$VOL3_IOPS,VolumeSize=$VOL3_SIZE,DeleteOnTermination=true}"
    else
        BLOCK_DEVICE3="DeviceName=/dev/sdd,Ebs={VolumeType=${VOL3_TYPE},VolumeSize=$VOL3_SIZE,DeleteOnTermination=true}"
    fi
fi

if [[ $INSTANCE == a1* ]] || [[ $INSTANCE == m6g* ]] || [[ $INSTANCE == c6g* ]]; then
    aws ec2 run-instances --profile benchmarking \
    --image-id "$BROKER_AMI" \
    --count 1 \
    --instance-type "$INSTANCE" \
    --key-name "$KEY_PAIR" \
    --security-group-ids "$SG" \
    --subnet-id "$SN" \
    --placement "Tenancy=$TENANCY" \
    --block-device-mappings $BLOCK_DEVICE1 $BLOCK_DEVICE2 $BLOCK_DEVICE3 \
    --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$TAG},{Key=inventorygroup,Value=$TAG}]" "ResourceType=volume,Tags=[{Key=Name,Value=$TAG},{Key=inventorygroup,Value=$TAG}]"
else
    aws ec2 run-instances --profile benchmarking \
    --image-id "$BROKER_AMI" \
    --count 1 \
    --instance-type "$INSTANCE" \
    --key-name "$KEY_PAIR" \
    --security-group-ids "$SG" \
    --subnet-id "$SN" \
    --placement "Tenancy=$TENANCY" \
    --block-device-mappings $BLOCK_DEVICE1 $BLOCK_DEVICE2 $BLOCK_DEVICE3 \
    --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$TAG},{Key=inventorygroup,Value=$TAG}]" "ResourceType=volume,Tags=[{Key=Name,Value=$TAG},{Key=inventorygroup,Value=$TAG}]" \
    --cpu-options "CoreCount=${CORE_COUNT},ThreadsPerCore=${TPC}"
fi

if [[ ${LG_INCLUDED} == "false" ]];then
    echo "Node $NODE_NUMBER: Not deploying loadgen"
else 
    # nodes above 100 are federation downstreams and do not have a separate benchmarker
    if (( $NODE_NUMBER < 100 )); then
        sleep $((RANDOM % 30))
        LG_TAG=benchmarking_loadgen_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}

        echo "Node $NODE_NUMBER: Deploying loadgen"
        aws ec2 run-instances --profile benchmarking \
        --image-id "$LOADGEN_AMI" \
        --count 1 \
        --instance-type "${LG_INSTANCE}" \
        --key-name "$KEY_PAIR" \
        --security-group-ids "${LG_SG}" \
        --subnet-id "$SN" \
        --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$LG_TAG},{Key=inventorygroup,Value=$LG_TAG}]" "ResourceType=volume,Tags=[{Key=Name,Value=$TAG},{Key=inventorygroup,Value=$TAG}]"
    fi
fi
