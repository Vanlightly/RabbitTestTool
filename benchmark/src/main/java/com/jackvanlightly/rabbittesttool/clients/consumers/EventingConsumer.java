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

public class EventingConsumer extends DefaultConsumer {

    private BenchmarkLogger logger;
    private String consumerId;
    private String vhost;
    private String queue;
    private Stats stats;
    private MessageModel messageModel;
    private int ackInterval;
    private long delTagLastAcked;
    private long delTagLastReceived;
    private Instant lastAckedTime;
    private int prefetch;
    private int processingMs;
    private ConsumerStats consumerStats;
    private AtomicBoolean consumerCancelled;

    public EventingConsumer(String consumerId,
                            String vhost,
                            String queue,
                            Channel channel,
                            Stats stats,
                            MessageModel messageModel,
                            ConsumerStats consumerStats,
                            int prefetch,
                            int ackInterval,
                            int processingMs) {
        super(channel);
        this.logger = new BenchmarkLogger("CONSUMER");
        this.consumerId = consumerId;
        this.vhost = vhost;
        this.queue = queue;
        this.stats = stats;
        this.messageModel = messageModel;
        this.ackInterval = ackInterval;
        this.prefetch = prefetch;
        this.processingMs = processingMs;
        this.consumerStats = consumerStats;

        consumerCancelled = new AtomicBoolean();
        delTagLastAcked = -1;
        lastAckedTime = Instant.now();
    }

    public void setProcessingMs(int processingMs) {
        this.processingMs = processingMs;
    }

    public void tryAcknowledgeRemaining() {
        if(delTagLastReceived > delTagLastAcked)
        {
            if(this.getChannel().isOpen()) {
                try {
                    getChannel().basicAck(delTagLastReceived, true);
                }
                catch(IOException e) {
                    logger.warn("Failed to ack on shutdown", e);
                }
            }
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
        stats.handleRecv(lag, body.length, headerCount, prefetch, ackInterval);
        consumerStats.incrementReceivedCount();

        long deliveryTag = envelope.getDeliveryTag();
        if(ackInterval == 1) {
            getChannel().basicAck(deliveryTag, false);
            delTagLastAcked = deliveryTag;
        } else if (ackInterval > 1) {
            if(deliveryTag - delTagLastAcked > ackInterval) {
                getChannel().basicAck(deliveryTag, true);
                delTagLastAcked = deliveryTag;
            }
            else {
                Instant now = Instant.now();
                if(Duration.between(lastAckedTime, now).toMillis() > 1000) {
                    getChannel().basicAck(deliveryTag, true);
                    delTagLastAcked = deliveryTag;
                    lastAckedTime = now;
                }
            }
        }

        delTagLastReceived = deliveryTag;
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
