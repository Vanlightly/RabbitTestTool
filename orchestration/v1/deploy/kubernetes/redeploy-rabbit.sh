#!bin/bash

set -e

while getopts ":b:k:u:" opt; do
  case $opt in
    b) BROKERS="$OPTARG"
    ;;
    k) KUBERNETES_ENGINE="$OPTARG"
    ;;
    u) K_USER_OR_PROJECT="$OPTARG"
    ;;
    \?) echo "Invalid option -$OPTARG" >&2
    ;;
  esac
done

RABBITMQ_CLUSTER_NAME="rmq-$KUBERNETES_ENGINE"

if [[ $KUBERNETES_ENGINE == "eks" ]]; then
  K_CONTEXT=${K_USER_OR_PROJECT}@benchmarking-eks.eu-west-1.eksctl.io
elif [[ $KUBERNETES_ENGINE == "gke" ]]; then
  K_CONTEXT=gke_${K_USER_OR_PROJECT}_europe-west4-a_benchmarking-gke
fi

echo "Deleting everything"
kubectl --context ${K_CONTEXT} delete --all namespaces
sleep 60

echo "Deploying RabbitMQ cluster operator"
bash deploy-operator.sh $K_CONTEXT
sleep 5
echo "Deploying RabbitMQ cluster"
MANIFEST_FILE="manifests/rabbitmq/${RABBITMQ_CLUSTER_NAME}-generated.yaml"
kubectl --context ${K_CONTEXT} apply -f $MANIFEST_FILE

bash wait-for-cluster.sh $K_CONTEXT $BROKERS $RABBITMQ_CLUSTER_NAME

sleep 5
echo "Deploying Telegraf"
bash deploy-telegraf-multiple.sh $K_CONTEXT $RABBITMQ_CLUSTER_NAME

echo "Deployment complete!"