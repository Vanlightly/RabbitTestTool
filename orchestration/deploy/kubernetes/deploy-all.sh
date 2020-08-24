#!bin/bash

set -e

while getopts ":i:b:t:s:c:m:n:k:l:v:" opt; do
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
    \?) echo "Invalid option -$OPTARG" >&2
    ;;
  esac
done

if [[ $KUBERNETES_ENGINE == "eks" ]]; then
  echo "Deploying k8s cluster to EKS"
  cd eks
  bash create-eks-cluster.sh $INSTANCE_TYPE $BROKERS $K_VERSION

  sleep 5
  echo "Creating storage classes"
  bash create-storage-classes.sh
  cd ..
elif [[ $KUBERNETES_ENGINE == "gke" ]]; then
  echo "Deploying k8s cluster to GKE"
  cd gke
  bash create-gke-cluster.sh $INSTANCE_TYPE $BROKERS $K_VERSION

  sleep 5
  echo "Creating storage classes"
  
  bash create-storage-classes.sh
  cd ..
fi

sleep 5
echo "Deploying RabbitMQ cluster operator"
bash deploy-operator.sh
sleep 5
echo "Deploying RabbitMQ cluster"
bash deploy-rabbitmq.sh $BROKERS $VOLUME_SIZE $VOLUME_TYPE $COMPUTE_LIMIT $MEMORY_LIMIT $NETWORKING $USE_NLB

STATUS=$(kubectl get statefulsets | grep rtt | awk '{ print $2 }')

while [[ $STATUS != "$BROKERS/$BROKERS" ]]
do
    echo "Cluster not ready yet: $STATUS"
    sleep 5
    STATUS=$(kubectl get statefulsets | grep rtt | awk '{ print $2 }')
done

echo "Cluster ready!"

sleep 5
echo "Deploying Telegraf"
bash deploy-telegraf-multiple.sh

echo "Deployment complete!"