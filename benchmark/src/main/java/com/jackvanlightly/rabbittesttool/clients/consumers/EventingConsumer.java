package com.jackvanlightly.rabbittesttool.clients.consumers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import com.jackvanlightly.rabbittesttool.clients.MessageUtils;
import com.jackvanlightly.rabbittesttool.clients.publishers.MessageGenerator;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.model.ReceivedMessage;
import com.jackvanlightly.rabbittesttool.statistics.Stats;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class EventingConsumer extends DefaultConsumer {

    private BenchmarkLogger logger;
    private String consumerId;
    private String vhost;
    private String queue;
    private Stats stats;
    private MessageModel messageModel;
    private volatile int ackInterval;
    private volatile int ackIntervalMs;
    private volatile int prefetch;
    private volatile int processingMs;
    private volatile long delTagLastAcked;
    private volatile long delTagLastReceived;
    private volatile Instant lastAckedTime;
    private ConsumerStats consumerStats;
    private AtomicBoolean consumerCancelled;
    private Lock ackLock;

    public EventingConsumer(String consumerId,
                            String vhost,
                            String queue,
                            Channel channel,
                            Stats stats,
                            MessageModel messageModel,
                            ConsumerStats consumerStats,
                            int prefetch,
                            int ackInterval,
                            int ackIntervalMs,
                            int processingMs) {
        super(channel);
        this.logger = new BenchmarkLogger("CONSUMER");
        this.consumerId = consumerId;
        this.vhost = vhost;
        this.queue = queue;
        this.stats = stats;
        this.messageModel = messageModel;
        this.ackInterval = ackInterval;
        this.ackIntervalMs = ackIntervalMs;
        this.prefetch = prefetch;
        this.processingMs = processingMs;
        this.consumerStats = consumerStats;

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
                    stats.handleAck((int) msgsToAck);
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
        MessagePayload mp = MessageGenerator.toMessagePayload(body);
        long lag = MessageUtils.getLag(mp.getTimestamp());
        messageModel.received(new ReceivedMessage(consumerId, vhost, queue, mp, envelope.isRedeliver(), lag, System.currentTimeMillis()));

        int headerCount = 0;
        if(properties != null && properties.getHeaders() != null)
            headerCount = properties.getHeaders().size();
        stats.handleRecv(lag, body.length, headerCount, prefetch, ackInterval, ackIntervalMs, false);
        consumerStats.incrementReceivedCount();

        ackLock.lock();

        try {
            long deliveryTag = envelope.getDeliveryTag();
            if (ackInterval == 1) {
                getChannel().basicAck(deliveryTag, false);
                delTagLastAcked = deliveryTag;
                stats.handleAck(1);
            } else if (ackInterval > 1) {
                long msgsToAck = deliveryTag - delTagLastAcked;
                if (msgsToAck > ackInterval || msgsToAck >= Short.MAX_VALUE-1) {
                    getChannel().basicAck(deliveryTag, true);
                    delTagLastAcked = deliveryTag;
                    stats.handleAck((int) msgsToAck);
                } else {
                    Instant now = Instant.now();
                    if (Duration.between(lastAckedTime, now).toMillis() > ackIntervalMs) {
                        getChannel().basicAck(deliveryTag, true);
                        delTagLastAcked = deliveryTag;
                        lastAckedTime = now;
                        stats.handleAck((int) msgsToAck);
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
            this.stats.handleConnectionError();
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
