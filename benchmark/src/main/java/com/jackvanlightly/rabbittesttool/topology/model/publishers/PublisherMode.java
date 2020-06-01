package com.jackvanlightly.rabbittesttool.topology.model.publishers;

import com.jackvanlightly.rabbittesttool.topology.model.Protocol;

public class PublisherMode {
    boolean useConfirms;
    int inFlightLimit;
    Protocol protocol;
    int maxBatchSize;
    int maxBatchSizeBytes;
    int maxBatchWaitMs;

    public boolean isUseConfirms() {
        return useConfirms;
    }

    public void setUseConfirms(boolean useConfirms) {
        this.useConfirms = useConfirms;
    }

    public int getInFlightLimit() {
        return inFlightLimit;
    }

    public void setInFlightLimit(int inFlightLimit) {
        this.inFlightLimit = inFlightLimit;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public void setMaxBatchSize(int maxBatchSize) {
        this.maxBatchSize = maxBatchSize;
    }

    public int getMaxBatchWaitMs() {
        return maxBatchWaitMs;
    }

    public void setMaxBatchWaitMs(int maxBatchWaitMs) {
        this.maxBatchWaitMs = maxBatchWaitMs;
    }

    public int getMaxBatchSizeBytes() {
        return maxBatchSizeBytes;
    }

    public void setMaxBatchSizeBytes(int maxBatchSizeBytes) {
        this.maxBatchSizeBytes = maxBatchSizeBytes;
    }
}
