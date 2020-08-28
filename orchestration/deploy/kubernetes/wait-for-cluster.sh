#!/bin/bash 

k_CONTEXT=$1
BROKERS=$2
RABBITMQ_CLUSTER_NAME=$3

STATUS=$(kubectl --context ${K_CONTEXT} get statefulsets | grep $RABBITMQ_CLUSTER_NAME | awk '{ print $2 }')

while [[ $STATUS != "$BROKERS/$BROKERS" ]]
do
    echo "Cluster not ready yet. Actual: $STATUS, expected: $BROKERS/$BROKERS"
    sleep 5
    STATUS=$(kubectl --context ${K_CONTEXT} get statefulsets | grep $RABBITMQ_CLUSTER_NAME | awk '{ print $2 }')
done