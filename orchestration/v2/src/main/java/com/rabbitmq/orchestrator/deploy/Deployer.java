package com.rabbitmq.orchestrator.deploy;

import com.rabbitmq.orchestrator.model.actions.BrokerAction;
import com.rabbitmq.orchestrator.run.RabbitMQConfiguration;

public interface Deployer {
    void deploySystem();
    void obtainSystemInfo();
    void applyNewConfiguration(RabbitMQConfiguration brokerConfiguration);
    void restartBrokers();
    void teardown();
    void retrieveLogs(String path);
}
