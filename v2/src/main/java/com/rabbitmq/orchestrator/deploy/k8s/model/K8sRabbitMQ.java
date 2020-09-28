package com.rabbitmq.orchestrator.deploy.k8s.model;

public class K8sRabbitMQ {
    String rabbitmqVersion;
    String rabbitmqImage;
    boolean rebootBetweenBenchmarks;

    public K8sRabbitMQ(String rabbitmqVersion, String rabbitmqImage, boolean rebootBetweenBenchmarks) {
        this.rabbitmqVersion = rabbitmqVersion;
        this.rabbitmqImage = rabbitmqImage;
        this.rebootBetweenBenchmarks = rebootBetweenBenchmarks;
    }

    public String getRabbitmqVersion() {
        return rabbitmqVersion;
    }

    public void setRabbitmqVersion(String rabbitmqVersion) {
        this.rabbitmqVersion = rabbitmqVersion;
    }

    public String getRabbitmqImage() {
        return rabbitmqImage;
    }

    public void setRabbitmqImage(String rabbitmqImage) {
        this.rabbitmqImage = rabbitmqImage;
    }

    public boolean isRebootBetweenBenchmarks() {
        return rebootBetweenBenchmarks;
    }

    public void setRebootBetweenBenchmarks(boolean rebootBetweenBenchmarks) {
        this.rebootBetweenBenchmarks = rebootBetweenBenchmarks;
    }
}
