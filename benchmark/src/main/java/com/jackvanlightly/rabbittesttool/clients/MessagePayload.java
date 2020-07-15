package com.jackvanlightly.rabbittesttool.clients;

import java.util.Objects;

public class MessagePayload implements Comparable<MessagePayload> {
    public static final int MinimumMessageSize = 20;

    private Integer sequence;
    private Long sequenceNumber;
    private long timestamp;

    public MessagePayload() {
    }

    public MessagePayload(Integer sequence, Long sequenceNumber, long timestamp) {
        this.sequence = sequence;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
    }

    public Integer getSequence() {
        return sequence;
    }

    public void setSequence(Integer sequence) {
        this.sequence = sequence;
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
        return new MessagePayload(sequence, sequenceNumber, timestamp);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MessagePayload that = (MessagePayload) o;
        return timestamp == that.timestamp &&
                sequence.equals(that.sequence) &&
                sequenceNumber.equals(that.sequenceNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sequence, sequenceNumber, timestamp);
    }

    @Override
    public String toString() {
        return "MessagePayload{" +
                "sequence=" + sequence +
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
