package com.jackvanlightly.rabbittesttool;

import java.util.List;

public class BrokerConfiguration {
    private String technology;
    private String version;
    private List<String> nodes;

    public BrokerConfiguration(String technology, String version, List<String> nodes) {
        this.technology = technology;
        this.version = version;
        this.nodes = nodes;
    }

    public String getTechnology() {
        return technology;
    }

    public String getVersion() {
        return version;
    }

    public List<String> getNodes() {
        return nodes;
    }
}
