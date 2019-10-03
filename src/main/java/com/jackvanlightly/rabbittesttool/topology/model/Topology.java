package com.jackvanlightly.rabbittesttool.topology.model;

import java.util.ArrayList;
import java.util.List;

public class Topology {
    private TopologyType topologyType;
    private BenchmarkType benchmarkType;
    private String topologyName;
    private String description;
    private List<VirtualHost> virtualHosts;
    private FixedConfig fixedConfig;
    private VariableConfig variableConfig;
    private List<Policy> policies;

    public Topology() {
        policies = new ArrayList<>();
    }

    public String getTopologyName() {
        return topologyName;
    }

    public void setTopologyName(String topologyName) {
        this.topologyName = topologyName;
    }

    public BenchmarkType getBenchmarkType() {
        return benchmarkType;
    }

    public void setBenchmarkType(BenchmarkType benchmarkType) {
        this.benchmarkType = benchmarkType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TopologyType getTopologyType() {
        return topologyType;
    }

    public void setTopologyType(TopologyType topologyType) {
        this.topologyType = topologyType;
    }

    public List<VirtualHost> getVirtualHosts() {
        return virtualHosts;
    }

    public void setVirtualHosts(List<VirtualHost> virtualHosts) {
        this.virtualHosts = virtualHosts;
    }

    public FixedConfig getFixedConfig() {
        return fixedConfig;
    }

    public void setFixedConfig(FixedConfig fixedConfig) {
        this.fixedConfig = fixedConfig;
    }

    public VariableConfig getVariableConfig() {
        return variableConfig;
    }

    public void setVariableConfig(VariableConfig variableConfig) {
        this.variableConfig = variableConfig;
    }

    public List<Policy> getPolicies() {
        return policies;
    }

    public void setPolicies(List<Policy> policies) {
        this.policies = policies;
    }
}
