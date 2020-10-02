package com.rabbitmq.orchestrator.deploy;

import com.rabbitmq.orchestrator.model.actions.BrokerAction;
import com.rabbitmq.orchestrator.run.BrokerActioner;
import com.rabbitmq.orchestrator.run.Runner;

public class Deployment {
    Deployer deployer;
    Runner runner;
    BaseSystem baseSystem;
    BrokerActioner brokerActioner;

    public Deployment(Deployer deployer,
                      Runner runner,
                      BrokerActioner brokerActioner,
                      BaseSystem baseSystem) {
        this.deployer = deployer;
        this.runner = runner;
        this.brokerActioner = brokerActioner;
        this.baseSystem = baseSystem;
    }

    public Deployer getDeployer() {
        return deployer;
    }

    public Runner getRunner() {
        return runner;
    }

    public BrokerActioner getBrokerActioner() {
        return brokerActioner;
    }

    public BaseSystem getBaseSystem() {
        return baseSystem;
    }
}
