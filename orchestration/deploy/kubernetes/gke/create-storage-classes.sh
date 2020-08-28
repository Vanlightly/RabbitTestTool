#!/bin/bash
K_CONTEXT=$1
kubectl --context ${K_CONTEXT} create -f pd-ssd.yaml