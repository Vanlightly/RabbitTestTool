package com.jackvanlightly.rabbittesttool.topology.model.consumers;

public class AckMode {
    private boolean manualAcks;
    private short consumerPrefetch;
    private int ackInterval;
    private int ackIntervalMs;

    public AckMode() {
    }

    public AckMode(boolean manualAcks, short consumerPrefetch, int ackInterval, int ackIntervalMs) {
        this.manualAcks = manualAcks;
        this.consumerPrefetch = consumerPrefetch;
        this.ackInterval = ackInterval;
        this.ackIntervalMs = ackIntervalMs;
    }

    public static AckMode withNoAck() {
        return new AckMode();
    }

    public static AckMode withManualAcks(short consumerPrefetch, int ackInterval, int ackIntervalMs) {
        return new AckMode(true, consumerPrefetch, ackInterval, ackIntervalMs);
    }

    public boolean isManualAcks() {
        return manualAcks;
    }

    public void setManualAcks(boolean manualAcks) {
        this.manualAcks = manualAcks;
    }

    public int getConsumerPrefetch() {
        return consumerPrefetch;
    }

    public void setConsumerPrefetch(short consumerPrefetch) {
        this.consumerPrefetch = consumerPrefetch;
    }

    public int getAckInterval() {
        return ackInterval;
    }

    public void setAckInterval(int ackInterval) {
        this.ackInterval = ackInterval;
    }

    public int getAckIntervalMs() {
        return ackIntervalMs;
    }

    public void setAckIntervalMs(int ackIntervalMs) {
        this.ackIntervalMs = ackIntervalMs;
    }
}
