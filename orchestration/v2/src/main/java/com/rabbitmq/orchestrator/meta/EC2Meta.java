package com.rabbitmq.orchestrator.meta;

import java.util.List;

public class EC2Meta {
    final String keyPair;
    final String awsUser;
    final String loadgenSecurityGroup;
    final String brokerSecurityGroup;
    final List<String> subnets;
    final String deployScriptsRoot;
    final String runScriptsRoot;

    public EC2Meta(String keyPair,
                   String awsUser,
                   String loadgenSecurityGroup,
                   String brokerSecurityGroup,
                   List<String> subnets,
                   String deployScriptsRoot,
                   String runScriptsRoot) {
        this.keyPair = keyPair;
        this.awsUser = awsUser;
        this.loadgenSecurityGroup = loadgenSecurityGroup;
        this.brokerSecurityGroup = brokerSecurityGroup;
        this.subnets = subnets;
        this.deployScriptsRoot = deployScriptsRoot;
        this.runScriptsRoot = runScriptsRoot;
    }

    public String getKeyPair() {
        return keyPair;
    }

    public String getAwsUser() {
        return awsUser;
    }

    public String getLoadgenSecurityGroup() {
        return loadgenSecurityGroup;
    }

    public String getBrokerSecurityGroup() {
        return brokerSecurityGroup;
    }

    public List<String> getSubnets() {
        return subnets;
    }

    public String getDeployScriptsRoot() {
        return deployScriptsRoot;
    }

    public String getRunScriptsRoot() {
        return runScriptsRoot;
    }
}