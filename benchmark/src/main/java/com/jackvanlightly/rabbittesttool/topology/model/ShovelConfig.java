package com.jackvanlightly.rabbittesttool.topology.model;

public class ShovelConfig {
    private ShovelTarget srcTargetType;
    private String srcTargetName;
    private boolean srcIsDownstream;
    private int prefetch;
    private int reconnectDelaySeconds;
    private String ackMode;

    public ShovelConfig() {
    }

    public ShovelTarget getSrcTargetType() {
        return srcTargetType;
    }

    public void setSrcTargetType(ShovelTarget srcTargetType) {
        this.srcTargetType = srcTargetType;
    }

    public String getSrcTargetName() {
        return srcTargetName;
    }

    public void setSrcTargetName(String srcTargetName) {
        this.srcTargetName = srcTargetName;
    }

    public boolean isSrcIsDownstream() {
        return srcIsDownstream;
    }

    public void setSrcIsDownstream(boolean srcIsDownstream) {
        this.srcIsDownstream = srcIsDownstream;
    }

    public int getPrefetch() {
        return prefetch;
    }

    public void setPrefetch(int prefetch) {
        this.prefetch = prefetch;
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
