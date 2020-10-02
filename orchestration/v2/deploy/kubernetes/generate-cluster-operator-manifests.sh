#!/bin/bash

kubectl kustomize ~/rabbitmq/cluster-operator/config/namespace/base/ > manifests/operator/namespace.yaml
kubectl kustomize ~/rabbitmq/cluster-operator/config/rbac/ >  manifests/operator/rbac.yaml
kubectl kustomize ~/rabbitmq/cluster-operator/config/crd/ > manifests/operator/crd.yaml
kubectl kustomize ~/rabbitmq/cluster-operator/config/installation > manifests/operator/operator.yaml