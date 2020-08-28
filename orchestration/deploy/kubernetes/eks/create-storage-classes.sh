#!/bin/bash

K_CONTEXT=$1

kubectl --context ${K_CONTEXT} create -f io1.yaml
kubectl --context ${K_CONTEXT} create -f st1.yaml