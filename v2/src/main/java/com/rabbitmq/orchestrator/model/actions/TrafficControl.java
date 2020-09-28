package com.rabbitmq.orchestrator.model.actions;

public class TrafficControl {
    boolean applyToClientTraffic;
    boolean applyToAllBrokers;
    int delayMs;
    int delayJitterMs;
    String delayDistribution;
    int bandwidth;
    String packetLossMode;
    String packetLossArg1;
    String packetLossArg2;
    String packetLossArg3;
    String packetLossArg4;

    public TrafficControl(boolean applyToClientTraffic,
                          boolean applyToAllBrokers,
                          int delayMs,
                          int delayJitterMs,
                          String delayDistribution,
                          int bandwidth,
                          String packetLossMode,
                          String packetLossArg1,
                          String packetLossArg2,
                          String packetLossArg3,
                          String packetLossArg4) {
        this.applyToClientTraffic = applyToClientTraffic;
        this.applyToAllBrokers = applyToAllBrokers;
        this.delayMs = delayMs;
        this.delayJitterMs = delayJitterMs;
        this.delayDistribution = delayDistribution;
        this.bandwidth = bandwidth;
        this.packetLossMode = packetLossMode;
        this.packetLossArg1 = packetLossArg1;
        this.packetLossArg2 = packetLossArg2;
        this.packetLossArg3 = packetLossArg3;
        this.packetLossArg4 = packetLossArg4;
    }

    public boolean isApplyToClientTraffic() {
        return applyToClientTraffic;
    }

    public void setApplyToClientTraffic(boolean applyToClientTraffic) {
        this.applyToClientTraffic = applyToClientTraffic;
    }

    public boolean isApplyToAllBrokers() {
        return applyToAllBrokers;
    }

    public void setApplyToAllBrokers(boolean applyToAllBrokers) {
        this.applyToAllBrokers = applyToAllBrokers;
    }

    public int getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(int delayMs) {
        this.delayMs = delayMs;
    }

    public int getDelayJitterMs() {
        return delayJitterMs;
    }

    public void setDelayJitterMs(int delayJitterMs) {
        this.delayJitterMs = delayJitterMs;
    }

    public String getDelayDistribution() {
        return delayDistribution;
    }

    public void setDelayDistribution(String delayDistribution) {
        this.delayDistribution = delayDistribution;
    }

    public int getBandwidth() {
        return bandwidth;
    }

    public void setBandwidth(int bandwidth) {
        this.bandwidth = bandwidth;
    }

    public String getPacketLossMode() {
        return packetLossMode;
    }

    public void setPacketLossMode(String packetLossMode) {
        this.packetLossMode = packetLossMode;
    }

    public String getPacketLossArg1() {
        return packetLossArg1;
    }

    public String getPacketLossArg2() {
        return packetLossArg2;
    }

    public String getPacketLossArg3() {
        return packetLossArg3;
    }

    public String getPacketLossArg4() {
        return packetLossArg4;
    }
}
