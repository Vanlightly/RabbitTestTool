package com.jackvanlightly.rabbittesttool.topology.model.consumers;

import com.jackvanlightly.rabbittesttool.topology.model.QueueConfig;
import com.jackvanlightly.rabbittesttool.topology.model.VirtualHost;

import java.util.List;

public class ConsumerConfig {
    private String group;
    private String vhostName;
    private AckMode ackMode;
    private int scale;
    private String queueGroup;
    private int frameMax;
    private int processingMs;
    private boolean isDownstream;

    public ConsumerConfig() {
    }

    public ConsumerConfig(String group, String vhostName, AckMode ackMode, int scale, String queueGroup, int frameMax, int processingMs, boolean isDownstream) {
        this.group = group;
        this.vhostName = vhostName;
        this.ackMode = ackMode;
        this.scale = scale;
        this.queueGroup = queueGroup;
        this.frameMax = frameMax;
        this.processingMs = processingMs;
        this.isDownstream = isDownstream;
    }

    public ConsumerConfig clone(int scaleNumber) {
        return new ConsumerConfig(this.group + VirtualHost.getScaleSuffix(scaleNumber),
            this.vhostName,
            this.ackMode,
            this.scale,
            this.queueGroup + VirtualHost.getScaleSuffix(scaleNumber),
            this.frameMax,
            this.processingMs,
            this.isDownstream);
    }

    public String getVhostName() {
        return vhostName;
    }

    public void setVhostName(String vhostName) {
        this.vhostName = vhostName;
    }

    public AckMode getAckMode() {
        return ackMode;
    }

    public void setAckMode(AckMode ackMode) {
        this.ackMode = ackMode;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getQueueGroup() {
        return queueGroup;
    }

    public void setQueueGroup(String queueGroup, List<QueueConfig> queueConfigs) {

        boolean queueGroupExists = queueConfigs.stream().anyMatch(x -> x.getGroup().equals(queueGroup));
        if(queueGroupExists)
            this.queueGroup = queueGroup;
        else
            throw new RuntimeException("No queue group exists that matches: " + queueGroup);
    }

    public void setQueueGroup(String queueGroup) {
        this.queueGroup = queueGroup;
    }

    public int getFrameMax() {
        return frameMax;
    }

    public void setFrameMax(int frameMax) {
        this.frameMax = frameMax;
    }

    public int getProcessingMs() {
        return processingMs;
    }

    public void setProcessingMs(int processingMs) {
        this.processingMs = processingMs;
    }

    public boolean isDownstream() {
        return isDownstream;
    }

    public void setDownstream(boolean downstream) {
        isDownstream = downstream;
    }
}
