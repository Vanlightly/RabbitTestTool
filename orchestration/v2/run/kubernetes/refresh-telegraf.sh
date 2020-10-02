#!/bin/bash

K_CONTEXT=$1
RABBITMQ_CLUSTER_NAME=$2
KUBERNETES_ENGINE=$3

cd ../../deploy/kubernetes

if [[ $KUBERNETES_ENGINE == "eks" ]]; then
  export AWS_PROFILE=benchmarking
fi

kubectl --context ${K_CONTEXT} apply -f manifests/telegraf/.variables/influx-secret.yaml

PODS=$(kubectl --context ${K_CONTEXT} get pods | grep rabbit | awk '{print $1}')
COUNTER=0
while IFS= read -r POD; do
    kubectl --context ${K_CONTEXT} delete deployment telegraf${COUNTER}

    BROKER_IP=$(kubectl --context ${K_CONTEXT} get pod ${POD} -o jsonpath="{.status.podIP}")
    BROKER_NAME="rabbit@${POD}.${RABBITMQ_CLUSTER_NAME}-rabbitmq-headless.default"
    BROKER_URL="\"http://${BROKER_IP}:15692/metrics\""

    CONFIG_MANIFEST_FILE="manifests/telegraf/telegraf-config-${RABBITMQ_CLUSTER_NAME}-generated.yaml"
    cp manifests/telegraf/telegraf-config-template.yaml $CONFIG_MANIFEST_FILE
    sed -i "s#RABBITMQ_SCRAPE_URLS#$BROKER_URL#g" $CONFIG_MANIFEST_FILE
    sed -i "s#BROKER_NAME#$BROKER_NAME#g" $CONFIG_MANIFEST_FILE
    sed -i "s#ORDINAL#$COUNTER#g" $CONFIG_MANIFEST_FILE

    DEPLOYMENT_MANIFEST_FILE="manifests/telegraf/telegraf-deployment-${RABBITMQ_CLUSTER_NAME}-generated.yaml"
    cp manifests/telegraf/telegraf-deployment-template.yaml $DEPLOYMENT_MANIFEST_FILE
    sed -i "s#ORDINAL#$COUNTER#g" $DEPLOYMENT_MANIFEST_FILE

    echo "Applying telegraf manifests for $POD $DEPLOYMENT_MANIFEST_FILE"
    kubectl --context ${K_CONTEXT} apply -f ./$CONFIG_MANIFEST_FILE
    kubectl --context ${K_CONTEXT} apply -f ./$DEPLOYMENT_MANIFEST_FILE

    COUNTER=$((COUNTER + 1))
done <<< "$PODS"