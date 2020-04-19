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

    public ShovelConfig(ShovelTarget srcTargetType, String srcTargetName, boolean srcIsDownstream, int prefetch, int reconnectDelaySeconds, String ackMode) {
        this.srcTargetType = srcTargetType;
        this.srcTargetName = srcTargetName;
        this.srcIsDownstream = srcIsDownstream;
        this.prefetch = prefetch;
        this.reconnectDelaySeconds = reconnectDelaySeconds;
        this.ackMode = ackMode;
    }

    public ShovelConfig clone(int scaleNumber) {
        return new ShovelConfig(srcTargetType,
                srcTargetName + VirtualHost.getScaleSuffix(scaleNumber),
                srcIsDownstream,
                prefetch,
                reconnectDelaySeconds,
                ackMode);
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
