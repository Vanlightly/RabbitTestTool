package com.jackvanlightly.rabbittesttool.topology.model.consumers;

import com.jackvanlightly.rabbittesttool.topology.TopologyException;

public class AckMode {
    boolean manualAcks;
    short consumerPrefetch;
    boolean globalPrefetch;
    int ackInterval;
    int ackIntervalMs;
    int requeueEveryNth;

    public AckMode() {
    }

    public AckMode(boolean manualAcks,
                   short consumerPrefetch,
                   boolean globalPrefetch,
                   int ackInterval,
                   int ackIntervalMs,
                   int requeueEveryNth) {
        this.manualAcks = manualAcks;
        this.consumerPrefetch = consumerPrefetch;
        this.globalPrefetch = globalPrefetch;
        this.ackInterval = ackInterval;
        this.ackIntervalMs = ackIntervalMs;
        this.requeueEveryNth = requeueEveryNth;

        if(requeueEveryNth > 0 && ackInterval > 1)
            throw new TopologyException("Invalid AckMode: cannot use requeuing with an ack interval > 1");
    }

    public static AckMode withNoAck() {
        return new AckMode();
    }

    public static AckMode withManualAcks(short consumerPrefetch,
                                         boolean globalPrefetch,
                                         int ackInterval,
                                         int ackIntervalMs,
                                         int requeueEveryNth) {
        return new AckMode(true, consumerPrefetch, globalPrefetch, ackInterval, ackIntervalMs, requeueEveryNth);
    }

    public boolean isManualAcks() {
        return manualAcks;
    }

    public void setManualAcks(boolean manualAcks) {
        this.manualAcks = manualAcks;
    }

    public short getConsumerPrefetch() {
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

    public boolean isGlobalPrefetch() {
        return globalPrefetch;
    }

    public void setGlobalPrefetch(boolean globalPrefetch) {
        this.globalPrefetch = globalPrefetch;
    }

    public int getRequeueEveryNth() {
        return requeueEveryNth;
    }

    public void setRequeueEveryNth(int requeueEveryNth) {
        this.requeueEveryNth = requeueEveryNth;
    }
}
