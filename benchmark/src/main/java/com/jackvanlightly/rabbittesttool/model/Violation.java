package com.jackvanlightly.rabbittesttool.model;

import com.jackvanlightly.rabbittesttool.clients.MessagePayload;

import java.text.MessageFormat;

public class Violation implements Comparable<Violation> {
    private ViolationType violationType;
    private MessagePayload messagePayload;
    private MessagePayload priorMessagePayload;
    private Span span;

    public Violation(ViolationType violationType, Span span) {
        this.violationType = violationType;
        this.span = span;
    }

    public Violation(ViolationType violationType, MessagePayload messagePayload) {
        this.violationType = violationType;
        this.messagePayload = messagePayload;
    }

    public Violation(ViolationType violationType, MessagePayload messagePayload, MessagePayload priorMessagePayload) {
        this.violationType = violationType;
        this.messagePayload = messagePayload;
        this.priorMessagePayload = priorMessagePayload;
    }

    public ViolationType getViolationType() {
        return violationType;
    }

    public void setViolationType(ViolationType violationType) {
        this.violationType = violationType;
    }

    public MessagePayload getMessagePayload() {
        return messagePayload;
    }

    public void setMessagePayload(MessagePayload messagePayload) {
        this.messagePayload = messagePayload;
    }

    public MessagePayload getPriorMessagePayload() {
        return priorMessagePayload;
    }

    public void setPriorMessagePayload(MessagePayload priorMessagePayload) {
        this.priorMessagePayload = priorMessagePayload;
    }

    public long getTimestamp() {
        if(messagePayload != null)
            return messagePayload.getTimestamp();
        else
            return span.getCreated().toEpochMilli();
    }

    public Span getSpan() {
        return span;
    }

    @Override
    public int compareTo(Violation o) {
        int stream1 = messagePayload != null ? messagePayload.getSequence() : span.getSequence();
        int stream2 = o.getMessagePayload() != null ? o.getMessagePayload().getSequence() : o.getSpan().getSequence();

        if(stream1 != stream2)
            return Integer.compare(stream1, stream2);

        long seqNo1 = messagePayload != null ? messagePayload.getSequenceNumber() : span.getLow();
        long seqNo2 = o.getMessagePayload() != null ? o.getMessagePayload().getSequenceNumber() : o.getSpan().getLow();

        return Long.compare(seqNo1, seqNo2);
    }

    public String toLogString() {
        if(getViolationType() == ViolationType.Ordering || getViolationType() == ViolationType.RedeliveredOrdering) {
            return MessageFormat.format("Type: {0}, Sequence: {1,number,#}, SeqNo: {2,number,#}, Timestamp {3,number,#}, Prior Seq No {4,number,#}, Prior Timestamp {5,number,#}",
                    getViolationType(),
                    getMessagePayload().getSequence(),
                    getMessagePayload().getSequenceNumber(),
                    getMessagePayload().getTimestamp(),
                    getPriorMessagePayload().getSequenceNumber(),
                    getPriorMessagePayload().getTimestamp()
            );
        }
        else if(getMessagePayload() != null) {
            return MessageFormat.format("Type: {0}, Sequence: {1,number,#}, SeqNo: {2,number,#}, Timestamp {3,number,#}",
                    getViolationType(),
                    getMessagePayload().getSequence(),
                    getMessagePayload().getSequenceNumber(),
                    getMessagePayload().getTimestamp());
        }
        else {
            return MessageFormat.format("Type: {0}, Sequence: {1,number,#}, Size: {2,number,#}, Low SeqNo: {3,number,#}, High SeqNo: {4,number,#}, Span ts {5}",
                    getViolationType(),
                    getSpan().getSequence(),
                    getSpan().size(),
                    getSpan().getLow(),
                    getSpan().getHigh(),
                    getSpan().getCreated());
        }
    }
}
