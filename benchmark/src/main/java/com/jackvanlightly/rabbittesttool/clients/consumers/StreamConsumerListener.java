package com.jackvanlightly.rabbittesttool.clients.consumers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import com.jackvanlightly.rabbittesttool.clients.MessageUtils;
import com.jackvanlightly.rabbittesttool.clients.publishers.MessageGenerator;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.model.ReceivedMessage;
import com.jackvanlightly.rabbittesttool.statistics.MetricGroup;
import com.jackvanlightly.rabbittesttool.statistics.MetricType;
import com.rabbitmq.stream.Message;
import com.rabbitmq.stream.impl.Client;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class StreamConsumerListener implements Client.ChunkListener, Client.MessageListener {

    BenchmarkLogger logger;
    String consumerId;
    String vhost;
    String queue;
    MetricGroup metricGroup;
    MessageModel messageModel;
    volatile int ackInterval;
    volatile int ackIntervalMs;
    volatile int prefetch;
    volatile int processingMs;
    volatile int pendingCreditCounter;
    volatile Instant lastGrantedCreditTime;
    AtomicLong lastOffset;
    long lastRecordedLatency;
    long summedLatency = 0;
    long latencyMeasurements = 0;

    public StreamConsumerListener(String consumerId,
                                  String vhost,
                                  String queue,
                                  MetricGroup metricGroup,
                                  MessageModel messageModel,
                                  AtomicLong lastOffset,
                                  int prefetch,
                                  int ackInterval,
                                  int ackIntervalMs,
                                  int processingMs) {
        this.logger = new BenchmarkLogger("STREAM CONSUMER");
        this.consumerId = consumerId;
        this.vhost = vhost;
        this.queue = queue;
        this.metricGroup = metricGroup;
        this.messageModel = messageModel;
        this.ackInterval = ackInterval;
        this.ackIntervalMs = ackIntervalMs;
        this.prefetch = prefetch;
        this.processingMs = processingMs;
        this.lastOffset = lastOffset;

        pendingCreditCounter = 0;
        lastGrantedCreditTime = Instant.now();
    }

    public void setProcessingMs(int processingMs) {
        this.processingMs = processingMs;
    }

    public void setAckInterval(int ackInterval) {
        this.ackInterval = ackInterval;
    }

    public void setAckIntervalMs(int ackIntervalMs) {
        this.ackIntervalMs = ackIntervalMs;
    }

    @Override
    public void handle(byte subscriptionId, long offset, Message message) {
        try {
            this.lastOffset.set(offset);
            this.handleMessage(message);

            if(processingMs > 0)
                waitFor(processingMs);
        }
        catch(IOException e) {
            logger.error("Failed consuming message");
        }
    }

    void handleMessage(Message message) throws IOException {
        MessagePayload mp = MessageGenerator.toMessagePayload(message.getBodyAsBinary());
        long now = System.nanoTime();
        long lag = MessageUtils.getLag(now, mp.getTimestamp());
        messageModel.received(new ReceivedMessage(consumerId, vhost, queue, mp, false, lag, System.currentTimeMillis()));

        summedLatency+=lag;
        latencyMeasurements++;

        if(now-lastRecordedLatency > 100000000) {
            long avgLag = summedLatency/latencyMeasurements;
            metricGroup.add(MetricType.ConsumerLatencies, avgLag);
            summedLatency = 0;
            latencyMeasurements = 0;
            lastRecordedLatency = now;
        }
    }

    @Override
    public void handle(Client client, byte subscriptionId, long offset, long messageCount, long dataSize) {
        try {
            metricGroup.increment(MetricType.ConsumerStreamBatches);
            metricGroup.increment(MetricType.ConsumerReceivedMessage, messageCount);
            metricGroup.increment(MetricType.ConsumerStreamMessages, messageCount);
            metricGroup.increment(MetricType.ConsumerPrefetch, prefetch*messageCount);
            metricGroup.increment(MetricType.ConsumerAckInterval, ackInterval*messageCount);
            metricGroup.increment(MetricType.ConsumerAckIntervalMs, ackIntervalMs*messageCount);
            metricGroup.increment(MetricType.ConsumerReceivedBytes, dataSize);

            if (this.ackInterval == 1) {
                client.credit(subscriptionId, 1);
            } else {
                pendingCreditCounter++;
                if (pendingCreditCounter >= ackInterval && pendingCreditCounter % ackInterval == 0) {
                    client.credit(subscriptionId, pendingCreditCounter);
                    pendingCreditCounter = 0;
                    lastGrantedCreditTime = Instant.now();
                } else if (Duration.between(lastGrantedCreditTime, Instant.now()).toMillis() > ackIntervalMs) {
                    client.credit(subscriptionId, pendingCreditCounter);
                    pendingCreditCounter = 0;
                    lastGrantedCreditTime = Instant.now();
                }
            }
        }
        catch(Exception e) {
            logger.error("Error in chunk listener: ", e);
        }
    }

    private void waitFor(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
