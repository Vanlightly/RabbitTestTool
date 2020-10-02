#!/bin/bash
K_CONTEXT=$1
kubectl --context ${K_CONTEXT} apply -f manifests/operator/namespace.yaml
kubectl --context ${K_CONTEXT} apply -f manifests/operator/rbac.yaml
kubectl --context ${K_CONTEXT} apply -f manifests/operator/crd.yaml
kubectl --context ${K_CONTEXT} apply -f manifests/operator/deployment.yaml

