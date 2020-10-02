#!/bin/bash

VERSION="$1"

echo "Upgrading k8s cluster master to version $VERSION"

gcloud container clusters upgrade benchmarking --master --cluster-version $VERSION

echo "Upgrading k8s cluster workers to version $VERSION"

gcloud container clusters upgrade benchmarking --node-pool=default-pool
