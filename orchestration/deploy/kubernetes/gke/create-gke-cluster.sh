#!/bin/bash

K_CLUSTER_SIZE=$((${2} + 1))
VERSION="${3:-1.16.13-gke.1}"

echo "Deploying k8s cluster of 6 nodes of instance type: $1"

# gcloud container clusters create benchmarking \
#  --num-nodes=$K_CLUSTER_SIZE \
#  --region=europe-west1 \
#  --machine-type=$1

gcloud container clusters create benchmarking \
 --cluster-version 1.16.13-gke.1 \
 --num-nodes 2 \
 --zone europe-west4-a \
 --node-locations europe-west4-a,europe-west4-b,europe-west4-c \
 --machine-type $1


gcloud container clusters get-credentials benchmarking