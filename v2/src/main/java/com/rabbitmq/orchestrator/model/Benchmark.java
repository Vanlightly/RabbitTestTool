package com.rabbitmq.orchestrator.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Benchmark {
    Map<String, Workload> systemWorkloads;
    int ordinal;

    public Benchmark(Map<String, Workload> systemWorkloads, int ordinal) {
        this.systemWorkloads = systemWorkloads;
        this.ordinal = ordinal;
    }

    public Map<String, Workload> getSystemWorkloads() {
        return systemWorkloads;
    }

    public void setSystemWorkloads(Map<String, Workload> systemWorkloads) {
        this.systemWorkloads = systemWorkloads;
    }

    public int getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(int ordinal) {
        this.ordinal = ordinal;
    }

    public Workload getWorkloadFor(String systemName) {
        return systemWorkloads.get(systemName);
    }
}
