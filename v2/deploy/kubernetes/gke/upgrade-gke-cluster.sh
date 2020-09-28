#!/bin/bash

VERSION="$1"
CLUSTER_NAME="$2"

echo "Upgrading k8s cluster master to version $VERSION"

gcloud container clusters upgrade $CLUSTER_NAME --master --cluster-version $VERSION

echo "Upgrading k8s cluster workers to version $VERSION"

gcloud container clusters upgrade $CLUSTER_NAME --node-pool=default-pool
