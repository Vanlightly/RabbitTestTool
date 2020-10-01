#!/bin/bash

while getopts ":c:d:" opt; do
  case $opt in
    c) K_CONTEXT="$OPTARG"
    ;;
    d) LOGS_DIR="$OPTARG"
    ;;
    \?) echo "Invalid option -$OPTARG" >&2
    ;;
  esac
done

mkdir -p $LOGS_DIR/$POD

PODS=$(kubectl --context ${K_CONTEXT} get pods | grep rabbit | awk '{print $1}')
BROKER_IPS=""
STREAM_PORTS=""
while IFS= read -r POD; do
    echo "Downloading logs of $POD"
    kubectl --context ${K_CONTEXT} $POD > $LOGS_DIR/$POD
    tar -zcvf $LOGS_DIR/$POD.tar.gz $LOGS_DIR/$POD
    echo "Compressed logs of $POD to $LOGS_DIR/$POD.tar.gz"
done <<< "$PODS"