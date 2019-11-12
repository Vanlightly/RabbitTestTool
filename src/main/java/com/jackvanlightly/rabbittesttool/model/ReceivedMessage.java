package com.jackvanlightly.rabbittesttool.model;

import com.jackvanlightly.rabbittesttool.clients.MessagePayload;

public class ReceivedMessage {
    private MessagePayload messagePayload;
    private long receiveTimestamp;
    private long lag;
    private boolean redelivered;

    public ReceivedMessage(MessagePayload messagePayload, boolean redelivered, long lag, long receiveTimestamp) {
        this.messagePayload = messagePayload;
        this.redelivered = redelivered;
        this.lag = lag;
        this.receiveTimestamp = receiveTimestamp;
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

    public long getReceiveTimestamp() {
        return receiveTimestamp;
    }

    public void setReceiveTimestamp(long receiveTimestamp) {
        this.receiveTimestamp = receiveTimestamp;
    }
}
