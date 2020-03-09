package com.jackvanlightly.rabbittesttool.register;

import com.jackvanlightly.rabbittesttool.InstanceConfiguration;
import com.jackvanlightly.rabbittesttool.topology.model.Topology;

public class BenchmarkMetaData {
    private String configTag;
    private String runId;
    private String technology;
    private String version;
    private InstanceConfiguration instanceConfig;
    private int runOrdinal;
    private String topology;
    private String policy;
    private String arguments;
    private String benchmarkTag;

    public String getConfigTag() {
        return configTag;
    }

    public void setConfigTag(String configTag) {
        this.configTag = configTag;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getTechnology() {
        return technology;
    }

    public void setTechnology(String technology) {
        this.technology = technology;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public int getRunOrdinal() {
        return runOrdinal;
    }

    public void setRunOrdinal(int runOrdinal) {
        this.runOrdinal = runOrdinal;
    }

    public InstanceConfiguration getInstanceConfig() {
        return instanceConfig;
    }

    public void setInstanceConfig(InstanceConfiguration instanceConfig) {
        this.instanceConfig = instanceConfig;
    }

    public String getTopology() {
        return topology;
    }

    public void setTopology(String topology) {
        this.topology = topology;
    }

    public String getPolicy() {
        return policy;
    }

    public void setPolicy(String policy) {
        this.policy = policy;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(String arguments) {
        this.arguments = arguments;
    }

    public String getBenchmarkTag() {
        return benchmarkTag;
    }

    public void setBenchmarkTag(String benchmarkTag) {
        this.benchmarkTag = benchmarkTag;
    }
}
