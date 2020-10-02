package com.rabbitmq.orchestrator.deploy.k8s.model;

import com.rabbitmq.orchestrator.InvalidInputException;

public class K8sVolumeConfig {
    String name;
    K8sVolumeType volumeType;
    int sizeGb;

    public K8sVolumeConfig(String name, K8sVolumeType volumeType, int sizeGb) {
        this.name = name;
        this.volumeType = volumeType;
        this.sizeGb = sizeGb;

        if(this.sizeGb <= 0)
            throw new InvalidInputException("Volume size cannot be 0");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public K8sVolumeType getVolumeType() {
        return volumeType;
    }

    public void setVolumeType(K8sVolumeType volumeType) {
        this.volumeType = volumeType;
    }

    public int getSizeGb() {
        return sizeGb;
    }

    public void setSizeGb(int sizeGb) {
        this.sizeGb = sizeGb;
    }
}
