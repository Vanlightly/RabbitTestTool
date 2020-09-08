package com.jackvanlightly.rabbittesttool.register;

import java.time.Instant;

public class StepStatistics {
    private String topology;
    private String topologyDescription;
    private String dimensions;
    private String benchmarkType;
    private int runOrdinal;
    private int step;
    private String stepValue;
    private String node;
    private int durationSeconds;
    private int recordingSeconds;
    private long sentCount;
    private long receivedCount;
    private long sentBytesCount;
    private long receivedBytesCount;
    private long nackedCount;
    private long returnedCount;

    private String[] latencyPercentiles;
    private double[] latencies;
    private double[] confirmLatencies;

    private String[] throughPutPercentiles;
    private double[] sendRates;
    private double[] receiveRates;

    private String[] fairnessPercentiles;
    private double[] perPublisherSendRates;
    private double[] perConsumerReceiveRates;

    private long startMs;
    private long endMs;

    public String getTopology() {
        return topology;
    }

    public void setTopology(String topology) {
        this.topology = topology;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public int getRunOrdinal() {
        return runOrdinal;
    }

    public void setRunOrdinal(int runOrdinal) {
        this.runOrdinal = runOrdinal;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getBenchmarkType() {
        return benchmarkType;
    }

    public void setBenchmarkType(String benchmarkType) {
        this.benchmarkType = benchmarkType;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public int getRecordingSeconds() {
        return recordingSeconds;
    }

    public void setRecordingSeconds(int recordingSeconds) {
        this.recordingSeconds = recordingSeconds;
    }

    public long getSentCount() {
        return sentCount;
    }

    public void setSentCount(long sentCount) {
        this.sentCount = sentCount;
    }

    public long getReceivedCount() {
        return receivedCount;
    }

    public long getSentBytesCount() {
        return sentBytesCount;
    }

    public void setSentBytesCount(long sentBytesCount) {
        this.sentBytesCount = sentBytesCount;
    }

    public long getReceivedBytesCount() {
        return receivedBytesCount;
    }

    public void setReceivedBytesCount(long receivedBytesCount) {
        this.receivedBytesCount = receivedBytesCount;
    }

    public void setReceivedCount(long receivedCount) {
        this.receivedCount = receivedCount;
    }

    public long getNackedCount() {
        return nackedCount;
    }

    public void setNackedCount(long nackedCount) {
        this.nackedCount = nackedCount;
    }

    public long getReturnedCount() {
        return returnedCount;
    }

    public void setReturnedCount(long returnedCount) {
        this.returnedCount = returnedCount;
    }

    public double[] getLatencies() {
        return latencies;
    }

    public void setLatencies(double[] latencies) {
        this.latencies = latencies;
    }

    public double[] getConfirmLatencies() {
        return confirmLatencies;
    }

    public void setConfirmLatencies(double[] confirmLatencies) {
        this.confirmLatencies = confirmLatencies;
    }

    public double[] getSendRates() {
        return sendRates;
    }

    public void setSendRates(double[] sendRates) {
        this.sendRates = sendRates;
    }

    public double[] getReceiveRates() {
        return receiveRates;
    }

    public void setReceiveRates(double[] receiveRates) {
        this.receiveRates = receiveRates;
    }

    public String[] getFairnessPercentiles() {
        return fairnessPercentiles;
    }

    public void setFairnessPercentiles(String[] fairnessPercentiles) {
        this.fairnessPercentiles = fairnessPercentiles;
    }

    public double[] getPerPublisherSendRates() {
        return perPublisherSendRates;
    }

    public void setPerPublisherSendRates(double[] perPublisherSendRates) {
        this.perPublisherSendRates = perPublisherSendRates;
    }

    public double[] getPerConsumerReceiveRates() {
        return perConsumerReceiveRates;
    }

    public void setPerConsumerReceiveRates(double[] perConsumerReceiveRates) {
        this.perConsumerReceiveRates = perConsumerReceiveRates;
    }

    public String[] getLatencyPercentiles() {
        return latencyPercentiles;
    }

    public void setLatencyPercentiles(String[] latencyPercentiles) {
        this.latencyPercentiles = latencyPercentiles;
    }

    public String[] getThroughPutPercentiles() {
        return throughPutPercentiles;
    }

    public void setThroughPutPercentiles(String[] throughPutPercentiles) {
        this.throughPutPercentiles = throughPutPercentiles;
    }

    public long getStartMs() {
        return startMs;
    }

    public void setStartMs(long startMs) {
        this.startMs = startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public void setEndMs(long endMs) {
        this.endMs = endMs;
    }

    public String getTopologyDescription() {
        return topologyDescription;
    }

    public void setTopologyDescription(String topologyDescription) {
        this.topologyDescription = topologyDescription;
    }

    public String getDimensions() {
        return dimensions;
    }

    public void setDimensions(String dimensions) {
        this.dimensions = dimensions;
    }

    public String getStepValue() {
        return stepValue;
    }

    public void setStepValue(String stepValue) {
        this.stepValue = stepValue;
    }
}
