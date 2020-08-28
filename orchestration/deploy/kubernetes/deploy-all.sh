#!bin/bash

set -e

while getopts ":i:b:t:s:c:m:n:k:l:v:u:" opt; do
  case $opt in
    i) INSTANCE_TYPE="$OPTARG"
    ;;
    b) BROKERS="$OPTARG"
    ;;
    t) VOLUME_TYPE="$OPTARG"
    ;;
    s) VOLUME_SIZE="$OPTARG"
    ;;
    c) COMPUTE_LIMIT="$OPTARG"
    ;;
    m) MEMORY_LIMIT="$OPTARG"
    ;;
    n) NETWORKING="$OPTARG"
    ;;
    k) KUBERNETES_ENGINE="$OPTARG"
    ;;
    l) USE_NLB="$OPTARG"
    ;;
    v) K_VERSION="$OPTARG"
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

  echo "Deploying k8s cluster to EKS"
  cd eks
  bash create-eks-cluster.sh $INSTANCE_TYPE $BROKERS $K_VERSION

  sleep 5
  echo "Creating storage classes"
  bash create-storage-classes.sh $K_CONTEXT
  cd ..
elif [[ $KUBERNETES_ENGINE == "gke" ]]; then
  K_CONTEXT=gke_${K_USER_OR_PROJECT}_europe-west4-a_benchmarking-gke
  echo "Deploying k8s cluster to GKE"
  cd gke
  bash create-gke-cluster.sh $INSTANCE_TYPE $BROKERS $K_VERSION

  sleep 5
  echo "Creating storage classes"
  
  bash create-storage-classes.sh $K_CONTEXT
  cd ..
fi

sleep 5
echo "Deploying RabbitMQ cluster operator"
bash deploy-operator.sh $K_CONTEXT
sleep 5
echo "Deploying RabbitMQ cluster"
bash deploy-rabbitmq.sh $K_CONTEXT $BROKERS $RABBITMQ_CLUSTER_NAME $VOLUME_SIZE $VOLUME_TYPE $COMPUTE_LIMIT $MEMORY_LIMIT $NETWORKING $USE_NLB

bash wait-for-cluster.sh $K_CONTEXT $BROKERS $RABBITMQ_CLUSTER_NAME

sleep 5
echo "Deploying Telegraf"
bash deploy-telegraf-multiple.sh $K_CONTEXT $RABBITMQ_CLUSTER_NAME

echo "Deployment complete!"