package com.rabbitmq.orchestrator.deploy.ec2.model;

public class EC2RabbitMQ {
    String rabbitmqVersion;
    String rabbitmqUrl;
    String erlangVersion;
    String erlangUrl;
    boolean rebootBetweenBenchmarks;

    public EC2RabbitMQ(String rabbitmqVersion, String rabbitmqUrl, String erlangVersion, String erlangUrl, boolean rebootBetweenBenchmarks) {
        this.rabbitmqVersion = rabbitmqVersion;
        this.rabbitmqUrl = rabbitmqUrl;
        this.erlangVersion = erlangVersion;
        this.erlangUrl = erlangUrl;
        this.rebootBetweenBenchmarks = rebootBetweenBenchmarks;
    }

    public String getRabbitmqVersion() {
        return rabbitmqVersion;
    }

    public String getRabbitmqUrl() {
        return rabbitmqUrl;
    }

    public String getErlangVersion() {
        return erlangVersion;
    }

    public void setErlangVersion(String erlangVersion) {
        this.erlangVersion = erlangVersion;
    }

    public String getErlangUrl() {
        return erlangUrl;
    }

    public boolean rebootBetweenBenchmarks() {
        return rebootBetweenBenchmarks;
    }
}
