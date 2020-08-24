#!/bin/bash

kubectl apply -f manifests/operator/namespace.yaml
kubectl apply -f manifests/operator/rbac.yaml
kubectl apply -f manifests/operator/crd.yaml
kubectl apply -f manifests/operator/deployment.yaml

