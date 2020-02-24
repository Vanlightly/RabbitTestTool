package com.jackvanlightly.rabbittesttool;

import com.jackvanlightly.rabbittesttool.topology.Broker;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BrokerConfiguration {
    private String technology;
    private String version;
    private List<Broker> hosts;
    private List<Broker> downstreamHosts;
    private int federationPrefetchCount;
    private int federationReconnectDelaySeconds;
    private String federationAckMode;

    public BrokerConfiguration(String technology, String version, List<Broker> hosts, List<Broker> downstreamHosts) {
        this.technology = technology;
        this.version = version;
        this.hosts = hosts;
        this.downstreamHosts = downstreamHosts;
    }

    public String getTechnology() {
        return technology;
    }

    public String getVersion() {
        return version;
    }

    public List<Broker> getHosts() {
        return hosts;
    }

    public List<String> getNodeNames() {
        return getHosts().stream().map(x -> x.getNodeName()).collect(Collectors.toList());
    }

    public List<Broker> getDownstreamHosts() {
        return downstreamHosts;
    }

    public List<String> getDownstreamNodeNames() {
        return getDownstreamHosts().stream().map(x -> x.getNodeName()).collect(Collectors.toList());
    }

    public int getFederationPrefetchCount() {
        return federationPrefetchCount;
    }

    public void setFederationPrefetchCount(int federationPrefetchCount) {
        this.federationPrefetchCount = federationPrefetchCount;
    }

    public int getFederationReconnectDelaySeconds() {
        return federationReconnectDelaySeconds;
    }

    public void setFederationReconnectDelaySeconds(int federationReconnectDelaySeconds) {
        this.federationReconnectDelaySeconds = federationReconnectDelaySeconds;
    }

    public String getFederationAckMode() {
        return federationAckMode;
    }

    public void setFederationAckMode(String federationAckMode) {
        this.federationAckMode = federationAckMode;
    }
}
