package com.rabbitmq.orchestrator.deploy;

import com.rabbitmq.orchestrator.run.Runner;

public class Deployment {
    Deployer deployer;
    Runner runner;
    BaseSystem baseSystem;

    public Deployment(Deployer deployer, Runner runner, BaseSystem baseSystem) {
        this.deployer = deployer;
        this.runner = runner;
        this.baseSystem = baseSystem;
    }

    public Deployer getDeployer() {
        return deployer;
    }

    public void setDeployer(Deployer deployer) {
        this.deployer = deployer;
    }

    public Runner getRunner() {
        return runner;
    }

    public void setRunner(Runner runner) {
        this.runner = runner;
    }

    public BaseSystem getBaseSystem() {
        return baseSystem;
    }

    public void setBaseSystem(BaseSystem baseSystem) {
        this.baseSystem = baseSystem;
    }
}
