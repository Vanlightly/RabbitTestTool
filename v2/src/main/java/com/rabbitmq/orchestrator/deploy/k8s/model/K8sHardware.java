package com.rabbitmq.orchestrator.deploy.k8s.model;

public class K8sHardware {
    K8sInstance instance;
    int instanceCount;
    K8sVolumeConfig volumeConfig;

    public K8sHardware(K8sInstance instance, int instanceCount, K8sVolumeConfig volumeConfig) {
        this.instance = instance;
        this.instanceCount = instanceCount;
        this.volumeConfig = volumeConfig;
    }

    public K8sInstance getInstance() {
        return instance;
    }

    public int getInstanceCount() {
        return instanceCount;
    }

    public K8sVolumeConfig getVolumeConfig() {
        return volumeConfig;
    }

    public int getCpuLimit() {
        return instance.getVcpuCount() - 1; // use all CPUs except for one
    }

    public int getMemoryMbLimit() {
        return instance.getMemoryGb()*800; // 80% of available
    }
}
