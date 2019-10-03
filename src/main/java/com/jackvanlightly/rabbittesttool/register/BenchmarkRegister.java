package com.jackvanlightly.rabbittesttool.register;

import com.jackvanlightly.rabbittesttool.InstanceConfiguration;
import com.jackvanlightly.rabbittesttool.topology.model.Topology;

import java.util.List;

public interface BenchmarkRegister {
    void logBenchmarkStart(String runId,
                           String technology,
                           String version,
                           InstanceConfiguration instanceConfig,
                           Topology topology);

    void logBenchmarkEnd(String benchmarkId);
    void logException(String benchmarkId, Exception e);
    void logStepStart(String benchmarkId, int step, int durationSeconds, String stepValue);
    void logLiveStatistics(String benchmarkId, int step, StepStatistics stepStatistics);
    void logStepEnd(String benchmarkId, int step, StepStatistics stepStatistics);
    List<StepStatistics> getStepStatistics(String runId,
                                           String technology,
                                           String version,
                                           String configTag);
    InstanceConfiguration getInstanceConfiguration(String runId,
                                                   String technology,
                                                   String version,
                                                   String configTag);
}
