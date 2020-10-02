#!/bin/bash

K_CLUSTER_SIZE=$((${2} + 1))

echo "Deploying k8s cluster of $K_CLUSTER_SIZE nodes of instance type: $1"

eksctl create cluster \
--name benchmarking-eks \
--version 1.17 \
--region eu-west-1 \
--nodegroup-name standard-workers \
--node-type $1 \
--nodes $K_CLUSTER_SIZE \
--nodes-min 1 \
--nodes-max $K_CLUSTER_SIZE \
--ssh-access \
--ssh-public-key ~/.ssh/benchmarking.pub \
--managed