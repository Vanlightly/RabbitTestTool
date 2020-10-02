package com.rabbitmq.orchestrator.deploy.k8s.model;

public enum K8sVolumeType {
    SSD, // GKE
    GP2, // EKS
    IO1, // EKS
    ST1, // EKS
    SC1 // EKS
}
