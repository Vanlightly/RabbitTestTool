#!/bin/bash

K_CONTEXT=$1
BROKERS=$2
RABBITMQ_CLUSTER_NAME=$3
VOLUME_SIZE=$4
VOLUME_TYPE=$5
COMPUTE_LIMIT=$6 
MEMORY_LIMIT=$7
NETWORKING=$8
USE_NLB=$9

MANIFEST_FILE="manifests/rabbitmq/${RABBITMQ_CLUSTER_NAME}-generated.yaml"

if [[ $NETWORKING == "host" ]]; then
    cp manifests/rabbitmq/cluster-template-host-networking.yaml $MANIFEST_FILE
else
    if [[ $USE_NLB == "true" ]]; then
        cp manifests/rabbitmq/cluster-template-loadbalancer.yaml $MANIFEST_FILE
    else
        cp manifests/rabbitmq/cluster-template.yaml $MANIFEST_FILE
    fi
fi

sed -i "s/CLUSTER_NAME/${RABBITMQ_CLUSTER_NAME}/g" $MANIFEST_FILE
sed -i "s/CLUSTER_SIZE/${BROKERS}/g" $MANIFEST_FILE
sed -i "s/VOLUME_SIZE/${VOLUME_SIZE}/g" $MANIFEST_FILE
sed -i "s/VOLUME_TYPE/${VOLUME_TYPE}/g" $MANIFEST_FILE
sed -i "s/VCPUS/${COMPUTE_LIMIT}/g" $MANIFEST_FILE
sed -i "s/MEMORY_MB/${MEMORY_LIMIT}/g" $MANIFEST_FILE

echo "Generated manifest:"
cat $MANIFEST_FILE

echo ""
echo "Deploying RabbitMQ cluster"
kubectl --context ${K_CONTEXT} apply -f $MANIFEST_FILE