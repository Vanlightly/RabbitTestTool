package com.jackvanlightly.rabbittesttool.topology.model;

public class FederationUpstream {
    private String vhost;
    private String name;
    private int prefetchCount;
    private int reconnectDelaySeconds;
    private String ackMode;

    public FederationUpstream(String vhost, String name, int prefetchCount, int reconnectDelaySeconds, String ackMode) {
        this.vhost = vhost;
        this.name = name;
        this.prefetchCount = prefetchCount;
        this.reconnectDelaySeconds = reconnectDelaySeconds;
        this.ackMode = ackMode;
    }

    public String getVhost() {
        return vhost;
    }

    public void setVhost(String vhost) {
        this.vhost = vhost;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
