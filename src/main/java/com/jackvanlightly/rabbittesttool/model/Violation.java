package com.jackvanlightly.rabbittesttool.model;

import com.jackvanlightly.rabbittesttool.clients.MessagePayload;

public class Violation {
    private ViolationType violationType;
    private MessagePayload messagePayload;

    public Violation(ViolationType violationType, MessagePayload messagePayload) {
        this.violationType = violationType;
        this.messagePayload = messagePayload;
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
}
