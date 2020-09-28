#!/bin/bash

KUBE_CLUSTER_NAME=$1

gcloud container clusters delete $KUBE_CLUSTER_NAME

# TODO delete persistent volumes