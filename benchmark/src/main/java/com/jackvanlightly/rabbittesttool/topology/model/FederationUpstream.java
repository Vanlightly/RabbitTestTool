package com.jackvanlightly.rabbittesttool.topology.model;

public class FederationUpstream {

    private int prefetchCount;
    private int reconnectDelaySeconds;
    private String ackMode;

    public FederationUpstream(int prefetchCount, int reconnectDelaySeconds, String ackMode) {
        this.prefetchCount = prefetchCount;
        this.reconnectDelaySeconds = reconnectDelaySeconds;
        this.ackMode = ackMode;
    }

    public int getPrefetchCount() {
        return prefetchCount;
    }

    public void setPrefetchCount(int prefetchCount) {
        this.prefetchCount = prefetchCount;
    }

    public int getReconnectDelaySeconds() {
        return reconnectDelaySeconds;
    }

    public void setReconnectDelaySeconds(int reconnectDelaySeconds) {
        this.reconnectDelaySeconds = reconnectDelaySeconds;
    }

    public String getAckMode() {
        return ackMode;
    }

    public void setAckMode(String ackMode) {
        this.ackMode = ackMode;
    }
}
