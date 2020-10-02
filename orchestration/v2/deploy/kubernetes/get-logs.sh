#!/bin/bash

while getopts ":c:d:k:" opt; do
  case $opt in
    c) K_CONTEXT="$OPTARG"
    ;;
    d) LOGS_DIR="$OPTARG"
    ;;
    k) KUBERNETES_ENGINE="$OPTARG"
    ;;
    \?) echo "Invalid option -$OPTARG" >&2
    ;;
  esac
done

if [[ $KUBERNETES_ENGINE == "eks" ]]; then
  echo "Configuring credentials for EKS workload"
  export AWS_PROFILE=benchmarking
fi

mkdir -p $LOGS_DIR/$POD

PODS=$(kubectl --context ${K_CONTEXT} get pods | grep rabbit | awk '{print $1}')
BROKER_IPS=""
STREAM_PORTS=""
while IFS= read -r POD; do
    echo "Downloading logs of $POD"
    kubectl --context ${K_CONTEXT} logs $POD > $LOGS_DIR/$POD.log
    tar -zcvf $LOGS_DIR/$POD.tar.gz $LOGS_DIR/$POD.log
    echo "Compressed logs of $POD to $LOGS_DIR/$POD.tar.gz"
    rm $LOGS_DIR/$POD.log
done <<< "$PODS"