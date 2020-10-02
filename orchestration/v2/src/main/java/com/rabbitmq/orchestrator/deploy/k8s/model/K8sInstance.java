package com.rabbitmq.orchestrator.deploy.k8s.model;

public class K8sInstance {
    final String instanceType;
    final int vcpuCount;
    final int memoryGb;

    public K8sInstance(String instanceType, int vcpuCount, int memoryGb) {
        this.instanceType = instanceType;
        this.vcpuCount = vcpuCount;
        this.memoryGb = memoryGb;
    }

    public String getInstanceType() {
        return instanceType;
    }

    public int getVcpuCount() {
        return vcpuCount;
    }

    public int getMemoryGb() {
        return memoryGb;
    }
}
