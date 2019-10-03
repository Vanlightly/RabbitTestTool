package com.jackvanlightly.rabbittesttool.model;

import com.jackvanlightly.rabbittesttool.clients.MessagePayload;

public class ReceivedMessage {
    private MessagePayload messagePayload;
    private boolean redelivered;
    private long lag;

    public ReceivedMessage(MessagePayload messagePayload, boolean redelivered, long lag) {
        this.messagePayload = messagePayload;
        this.redelivered = redelivered;
        this.lag = lag;
    }

    public MessagePayload getMessagePayload() {
        return messagePayload;
    }

    public void setMessagePayload(MessagePayload messagePayload) {
        this.messagePayload = messagePayload;
    }

    public boolean isRedelivered() {
        return redelivered;
    }

    public void setRedelivered(boolean redelivered) {
        this.redelivered = redelivered;
    }

    public long getLag() {
        return lag;
    }

    public void setLag(long lag) {
        this.lag = lag;
    }
}
