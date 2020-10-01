#!/bin/bash

while getopts ":N:b:c:" opt; do
  case $opt in
    N) RABBITMQ_CLUSTER_NAME="$OPTARG"
    ;;
    b) BROKERS="$OPTARG"
    ;;
    c) K_CONTEXT="$OPTARG"
    ;;
    \?) echo "Invalid option -$OPTARG" >&2
    ;;
  esac
done

STATEFULSET="$RABBITMQ_CLUSTER_NAME-rabbitmq-server"
echo "Restarting statefulset $STATEFULSET"

kubectl --context ${K_CONTEXT} rollout restart statefulset $STATEFULSET

sleep 30 

STATUS=$(kubectl --context ${K_CONTEXT} get statefulsets | grep $RABBITMQ_CLUSTER_NAME | awk '{ print $2 }')

while [[ $STATUS != "$BROKERS/$BROKERS" ]]
do
    echo "Cluster $RABBITMQ_CLUSTER_NAME not ready yet. Actual: $STATUS, expected: $BROKERS/$BROKERS"
    sleep 5
    STATUS=$(kubectl --context ${K_CONTEXT} get statefulsets | grep $RABBITMQ_CLUSTER_NAME | awk '{ print $2 }')
done

sleep 10