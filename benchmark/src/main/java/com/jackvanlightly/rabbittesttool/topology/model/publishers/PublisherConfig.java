package com.jackvanlightly.rabbittesttool.topology.model.publishers;

import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import com.jackvanlightly.rabbittesttool.topology.model.VirtualHost;

import java.util.ArrayList;
import java.util.List;

public class PublisherConfig {

    private String group;
    private PublisherMode publisherMode;
    private String vhostName;
    private boolean isDownstream;

    private int scale;

    private SendToMode sendToMode;
    private SendToExchange sendToExchange;
    private SendToQueueGroup sendToQueueGroup;

    private DeliveryMode deliveryMode;
    private int messageSize;
    private int headersPerMessage;
    private int frameMax;
    private List<MessageHeader> availableHeaders;
    private int publishRatePerSecond;
    private int streams;
    private long messageLimit;
    private long initialPublish;

    public PublisherConfig() {
        availableHeaders = new ArrayList<>();
    }

    public PublisherConfig(String group, PublisherMode publisherMode, String vhostName, boolean isDownstream, int scale, SendToMode sendToMode, SendToExchange sendToExchange, SendToQueueGroup sendToQueueGroup, DeliveryMode deliveryMode, int messageSize, int headersPerMessage, int frameMax, List<MessageHeader> availableHeaders, int publishRatePerSecond, int streams, long messageLimit, long initialPublish) {
        this.group = group;
        this.publisherMode = publisherMode;
        this.vhostName = vhostName;
        this.isDownstream = isDownstream;
        this.scale = scale;
        this.sendToMode = sendToMode;
        this.sendToExchange = sendToExchange;
        this.sendToQueueGroup = sendToQueueGroup;
        this.deliveryMode = deliveryMode;
        this.messageSize = messageSize;
        this.headersPerMessage = headersPerMessage;
        this.frameMax = frameMax;
        this.availableHeaders = availableHeaders;
        this.publishRatePerSecond = publishRatePerSecond;
        this.streams = streams;
        this.messageLimit = messageLimit;
        this.initialPublish = initialPublish;
    }

    public PublisherConfig clone(int scaleNumber) {
        SendToExchange s2e = null;
        if(this.sendToExchange != null)
            s2e = this.sendToExchange.clone(scaleNumber);

        SendToQueueGroup s2q = null;
        if(this.sendToQueueGroup != null)
            s2q = this.sendToQueueGroup.clone(scaleNumber);

        return new PublisherConfig(this.group + VirtualHost.getScaleSuffix(scaleNumber),
            this.publisherMode,
            this.vhostName,
            this.isDownstream,
            this.scale,
            this.sendToMode,
            s2e,
            s2q,
            this.deliveryMode,
            this.messageSize,
            this.headersPerMessage,
            this.frameMax,
            this.availableHeaders,
            this.publishRatePerSecond,
            this.streams,
            this.messageLimit,
            this.initialPublish);
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getVhostName() {
        return vhostName;
    }

    public void setVhostName(String vhostName) {
        this.vhostName = vhostName;
    }

    public SendToMode getSendToMode() {
        return sendToMode;
    }

    public void setSendToMode(SendToMode sendToMode) {
        this.sendToMode = sendToMode;
    }

    public PublisherMode getPublisherMode() {
        return publisherMode;
    }

    public void setPublisherMode(PublisherMode publisherMode) {
        this.publisherMode = publisherMode;
    }

    public DeliveryMode getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(DeliveryMode deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public SendToExchange getSendToExchange() {
        return sendToExchange;
    }

    public void setSendToExchange(SendToExchange sendToExchange) {
        this.sendToExchange = sendToExchange;
        this.sendToMode = SendToMode.Exchange;
    }

    public SendToQueueGroup getSendToQueueGroup() {
        return sendToQueueGroup;
    }

    public void setSendToQueueGroup(SendToQueueGroup sendToQueueGroup) {

        this.sendToQueueGroup = sendToQueueGroup;
        this.sendToMode = SendToMode.QueueGroup;
    }

    public int getMessageSize() {
        return messageSize;
    }

    public void setMessageSize(int messageSize) {
        if(messageSize < MessagePayload.MinimumMessageSize)
            this.messageSize = MessagePayload.MinimumMessageSize;
        this.messageSize = messageSize;
    }

    public int getHeadersPerMessage() {
        return headersPerMessage;
    }

    public void setHeadersPerMessage(int headersPerMessage) {
        this.headersPerMessage = headersPerMessage;
    }

    public List<MessageHeader> getAvailableHeaders() {
        return availableHeaders;
    }

    public void setAvailableHeaders(List<MessageHeader> availableHeaders) {
        this.availableHeaders = availableHeaders;
    }

    public int getPublishRatePerSecond() {
        return publishRatePerSecond;
    }

    public void setPublishRatePerSecond(int publishRatePerSecond) {
        this.publishRatePerSecond = publishRatePerSecond;
    }

    public int getStreams() {
        return streams;
    }

    public void setStreams(int streams) {
        this.streams = streams;
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

    public boolean isDownstream() {
        return isDownstream;
    }

    public void setDownstream(boolean downstream) {
        isDownstream = downstream;
    }
}
