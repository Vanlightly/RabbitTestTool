#!/bin/bash

RABBITMQ_CLUSTER_NAME=$1

echo "Username: $(kubectl get secret $RABBITMQ_CLUSTER_NAME-rabbitmq-admin -o jsonpath="{.data.username}" | base64 --decode)"
echo "Password: $(kubectl get secret $RABBITMQ_CLUSTER_NAME-rabbitmq-admin -o jsonpath="{.data.password}" | base64 --decode)"