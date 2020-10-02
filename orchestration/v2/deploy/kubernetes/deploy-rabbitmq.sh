#!bin/bash

set -e

destroy_rabbitmq() {
  PODS=$(kubectl --context ${K_CONTEXT} get pods | grep rabbit | awk '{print $1}')
  DEPLOYMENTS="$(kubectl --context ${K_CONTEXT} get deployments)"
  
  echo "Current pods: $PODS"
  echo "Current deployments: $DEPLOYMENTS"

  if echo $PODS | grep -q "rabbit"; then
    echo "Deleting operator"
    kubectl --context ${K_CONTEXT} delete namespace rabbitmq-system
    echo "Deleting everything else"
    kubectl --context ${K_CONTEXT} delete all --all -n default

    PVCS=$(kubectl --context ${K_CONTEXT} get pvc | grep rabbit | awk '{ print $1 }')
    while IFS= read -r PVC; do
        echo "Deleting PVC $PVC"
        kubectl --context ${K_CONTEXT} delete pvc $PVC
    done <<< "$PVCS"
  fi

  # COUNTER=0
  # while IFS= read -r POD; do
  #     if [[ $POD == *rabbit* ]]; then
  #       if echo $DEPLOYMENTS | grep -q "telegraf"; then 
  #         kubectl --context ${K_CONTEXT} delete deployment telegraf${COUNTER}
  #         COUNTER=$((COUNTER + 1))
  #       fi
  #     fi
  # done <<< "$PODS"

  # if [[ $COUNTER != 0 ]]; then
    

  #   # STATEFULSET="$RABBITMQ_CLUSTER_NAME-rabbitmq-server"
  #   # echo "Deleting RabbitMQ statefulset $STATEFULSET"
  #   # kubectl --context ${K_CONTEXT} delete statefulset $STATEFULSET
  #   # sleep 30
  #   # echo "RabbitMQ statefulset $STATEFULSET deleted"
  # fi
}

deploy_operator() {
  echo "Deploying RabbitMQ cluster operator"
  kubectl --context ${K_CONTEXT} apply -f manifests/operator/namespace.yaml
  kubectl --context ${K_CONTEXT} apply -f manifests/operator/rbac.yaml
  kubectl --context ${K_CONTEXT} apply -f manifests/operator/crd.yaml
  kubectl --context ${K_CONTEXT} apply -f manifests/operator/deployment.yaml
}

deploy_rabbitmq() {
  echo ""
  echo "Deploying RabbitMQ cluster"
  kubectl --context ${K_CONTEXT} apply -f $MANIFEST_FILE
}

wait_for_cluster() {
  STATUS=$(kubectl --context ${K_CONTEXT} get statefulsets | grep $RABBITMQ_CLUSTER_NAME | awk '{ print $2 }')

  while [[ $STATUS != "$BROKERS/$BROKERS" ]]
  do
      echo "Cluster $KUBE_CLUSTER_NAME not ready yet. Actual: $STATUS, expected: $BROKERS/$BROKERS"
      sleep 5
      STATUS=$(kubectl --context ${K_CONTEXT} get statefulsets | grep $RABBITMQ_CLUSTER_NAME | awk '{ print $2 }')
  done
}

deploy_telegraf() {
  echo "Deploying Telegraf"
  kubectl --context ${K_CONTEXT} apply -f manifests/telegraf/.variables/influx-secret.yaml

  PODS=$(kubectl --context ${K_CONTEXT} get pods | grep rabbit | awk '{print $1}')
  COUNTER=0
  while IFS= read -r POD; do
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

      echo "Applying manifests for $POD" $DEPLOYMENT_MANIFEST_FILE
      kubectl --context ${K_CONTEXT} apply -f ./$CONFIG_MANIFEST_FILE
      kubectl --context ${K_CONTEXT} apply -f ./$DEPLOYMENT_MANIFEST_FILE

      COUNTER=$((COUNTER + 1))
  done <<< "$PODS"
}

while getopts ":n:N:b:m:k:c:" opt; do
  case $opt in
    n) KUBE_CLUSTER_NAME="$OPTARG"
    ;;
    N) RABBITMQ_CLUSTER_NAME="$OPTARG"
    ;;
    b) BROKERS="$OPTARG"
    ;;
    m) MANIFEST_FILE="$OPTARG"
    ;;
    k) KUBERNETES_ENGINE="$OPTARG"
    ;;
    c) K_CONTEXT="$OPTARG"
    ;;
    \?) echo "Invalid option -$OPTARG" >&2
    ;;
  esac
done


if [[ $KUBERNETES_ENGINE == "eks" ]]; then
  export AWS_PROFILE=benchmarking
fi

destroy_rabbitmq
sleep 5
deploy_operator
sleep 5
deploy_rabbitmq
wait_for_cluster
sleep 5
deploy_telegraf

echo "Deployment complete for $KUBE_CLUSTER_NAME"