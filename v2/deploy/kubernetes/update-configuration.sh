#!bin/bash

set -e

update_rabbitmq() {
  echo ""
  echo "Updating RabbitMQ cluster $KUBE_CLUSTER_NAME with new manifest:"

  cat $MANIFEST_FILE

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

update_rabbitmq
sleep 30
wait_for_cluster
sleep 10
echo "Update complete for $KUBE_CLUSTER_NAME!"