#!/bin/bash

NETWORKING=$6
USE_NLB=$7

if [[ $NETWORKING == "host" ]]; then
    cp manifests/rabbitmq/cluster-template-host-networking.yaml manifests/rabbitmq/generated.yaml
else
    if [[ $USE_NLB == "true" ]]; then
        cp manifests/rabbitmq/cluster-template-loadbalancer.yaml manifests/rabbitmq/generated.yaml
    else
        cp manifests/rabbitmq/cluster-template.yaml manifests/rabbitmq/generated.yaml
    fi
fi

sed -i "s/CLUSTER_SIZE/${1}/g" manifests/rabbitmq/generated.yaml
sed -i "s/VOLUME_SIZE/${2}/g" manifests/rabbitmq/generated.yaml
sed -i "s/VOLUME_TYPE/${3}/g" manifests/rabbitmq/generated.yaml
sed -i "s/VCPUS/${4}/g" manifests/rabbitmq/generated.yaml
sed -i "s/MEMORY_MB/${5}/g" manifests/rabbitmq/generated.yaml

echo "Generated manifest:"
cat manifests/rabbitmq/generated.yaml

echo ""
echo "Deploying RabbitMQ cluster"
kubectl apply -f manifests/rabbitmq/generated.yaml