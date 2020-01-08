package com.jackvanlightly.rabbittesttool.model;

import com.jackvanlightly.rabbittesttool.clients.MessagePayload;

public class ReceivedMessage {
    private String consumerId;
    private String vhost;
    private String queue;
    private MessagePayload messagePayload;
    private long receiveTimestamp;
    private long lag;
    private boolean redelivered;

    public ReceivedMessage(String consumerId,
                           String vhost,
                           String queue,
                           MessagePayload messagePayload,
                           boolean redelivered,
                           long lag,
                           long receiveTimestamp) {
        this.consumerId = consumerId;
        this.vhost = vhost;
        this.queue = queue;
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

    public String getConsumerId() {
        return consumerId;
    }

    public void setConsumerId(String consumerId) {
        this.consumerId = consumerId;
    }

    public String getVhost() {
        return vhost;
    }

    public void setVhost(String vhost) {
        this.vhost = vhost;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }
}
