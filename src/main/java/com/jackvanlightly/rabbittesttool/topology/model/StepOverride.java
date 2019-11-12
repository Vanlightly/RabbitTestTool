package com.jackvanlightly.rabbittesttool.topology.model;

public class StepOverride {
    private int stepRepeat;
    private int stepSeconds;
    private int messageSize;
    private int msgsPerSecondPerPublisher;
    private long messageLimit;

    public StepOverride() {
        msgsPerSecondPerPublisher = -1;
    }

    public boolean hasStepRepeat() {
        return stepRepeat > 0;
    }

    public boolean hasStepSeconds() {
        return stepSeconds > 0;
    }

    public int getStepRepeat() {
        return stepRepeat;
    }

    public int getStepSeconds() {
        return stepSeconds;
    }

    public void setStepRepeat(int stepRepeat) {
        this.stepRepeat = stepRepeat;
    }

    public void setStepSeconds(int stepSeconds) {
        this.stepSeconds = stepSeconds;
    }

    public boolean hasMessageSize() {
        return messageSize > 0;
    }

    public int getMessageSize() {
        return messageSize;
    }

    public void setMessageSize(int messageSize) {
        this.messageSize = messageSize;
    }

    public boolean hasMsgsPerSecondPerPublisher() {
        return msgsPerSecondPerPublisher > -1;
    }

    public int getMsgsPerSecondPerPublisher() {
        return msgsPerSecondPerPublisher;
    }

    public void setMsgsPerSecondPerPublisher(int msgsPerSecondPerPublisher) {
        this.msgsPerSecondPerPublisher = msgsPerSecondPerPublisher;
    }

    public long getMessageLimit() {
        return messageLimit;
    }

    public void setMessageLimit(long messageLimit) {
        this.messageLimit = messageLimit;
    }
}
