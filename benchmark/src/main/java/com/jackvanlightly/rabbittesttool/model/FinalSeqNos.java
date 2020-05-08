package com.jackvanlightly.rabbittesttool.model;

public class FinalSeqNos {
    int stream;
    long firstPublished;
    long firstConsumed;
    long firstLost;

    long lastPublished;
    long lastConsumed;
    long lastLost;

    public FinalSeqNos(int stream, long firstPublished, long firstConsumed, long firstLost, long lastPublished, long lastConsumed, long lastLost) {
        this.stream = stream;
        this.firstPublished = firstPublished;
        this.firstConsumed = firstConsumed;
        this.firstLost = firstLost;
        this.lastPublished = lastPublished;
        this.lastConsumed = lastConsumed;
        this.lastLost = lastLost;
    }

    public int getStream() {
        return stream;
    }

    public long getFirstPublished() {
        return firstPublished;
    }

    public long getFirstConsumed() {
        return firstConsumed;
    }

    public long getFirstLost() {
        return firstLost;
    }

    public long getLastPublished() {
        return lastPublished;
    }

    public long getLastConsumed() {
        return lastConsumed;
    }

    public long getLastLost() {
        return lastLost;
    }
}
