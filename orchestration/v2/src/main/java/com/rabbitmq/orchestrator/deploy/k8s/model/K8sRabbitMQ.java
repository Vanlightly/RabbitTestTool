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

    public String getRabbitmqImage() {
        return rabbitmqImage;
    }

    public boolean rebootBetweenBenchmarks() {
        return rebootBetweenBenchmarks;
    }
}
