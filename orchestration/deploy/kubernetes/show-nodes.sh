#!/bin/bash

PODS=$(kubectl get pods | awk '{ print $1 }' | grep -v NAME)

while IFS= read -r POD; do
    HOST_IP=$(kubectl get pod $POD -o jsonpath="{.status.hostIP}")
    echo "$POD on $HOST_IP"
done <<< "$PODS"