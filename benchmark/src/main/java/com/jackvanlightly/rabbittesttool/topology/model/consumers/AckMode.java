package com.jackvanlightly.rabbittesttool.topology.model.consumers;

public class AckMode {
    private boolean manualAcks;
    private int consumerPrefetch;
    private int ackInterval;

    public AckMode() {
    }

    public AckMode(boolean manualAcks, int consumerPrefetch, int ackInterval) {
        this.manualAcks = manualAcks;
        this.consumerPrefetch = consumerPrefetch;
        this.ackInterval = ackInterval;
    }

    public static AckMode withNoAck() {
        return new AckMode();
    }

    public static AckMode withManualAcks(int consumerPrefetch, int ackInterval) {
        return new AckMode(true, consumerPrefetch, ackInterval);
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

    public void setConsumerPrefetch(int consumerPrefetch) {
        this.consumerPrefetch = consumerPrefetch;
    }

    public int getAckInterval() {
        return ackInterval;
    }

    public void setAckInterval(int ackInterval) {
        this.ackInterval = ackInterval;
    }
}
