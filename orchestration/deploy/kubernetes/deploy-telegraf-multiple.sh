#!/bin/bash

kubectl apply -f manifests/telegraf/.variables/influx-secret.yaml

PODS=$(kubectl get pods | grep rabbit | awk '{print $1}')
COUNTER=0
while IFS= read -r POD; do
    BROKER_IP=$(kubectl get pod ${POD} -o jsonpath="{.status.podIP}")
    BROKER_NAME="rabbit@${POD}.rtt-rabbitmq-headless.default"
    BROKER_URL="\"http://${BROKER_IP}:15692/metrics\""

    cp manifests/telegraf/telegraf-config-template.yaml manifests/telegraf/telegraf-config-generated.yaml
    sed -i "s#RABBITMQ_SCRAPE_URLS#$BROKER_URL#g" manifests/telegraf/telegraf-config-generated.yaml
    sed -i "s#BROKER_NAME#$BROKER_NAME#g" manifests/telegraf/telegraf-config-generated.yaml
    sed -i "s#ORDINAL#$COUNTER#g" manifests/telegraf/telegraf-config-generated.yaml

    cp manifests/telegraf/telegraf-deployment-template.yaml manifests/telegraf/telegraf-deployment-generated.yaml
    sed -i "s#ORDINAL#$COUNTER#g" manifests/telegraf/telegraf-deployment-generated.yaml

    echo "Applying manifests for $POD"
    kubectl apply -f ./manifests/telegraf/telegraf-config-generated.yaml
    kubectl apply -f ./manifests/telegraf/telegraf-deployment-generated.yaml    

    COUNTER=$((COUNTER + 1))
done <<< "$PODS"




