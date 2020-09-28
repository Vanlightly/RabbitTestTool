package com.rabbitmq.orchestrator.deploy.ec2.model;

import com.rabbitmq.orchestrator.InvalidInputException;
import com.rabbitmq.orchestrator.deploy.RabbitMQData;

import java.util.Arrays;
import java.util.List;

public class EC2VolumeConfig {
    String name;
    EC2VolumeType volumeType;
    int sizeGb;
    int iopsPerGb;
    String mountpoint;
    List<RabbitMQData> data;

    public static List<RabbitMQData> allData() {
        return Arrays.asList(RabbitMQData.MNESIA, RabbitMQData.LOGS, RabbitMQData.QUORUM, RabbitMQData.WAL);
    }

    public EC2VolumeConfig(String name, EC2VolumeType volumeType, int sizeGb, int iopsPerGb, String mountpoint, List<RabbitMQData> data) {
        this.name = name;
        this.volumeType = volumeType;
        this.sizeGb = sizeGb;
        this.iopsPerGb = iopsPerGb;
        this.mountpoint = mountpoint;
        this.data = data;

        if(this.sizeGb <= 0)
            throw new InvalidInputException("Volume size cannot be 0");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public EC2VolumeType getVolumeType() {
        return volumeType;
    }

    public void setVolumeType(EC2VolumeType volumeType) {
        this.volumeType = volumeType;
    }

    public int getSizeGb() {
        return sizeGb;
    }

    public void setSizeGb(int sizeGb) {
        this.sizeGb = sizeGb;
    }

    public int getIopsPerGb() {
        return iopsPerGb;
    }

    public void setIopsPerGb(int iopsPerGb) {
        this.iopsPerGb = iopsPerGb;
    }

    public String getMountpoint() {
        return mountpoint;
    }

    public void setMountpoint(String mountpoint) {
        this.mountpoint = mountpoint;
    }

    public List<RabbitMQData> getData() {
        return data;
    }

    public void setData(List<RabbitMQData> data) {
        this.data = data;
    }

    public boolean hasData(RabbitMQData dataType) {
        return data.contains(dataType);
    }
}
