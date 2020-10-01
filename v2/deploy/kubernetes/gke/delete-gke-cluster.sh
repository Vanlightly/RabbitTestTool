#!/bin/bash

KUBE_CLUSTER_NAME=$1

gcloud container clusters delete $KUBE_CLUSTER_NAME -q

# TODO delete persistent volumes