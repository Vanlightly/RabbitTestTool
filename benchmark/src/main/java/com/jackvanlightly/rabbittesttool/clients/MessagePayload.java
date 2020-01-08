package com.jackvanlightly.rabbittesttool.clients;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

public class MessagePayload {
    public static final int MinimumMessageSize = 16;

    private Integer stream;
    private Integer sequenceNumber;
    private long timestamp;

    public MessagePayload() {
    }

    public MessagePayload(Integer stream, Integer sequenceNumber, long timestamp) {
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

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public byte[] toByteArray() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(stream);
        baos.write(sequenceNumber);
        baos.write(ByteUtils.longToBytes(timestamp));
        return baos.toByteArray();
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
}
