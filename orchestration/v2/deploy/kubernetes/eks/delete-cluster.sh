#!/bin/bash

KUBE_CLUSTER_NAME=$1

AWS_PROFILE=benchmarking eksctl delete cluster --name $KUBE_CLUSTER_NAME

# TODO delete EBS volumes
