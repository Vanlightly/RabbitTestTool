package com.jackvanlightly.rabbittesttool.register;

import com.jackvanlightly.rabbittesttool.InstanceConfiguration;
import com.jackvanlightly.rabbittesttool.model.ConsumeInterval;
import com.jackvanlightly.rabbittesttool.model.DisconnectedInterval;
import com.jackvanlightly.rabbittesttool.model.Violation;
import com.jackvanlightly.rabbittesttool.topology.model.Topology;

import java.util.List;

public class NullRegister implements BenchmarkRegister {
    @Override
    public void logBenchmarkStart(String benchmarkId, int runOrdinal, String technology, String version, InstanceConfiguration instanceConfig, Topology topology, String arguments, String benchmarkTag) {

    }

    @Override
    public void logBenchmarkEnd(String benchmarkId) {

    }

    @Override
    public void logException(String benchmarkId, Exception e) {

    }

    @Override
    public void logStepStart(String benchmarkId, int step, int durationSeconds, String stepValue) {

    }

    @Override
    public void logLiveStatistics(String benchmarkId, int step, StepStatistics stepStatistics) {

    }

    @Override
    public void logStepEnd(String benchmarkId, int step, StepStatistics stepStatistics) {

    }

    @Override
    public List<StepStatistics> getStepStatistics(String runId, String technology, String version, String configTag) {
        return null;
    }

    @Override
    public InstanceConfiguration getInstanceConfiguration(String runId, String technology, String version, String configTag) {
        return null;
    }

    @Override
    public List<BenchmarkMetaData> getBenchmarkMetaData(String runId, String technology, String version, String configTag) {
        return null;
    }

    @Override
    public void logViolations(String benchmarkId, List<Violation> violations) {

    }

    @Override
    public void logConsumeIntervals(String benchmarkId, List<ConsumeInterval> consumeIntervals, int unavailabilityThresholdSeconds, double availability) {

    }

    @Override
    public void logDisconnectedIntervals(String benchmarkId, List<DisconnectedInterval> disconnectedIntervals, int unavailabilityThresholdSeconds, double availability) {

    }
}
