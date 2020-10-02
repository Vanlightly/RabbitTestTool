package com.rabbitmq.orchestrator.deploy.ec2.model;

import com.rabbitmq.orchestrator.deploy.RabbitMQData;

import java.util.List;

public class EC2Hardware {
    EC2Instance loadGenInstance;
    EC2Instance rabbitMQInstance;
    List<EC2VolumeConfig> volumeConfigs;
    String tenancy;
    int instanceCount;

    public EC2Hardware(EC2Instance loadGenInstance,
                       EC2Instance rabbitMQInstance,
                       List<EC2VolumeConfig> volumeConfigs,
                       String tenancy,
                       int instanceCount) {
        this.loadGenInstance = loadGenInstance;
        this.rabbitMQInstance = rabbitMQInstance;
        this.volumeConfigs = volumeConfigs;
        this.tenancy = tenancy;
        this.instanceCount = instanceCount;
    }

    public EC2Instance getLoadGenInstance() {
        return loadGenInstance;
    }

    public void setLoadGenInstance(EC2Instance loadGenInstance) {
        this.loadGenInstance = loadGenInstance;
    }

    public EC2Instance getRabbitMQInstance() {
        return rabbitMQInstance;
    }

    public void setRabbitMQInstance(EC2Instance rabbitMQInstance) {
        this.rabbitMQInstance = rabbitMQInstance;
    }

    public List<EC2VolumeConfig> getVolumeConfigs() {
        return volumeConfigs;
    }

    public void setVolumeConfigs(List<EC2VolumeConfig> volumeConfigs) {
        this.volumeConfigs = volumeConfigs;
    }

    public int getInstanceCount() {
        return instanceCount;
    }

    public void setInstanceCount(int instanceCount) {
        this.instanceCount = instanceCount;
    }

    public String getTenancy() {
        return tenancy;
    }

    public void setTenancy(String tenancy) {
        this.tenancy = tenancy;
    }

    public EC2VolumeConfig getVolumeFor(RabbitMQData dataType) {
        for(EC2VolumeConfig volumeConfig : volumeConfigs) {
            if(volumeConfig.hasData(dataType))
                return volumeConfig;
        }

        return volumeConfigs.get(0);
    }
}
