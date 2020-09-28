package com.rabbitmq.orchestrator.deploy.ec2.model;

import com.rabbitmq.orchestrator.deploy.BaseSystem;
import com.rabbitmq.orchestrator.deploy.k8s.model.K8sEngine;
import com.rabbitmq.orchestrator.model.hosts.Host;
import com.rabbitmq.orchestrator.run.RabbitMQConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class EC2System extends BaseSystem {
    EC2Hardware hardware;
    EC2OS os;
    EC2RabbitMQ rabbitmq;
    RabbitMQConfiguration rabbitmqConfig;
    List<Integer> nodeNumbers;
    List<Integer> downstreamNodeNumbers;
    boolean federationEnabled;
    String downstreamBrokerIps;

    private static AtomicInteger nodeNumberSource = new AtomicInteger();

    public static int nextNodeNumber() {
        return nodeNumberSource.incrementAndGet();
    }

    public static void resetNextNodeNumber() {
        nodeNumberSource.set(0);
    }

    public EC2System(String name, int clusterSize) {
        super(name, Host.EC2, K8sEngine.NONE);
        nodeNumbers = new ArrayList<>();
        downstreamNodeNumbers = new ArrayList<>();

        List<Integer> nNumbers = new ArrayList<>();
        for(int i=0; i<clusterSize; i++)
            nNumbers.add(nextNodeNumber());

        this.nodeNumbers = nNumbers;
        this.downstreamNodeNumbers = nNumbers.stream().map(x -> x + 100).collect(Collectors.toList());
    }

    public EC2Hardware getHardware() {
        return hardware;
    }

    public void setHardware(EC2Hardware hardware) {
        this.hardware = hardware;
    }

    public EC2OS getOs() {
        return os;
    }

    public void setOs(EC2OS os) {
        this.os = os;
    }

    public EC2RabbitMQ getRabbitmq() {
        return rabbitmq;
    }

    public void setRabbitmq(EC2RabbitMQ rabbitmq) {
        this.rabbitmq = rabbitmq;
    }

    public RabbitMQConfiguration getRabbitmqConfig() {
        return rabbitmqConfig;
    }

    public void setRabbitmqConfig(RabbitMQConfiguration rabbitmqConfig) {
        this.rabbitmqConfig = rabbitmqConfig;
    }

    public Integer getFirstNode(boolean isDownstream) {
        if(isDownstream)
            return downstreamNodeNumbers.get(0);
        else
            return nodeNumbers.get(0);
    }

    public Integer getLastNode(boolean isDownstream) {
        if(isDownstream)
            return downstreamNodeNumbers.get(downstreamNodeNumbers.size()-1);
        else
            return nodeNumbers.get(nodeNumbers.size()-1);
    }

    public List<Integer> getNodeNumbers() {
        return nodeNumbers;
    }

    public List<Integer> getDownstreamNodeNumbers() {
        return nodeNumbers;
    }

    public boolean isFederationEnabled() {
        return federationEnabled;
    }

    public void setFederationEnabled(boolean federationEnabled) {
        this.federationEnabled = federationEnabled;
    }

    public String getDownstreamBrokerIps() {
        return downstreamBrokerIps;
    }

    public void setDownstreamBrokerIps(String downstreamBrokerIps) {
        this.downstreamBrokerIps = downstreamBrokerIps;
    }
}
