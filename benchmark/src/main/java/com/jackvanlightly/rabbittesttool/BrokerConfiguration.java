package com.jackvanlightly.rabbittesttool;

import com.jackvanlightly.rabbittesttool.topology.Broker;

import java.util.List;
import java.util.stream.Collectors;

public class BrokerConfiguration {
    private String technology;
    private String version;
    private List<Broker> hosts;

    public BrokerConfiguration(String technology, String version, List<Broker> hosts) {
        this.technology = technology;
        this.version = version;
        this.hosts = hosts;
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
}
