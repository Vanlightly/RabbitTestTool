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

if [[ $NETWORKING == "host" ]]; then
    cp manifests/rabbitmq/cluster-template-host-networking.yaml manifests/rabbitmq/generated.yaml
else
    if [[ $USE_NLB == "true" ]]; then
        cp manifests/rabbitmq/cluster-template-loadbalancer.yaml manifests/rabbitmq/generated.yaml
    else
        cp manifests/rabbitmq/cluster-template.yaml manifests/rabbitmq/generated.yaml
    fi
fi

sed -i "s/CLUSTER_NAME/${RABBITMQ_CLUSTER_NAME}/g" manifests/rabbitmq/generated.yaml
sed -i "s/CLUSTER_SIZE/${BROKERS}/g" manifests/rabbitmq/generated.yaml
sed -i "s/VOLUME_SIZE/${VOLUME_SIZE}/g" manifests/rabbitmq/generated.yaml
sed -i "s/VOLUME_TYPE/${VOLUME_TYPE}/g" manifests/rabbitmq/generated.yaml
sed -i "s/VCPUS/${COMPUTE_LIMIT}/g" manifests/rabbitmq/generated.yaml
sed -i "s/MEMORY_MB/${MEMORY_LIMIT}/g" manifests/rabbitmq/generated.yaml

echo "Generated manifest:"
cat manifests/rabbitmq/generated.yaml

echo ""
echo "Deploying RabbitMQ cluster"
kubectl --context ${K_CONTEXT} apply -f manifests/rabbitmq/generated.yaml