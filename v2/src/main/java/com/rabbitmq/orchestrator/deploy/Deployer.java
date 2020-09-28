package com.rabbitmq.orchestrator.deploy;

import com.rabbitmq.orchestrator.run.RabbitMQConfiguration;

public interface Deployer {
    void deploySystem();
    void updateBroker(RabbitMQConfiguration brokerConfiguration);
    void restartBrokers();
    void teardown();
    void retrieveLogs();
    void cancelOperation();
}
