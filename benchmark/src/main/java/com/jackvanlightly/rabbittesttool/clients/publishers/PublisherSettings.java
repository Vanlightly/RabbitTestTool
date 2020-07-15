package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.jackvanlightly.rabbittesttool.topology.model.publishers.*;

import java.util.ArrayList;
import java.util.List;

public class PublisherSettings {
    private List<Integer> sequences;
    private PublisherMode publisherMode;
    private SendToMode sendToMode;
    private SendToExchange sendToExchange;
    private SendToQueueGroup sendToQueueGroup;
    private int messageSize;
    private List<MessageHeader> availableHeaders;
    private int messageHeadersPerMessage;
    private boolean useMandatoryFlag;
    private DeliveryMode deliveryMode;
    private int publishRatePerSecond;
    private int frameMax;
    private long messageLimit;
    private long initialPublish;

    public PublisherSettings(SendToExchange sendToExchange,
                             PublisherMode publisherMode,
                             List<Integer> sequences,
                             int messageSize,
                             DeliveryMode deliveryMode,
                             int frameMax,
                             long messageLimit,
                             long initialPublish) {
        this.sendToExchange = sendToExchange;
        this.publisherMode = publisherMode;
        this.sequences = sequences;
        this.messageSize = messageSize;
        this.deliveryMode = deliveryMode;
        this.sendToMode = SendToMode.Exchange;
        this.frameMax = frameMax;
        this.messageLimit = messageLimit;
        this.initialPublish = initialPublish;

        this.availableHeaders = new ArrayList<>();
    }

    public PublisherSettings(SendToQueueGroup sendToQueueGroup,
                             PublisherMode publisherMode,
                             List<Integer> sequences,
                             int messageSize,
                             DeliveryMode deliveryMode,
                             int frameMax,
                             long messageLimit,
                             long initialPublish) {
        this.sendToQueueGroup = sendToQueueGroup;
        this.publisherMode = publisherMode;
        this.sequences = sequences;
        this.messageSize = messageSize;
        this.deliveryMode = deliveryMode;
        this.sendToMode = SendToMode.QueueGroup;
        this.frameMax = frameMax;
        this.messageLimit = messageLimit;
        this.initialPublish = initialPublish;
        this.useMandatoryFlag = true;

        this.availableHeaders = new ArrayList<>();
    }

    public List<Integer> getSequences() {
        return sequences;
    }

    public int getMessageSize() {
        return messageSize;
    }

    public PublisherMode getPublisherMode() {
        return publisherMode;
    }

    public SendToExchange getSendToExchange() {
        return sendToExchange;
    }

    public SendToQueueGroup getSendToQueueGroup() {
        return sendToQueueGroup;
    }

    public SendToMode getSendToMode() {
        return sendToMode;
    }

    public List<MessageHeader> getAvailableHeaders() {
        return availableHeaders;
    }

    public void setAvailableHeaders(List<MessageHeader> availableHeaders) {
        this.availableHeaders = availableHeaders;
    }

    public int getMessageHeadersPerMessage() {
        return messageHeadersPerMessage;
    }

    public void setMessageHeadersPerMessage(int messageHeadersPerMessage) {
        this.messageHeadersPerMessage = messageHeadersPerMessage;
    }

    public boolean useMandatoryFlag() {
        return useMandatoryFlag;
    }

    public void setUseMandatoryFlag(boolean useMandatoryFlag) {
        this.useMandatoryFlag = useMandatoryFlag;
    }

    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public int getPublishRatePerSecond() {
        return publishRatePerSecond;
    }

    public void setPublishRatePerSecond(int publishRatePerSecond) {
        this.publishRatePerSecond = publishRatePerSecond;
    }

    public int getFrameMax() {
        return frameMax;
    }

    public void setFrameMax(int frameMax) {
        this.frameMax = frameMax;
    }

    public long getMessageLimit() {
        return messageLimit;
    }

    public void setMessageLimit(long messageLimit) {
        this.messageLimit = messageLimit;
    }

    public long getInitialPublish() {
        return initialPublish;
    }

    public void setInitialPublish(long initialPublish) {
        this.initialPublish = initialPublish;
    }
}
