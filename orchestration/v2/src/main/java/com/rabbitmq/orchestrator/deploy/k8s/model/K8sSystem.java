package com.rabbitmq.orchestrator.deploy.k8s.model;

import com.rabbitmq.orchestrator.InvalidInputException;
import com.rabbitmq.orchestrator.deploy.BaseSystem;
import com.rabbitmq.orchestrator.model.hosts.Host;
import com.rabbitmq.orchestrator.run.RabbitMQConfiguration;

public class K8sSystem extends BaseSystem {
    final K8sHardware hardware;
    final K8sRabbitMQ rabbitmq;
    final RabbitMQConfiguration rabbitmqConfig;

    public K8sSystem(String name,
                     Host host,
                     K8sEngine k8sEngine,
                     K8sHardware hardware,
                     K8sRabbitMQ rabbitmq,
                     RabbitMQConfiguration rabbitmqConfig) {
        super(name, host, k8sEngine);
        if(host != Host.K8S)
            throw new InvalidInputException("Only 'K8s' is a supported host for Kubernetes");

        this.hardware = hardware;
        this.rabbitmq = rabbitmq;
        this.rabbitmqConfig = rabbitmqConfig;
    }

    public String getRabbitClusterName() {
        return "rmq-" + super.getK8sEngineStr() + "-" + super.getName();
    }

    public K8sHardware getHardware() {
        return hardware;
    }

    public K8sRabbitMQ getRabbitmq() {
        return rabbitmq;
    }

    public RabbitMQConfiguration getRabbitmqConfig() {
        return rabbitmqConfig;
    }
}
