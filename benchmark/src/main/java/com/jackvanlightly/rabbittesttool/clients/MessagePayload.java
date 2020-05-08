package com.jackvanlightly.rabbittesttool.clients;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

public class MessagePayload implements Comparable<MessagePayload> {
    public static final int MinimumMessageSize = 20;

    private Integer stream;
    private Long sequenceNumber;
    private long timestamp;

    public MessagePayload() {
    }

    public MessagePayload(Integer stream, Long sequenceNumber, long timestamp) {
        this.stream = stream;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
    }

    public Integer getStream() {
        return stream;
    }

    public void setStream(Integer stream) {
        this.stream = stream;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public MessagePayload getClone() {
        return new MessagePayload(stream, sequenceNumber, timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessagePayload that = (MessagePayload) o;
        return timestamp == that.timestamp &&
                stream.equals(that.stream) &&
                sequenceNumber.equals(that.sequenceNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stream, sequenceNumber, timestamp);
    }

    @Override
    public String toString() {
        return "MessagePayload{" +
                "stream=" + stream +
                ", sequenceNumber=" + sequenceNumber +
                ", timestamp=" + timestamp +
                '}';
    }

    @Override
    public int compareTo(MessagePayload o) {
        if(sequenceNumber < o.getSequenceNumber())
            return -1;
        else if(sequenceNumber.equals(o.getSequenceNumber()))
            return 0;
        else
            return 1;
    }
}
