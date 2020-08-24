package com.jackvanlightly.rabbittesttool.model;

public class Summary {
    String benchmarkId;
    long publishedCount;
    long consumedCount;
    long unconsumedRemainder;
    long redeliveredCount;
    boolean checkedOrdering;
    boolean checkedDataloss;
    boolean checkedDuplicates;
    boolean checkedConnectivity;
    boolean checkedConsumeUptime;
    boolean includeRedeliveredInChecks;
    boolean safeConfiguration;
    long orderingViolations;
    long redeliveredOrderingViolations;
    long datalossViolations;
    long duplicateViolations;
    long redeliveredDuplicateViolations;
    double connectionAvailability;
    int disconnectionPeriods;
    int maxDisconnectionMs;
    double consumeAvailability;
    int noConsumePeriods;
    int maxNoconsumeMs;

    public Summary() {}

    public String getBenchmarkId() {
        return benchmarkId;
    }

    public void setBenchmarkId(String benchmarkId) {
        this.benchmarkId = benchmarkId;
    }

    public long getPublishedCount() {
        return publishedCount;
    }

    public void setPublishedCount(long publishedCount) {
        this.publishedCount = publishedCount;
    }

    public long getConsumedCount() {
        return consumedCount;
    }

    public void setConsumedCount(long consumedCount) {
        this.consumedCount = consumedCount;
    }

    public long getUnconsumedRemainder() {
        return unconsumedRemainder;
    }

    public void setUnconsumedRemainder(long unconsumedRemainder) {
        this.unconsumedRemainder = unconsumedRemainder;
    }

    public long getRedeliveredCount() {
        return redeliveredCount;
    }

    public void setRedeliveredCount(long redeliveredCount) {
        this.redeliveredCount = redeliveredCount;
    }

    public boolean isCheckedOrdering() {
        return checkedOrdering;
    }

    public void setCheckedOrdering(boolean checkedOrdering) {
        this.checkedOrdering = checkedOrdering;
    }

    public boolean isCheckedDataloss() {
        return checkedDataloss;
    }

    public void setCheckedDataloss(boolean checkedDataloss) {
        this.checkedDataloss = checkedDataloss;
    }

    public boolean isCheckedDuplicates() {
        return checkedDuplicates;
    }

    public void setCheckedDuplicates(boolean checkedDuplicates) {
        this.checkedDuplicates = checkedDuplicates;
    }

    public boolean isCheckedConnectivity() {
        return checkedConnectivity;
    }

    public void setCheckedConnectivity(boolean checkedConnectivity) {
        this.checkedConnectivity = checkedConnectivity;
    }

    public boolean isCheckedConsumeUptime() {
        return checkedConsumeUptime;
    }

    public void setCheckedConsumeUptime(boolean checkedConsumeUptime) {
        this.checkedConsumeUptime = checkedConsumeUptime;
    }

    public boolean isIncludeRedeliveredInChecks() {
        return includeRedeliveredInChecks;
    }

    public void setIncludeRedeliveredInChecks(boolean includeRedeliveredInChecks) {
        this.includeRedeliveredInChecks = includeRedeliveredInChecks;
    }

    public boolean isSafeConfiguration() {
        return safeConfiguration;
    }

    public void setSafeConfiguration(boolean safeConfiguration) {
        this.safeConfiguration = safeConfiguration;
    }

    public long getOrderingViolations() {
        return orderingViolations;
    }

    public void setOrderingViolations(long orderingViolations) {
        this.orderingViolations = orderingViolations;
    }

    public long getRedeliveredOrderingViolations() {
        return orderingViolations;
    }

    public void setRedeliveredOrderingViolations(long redeliveredOrderingViolations) {
        this.redeliveredOrderingViolations = redeliveredOrderingViolations;
    }

    public long getDatalossViolations() {
        return datalossViolations;
    }

    public void setDatalossViolations(long datalossViolations) {
        this.datalossViolations = datalossViolations;
    }

    public long getDuplicateViolations() {
        return duplicateViolations;
    }

    public void setDuplicateViolations(long duplicateViolations) {
        this.duplicateViolations = duplicateViolations;
    }

    public long getRedeliveredDuplicateViolations() {
        return duplicateViolations;
    }

    public void setRedeliveredDuplicateViolations(long redeliveredDuplicateViolations) {
        this.redeliveredDuplicateViolations = redeliveredDuplicateViolations;
    }

    public double getConnectionAvailability() {
        return connectionAvailability;
    }

    public void setConnectionAvailability(double connectionAvailability) {
        this.connectionAvailability = connectionAvailability;
    }

    public int getDisconnectionPeriods() {
        return disconnectionPeriods;
    }

    public void setDisconnectionPeriods(int disconnectionPeriods) {
        this.disconnectionPeriods = disconnectionPeriods;
    }

    public int getMaxDisconnectionMs() {
        return maxDisconnectionMs;
    }

    public void setMaxDisconnectionMs(int maxDisconnectionMs) {
        this.maxDisconnectionMs = maxDisconnectionMs;
    }

    public double getConsumeAvailability() {
        return consumeAvailability;
    }

    public void setConsumeAvailability(double consumeAvailability) {
        this.consumeAvailability = consumeAvailability;
    }

    public int getNoConsumePeriods() {
        return noConsumePeriods;
    }

    public void setNoConsumePeriods(int noConsumePeriods) {
        this.noConsumePeriods = noConsumePeriods;
    }

    public int getMaxNoconsumeMs() {
        return maxNoconsumeMs;
    }

    public void setMaxNoconsumeMs(int maxNoconsumeMs) {
        this.maxNoconsumeMs = maxNoconsumeMs;
    }
}
