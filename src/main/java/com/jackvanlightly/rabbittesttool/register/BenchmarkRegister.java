package com.jackvanlightly.rabbittesttool.register;

import com.jackvanlightly.rabbittesttool.CmdArguments;
import com.jackvanlightly.rabbittesttool.InstanceConfiguration;
import com.jackvanlightly.rabbittesttool.model.ConsumeInterval;
import com.jackvanlightly.rabbittesttool.model.Violation;
import com.jackvanlightly.rabbittesttool.topology.model.Topology;

import java.util.List;

public interface BenchmarkRegister {
    void logBenchmarkStart(String runId,
                           int runOrdinal,
                           String technology,
                           String version,
                           InstanceConfiguration instanceConfig,
                           Topology topology,
                           String arguments,
                           String benchmarkTag);

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
    void logViolations(String benchmarkId, List<Violation> violations);
    void logConsumeIntervals(String benchmarkId, List<ConsumeInterval> consumeIntervals);
}
