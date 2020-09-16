package com.jackvanlightly.rabbittesttool.clients.consumers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import com.jackvanlightly.rabbittesttool.clients.MessageUtils;
import com.jackvanlightly.rabbittesttool.clients.SequenceLag;
import com.jackvanlightly.rabbittesttool.clients.publishers.MessageGenerator;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.model.ReceivedMessage;
import com.jackvanlightly.rabbittesttool.statistics.MetricGroup;
import com.jackvanlightly.rabbittesttool.statistics.MetricType;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
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
    AtomicBoolean consumerCancelled;
    Lock ackLock;
    long lastRecordedLatency;
    Map<Integer, SequenceLag> sequenceLagMap;

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
                            int requeueEveryN) {
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

        consumerCancelled = new AtomicBoolean();
        delTagLastAcked = -1;
        sequenceLagMap = new HashMap<>();
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
        if(ackIntervalMs == 0)
            return;

        ackLock.lock();
        try {
            long msgsToAck = delTagLastReceived - delTagLastAcked;

            Instant now = Instant.now();
            if (msgsToAck > 0 && Duration.between(lastAckedTime, now).toMillis() > ackIntervalMs) {
                try {
                    if(getChannel().isOpen()) {
                        getChannel().basicAck(delTagLastReceived, true);
                        delTagLastAcked = delTagLastReceived;
                        lastAckedTime = now;
                        metricGroup.increment(MetricType.ConsumerAckedMessages, msgsToAck);
                        metricGroup.increment(MetricType.ConsumerAcks);
                    }
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
            if(consumerTag.equals(super.getConsumerTag())) {
                this.handleMessage(envelope, properties, body, super.getChannel());

                if (processingMs > 0)
                    waitFor(processingMs);
            }
        }
        catch(AlreadyClosedException e) {
            //logger.info("Could not ack message as connection was lost");
            delTagLastAcked = -1;
        }
        catch(Exception e) {
            logger.error("Unexpected error handling message in consumer " + consumerId +  "with tag " + consumerTag, e);
            throw e;
        }
    }

    void handleMessage(Envelope envelope, BasicProperties properties, byte[] body, Channel ch) throws IOException {
        MessagePayload mp = MessageGenerator.toMessagePayload(body);
        if(mp == null)
            return;

        long now = System.nanoTime();
        long lag = MessageUtils.getLag(now, mp.getTimestamp());
        SequenceLag seqLag = sequenceLagMap.get(mp.getSequence());
        if(seqLag == null) {
            seqLag = new SequenceLag();
            seqLag.totalLag = lag;
            seqLag.measurements = 1;
            sequenceLagMap.put(mp.getSequence(), seqLag);

            if(seqLag.measurements == 0)
                System.out.println("Put 0 seq lag for " + mp.getSequence());
        }
        else {
            seqLag.totalLag += lag;
            seqLag.measurements++;
        }

        if(now-lastRecordedLatency > 100000000) {
            for(Map.Entry<Integer, SequenceLag> entry : sequenceLagMap.entrySet()) {
                if(entry.getValue().measurements > 0) {
                    long avgLag = entry.getValue().totalLag / entry.getValue().measurements;
                    metricGroup.add(MetricType.ConsumerLatencies, avgLag);
                    entry.getValue().totalLag = 0;
                    entry.getValue().measurements = 0;
                }
            }
            lastRecordedLatency = now;
        }

        messageModel.received(new ReceivedMessage(consumerId, vhost, queue, mp, envelope.isRedeliver(), lag, System.currentTimeMillis()));

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
                    Instant instantNow = Instant.now();
                    if (Duration.between(lastAckedTime, instantNow).toMillis() > ackIntervalMs) {
                        getChannel().basicAck(deliveryTag, true);
                        delTagLastAcked = deliveryTag;
                        lastAckedTime = instantNow;
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
