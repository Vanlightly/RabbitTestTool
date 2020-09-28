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

    public void setRabbitmqVersion(String rabbitmqVersion) {
        this.rabbitmqVersion = rabbitmqVersion;
    }

    public String getRabbitmqUrl() {
        return rabbitmqUrl;
    }

    public void setRabbitmqUrl(String rabbitmqUrl) {
        this.rabbitmqUrl = rabbitmqUrl;
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

    public void setErlangUrl(String erlangUrl) {
        this.erlangUrl = erlangUrl;
    }

    public boolean isRebootBetweenBenchmarks() {
        return rebootBetweenBenchmarks;
    }

    public void setRebootBetweenBenchmarks(boolean rebootBetweenBenchmarks) {
        this.rebootBetweenBenchmarks = rebootBetweenBenchmarks;
    }
}
