package com.jackvanlightly.rabbittesttool.clients.consumers;

import com.jackvanlightly.rabbittesttool.topology.model.consumers.AckMode;

public class ConsumerSettings {
    private String queue;
    private AckMode ackMode;
    private int frameMax;
    private int processingMs;
    private boolean connectToDownstream;
    private boolean instrumentMessagePayloads;

    public ConsumerSettings(String queue,
                            AckMode ackMode,
                            int frameMax,
                            int processingMs,
                            boolean connectToDownstream,
                            boolean instrumentMessagePayloads) {
        this.queue = queue;
        this.ackMode = ackMode;
        this.frameMax = frameMax;
        this.processingMs = processingMs;
        this.connectToDownstream = connectToDownstream;
        this.instrumentMessagePayloads = instrumentMessagePayloads;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public AckMode getAckMode() {
        return ackMode;
    }

    public void setAckMode(AckMode ackMode) {
        this.ackMode = ackMode;
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

    public boolean shouldInstrumentMessagePayloads() {
        return instrumentMessagePayloads;
    }

    public void setInstrumentMessagePayloads(boolean instrumentMessagePayloads) {
        this.instrumentMessagePayloads = instrumentMessagePayloads;
    }
}
