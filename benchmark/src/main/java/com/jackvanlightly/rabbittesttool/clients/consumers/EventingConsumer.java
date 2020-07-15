package com.jackvanlightly.rabbittesttool.clients.consumers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import com.jackvanlightly.rabbittesttool.clients.MessageUtils;
import com.jackvanlightly.rabbittesttool.clients.publishers.MessageGenerator;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.model.ReceivedMessage;
import com.jackvanlightly.rabbittesttool.statistics.MetricGroup;
import com.jackvanlightly.rabbittesttool.statistics.MetricType;
import com.jackvanlightly.rabbittesttool.statistics.Stats;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EventingConsumer extends DefaultConsumer {

    BenchmarkLogger logger;
    String consumerId;
    String vhost;
    String queue;
    MetricGroup metricGroup;
    MessageModel messageModel;
    volatile int ackInterval;
    volatile int ackIntervalMs;
    volatile short prefetch;
    volatile int processingMs;
    int requeueEveryN;
    long receiveCount;
    long nextRequeue;
    volatile long delTagLastAcked;
    volatile long delTagLastReceived;
    volatile Instant lastAckedTime;
    boolean instrumentMessagePayloads;
    AtomicBoolean consumerCancelled;
    Lock ackLock;

    public EventingConsumer(String consumerId,
                            String vhost,
                            String queue,
                            Channel channel,
                            MetricGroup metricGroup,
                            MessageModel messageModel,
                            short prefetch,
                            int ackInterval,
                            int ackIntervalMs,
                            int processingMs,
                            int requeueEveryN,
                            boolean instrumentMessagePayloads) {
        super(channel);
        this.logger = new BenchmarkLogger("CONSUMER");
        this.consumerId = consumerId;
        this.vhost = vhost;
        this.queue = queue;
        this.metricGroup = metricGroup;
        this.messageModel = messageModel;
        this.ackInterval = ackInterval;
        this.ackIntervalMs = ackIntervalMs;
        this.prefetch = prefetch;
        this.processingMs = processingMs;
        this.requeueEveryN = requeueEveryN;
        this.receiveCount = 0;
        this.nextRequeue = requeueEveryN;
        this.instrumentMessagePayloads = instrumentMessagePayloads;

        consumerCancelled = new AtomicBoolean();
        delTagLastAcked = -1;
        lastAckedTime = Instant.now();
        ackLock = new ReentrantLock();
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

    public void tryAcknowledgeRemaining() {
        ackLock.lock();
        try {
            if(delTagLastReceived > delTagLastAcked)
            {
                if(this.getChannel().isOpen()) {
                    try {
                        getChannel().basicAck(delTagLastReceived, true);
                        delTagLastAcked = delTagLastReceived;
                    }
                    catch(IOException e) {
                        logger.warn("Failed to ack on shutdown", e);
                    }
                }
            }
        }
        finally {
            ackLock.unlock();
        }
    }

    public void ensureAckTimeLimitEnforced() {
        ackLock.lock();
        try {
            long msgsToAck = delTagLastReceived - delTagLastAcked;

            Instant now = Instant.now();
            if (msgsToAck > 0 && Duration.between(lastAckedTime, now).toMillis() > ackIntervalMs) {
                try {
                    getChannel().basicAck(delTagLastReceived, true);
                    delTagLastAcked = delTagLastReceived;
                    lastAckedTime = now;
                    metricGroup.increment(MetricType.ConsumerAckedMessages, msgsToAck);
                    metricGroup.increment(MetricType.ConsumerAcks);
                } catch (IOException e) {
                    logger.warn("Failed to ack on interval ms check", e);
                }
            }
        }
        finally {
            ackLock.unlock();
        }
    }

    @Override
    public void handleDelivery(String consumerTag,
                               Envelope envelope,
                               BasicProperties properties,
                               byte[] body) throws IOException {
        try {
            this.handleMessage(envelope, properties, body, super.getChannel());

            if(processingMs > 0)
                waitFor(processingMs);
        }
        catch(AlreadyClosedException e) {
            logger.info("Could not ack message as connection was lost");
            delTagLastAcked = -1;
        }
    }

    void handleMessage(Envelope envelope, BasicProperties properties, byte[] body, Channel ch) throws IOException {
        if(instrumentMessagePayloads) {
            MessagePayload mp = MessageGenerator.toMessagePayload(body);
            long lag = MessageUtils.getLag(mp.getTimestamp());
            messageModel.received(new ReceivedMessage(consumerId, vhost, queue, mp, envelope.isRedeliver(), lag, System.currentTimeMillis()));
            metricGroup.add(MetricType.ConsumerLatencies, lag);
        }

        int headerCount = 0;
        if(properties != null && properties.getHeaders() != null)
            headerCount = properties.getHeaders().size();

        metricGroup.increment(MetricType.ConsumerReceivedMessage);
        metricGroup.increment(MetricType.ConsumerReceivedBytes, body.length);
        metricGroup.increment(MetricType.ConsumerReceivedHeaderCount, headerCount);
        metricGroup.increment(MetricType.ConsumerPrefetch, prefetch);
        metricGroup.increment(MetricType.ConsumerAckInterval, ackInterval);
        metricGroup.increment(MetricType.ConsumerAckIntervalMs, ackIntervalMs);

        ackLock.lock();

        try {
            long deliveryTag = envelope.getDeliveryTag();
            if (ackInterval == 1) {
                receiveCount++;
                if(receiveCount == nextRequeue) {
                    getChannel().basicReject(deliveryTag, true);
                    nextRequeue = receiveCount + requeueEveryN;
                }
                else {
                    getChannel().basicAck(deliveryTag, false);
                }

                delTagLastAcked = deliveryTag;
                metricGroup.increment(MetricType.ConsumerAcks);
            } else if (ackInterval > 1) {
                long msgsToAck = deliveryTag - delTagLastAcked;
                receiveCount += msgsToAck;

                if (msgsToAck > ackInterval || msgsToAck >= Short.MAX_VALUE - 1) {
                    getChannel().basicAck(deliveryTag, true);
                    delTagLastAcked = deliveryTag;
                    metricGroup.increment(MetricType.ConsumerAcks, msgsToAck);
                } else {
                    Instant now = Instant.now();
                    if (Duration.between(lastAckedTime, now).toMillis() > ackIntervalMs) {
                        getChannel().basicAck(deliveryTag, true);
                        delTagLastAcked = deliveryTag;
                        lastAckedTime = now;
                        metricGroup.increment(MetricType.ConsumerAcks, msgsToAck);
                    }
                }
            }

            delTagLastReceived = deliveryTag;
        }
        finally {
            ackLock.unlock();
        }
    }

    @Override
    public void handleCancel(String consumerTag) throws IOException {
        logger.info("Consumer cancelled with tag: " + consumerTag);
        consumerCancelled.set(true);
    }

    @Override
    public void handleRecoverOk(String consumerTag) {
        logger.info("Consumer recovered with tag: " + consumerTag);
    }

    @Override
    public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
        if(sig.isHardError() && !sig.isInitiatedByApplication())
            metricGroup.increment(MetricType.ConsumerConnectionErrors);
        logger.info("Consumer shutdown with tag: " + consumerTag + " for reason: " + sig.getMessage());
    }

    public boolean isConsumerCancelled() {
        return consumerCancelled.get();
    }

    private void waitFor(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
