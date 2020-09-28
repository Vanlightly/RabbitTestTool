package com.rabbitmq.orchestrator.deploy.ec2.model;

public class EC2Instance {
    String ami;
    String instanceType;
    int coreCount;
    int threadsPerCore;
    int memoryGb;

    public EC2Instance(String ami, String instanceType, int coreCount, int threadsPerCore, int memoryGb) {
        this.ami = ami;
        this.instanceType = instanceType;
        this.coreCount = coreCount;
        this.threadsPerCore = threadsPerCore;
        this.memoryGb = memoryGb;
    }

    public String getAmi() {
        return ami;
    }

    public String getInstanceType() {
        return instanceType;
    }

    public int getCoreCount() {
        return coreCount;
    }

    public int getThreadsPerCore() {
        return threadsPerCore;
    }

    public int getMemoryGb() {
        return memoryGb;
    }


}
