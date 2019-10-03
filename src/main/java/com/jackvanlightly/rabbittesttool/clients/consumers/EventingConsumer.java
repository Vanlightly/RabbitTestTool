package com.jackvanlightly.rabbittesttool.clients.consumers;

import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import com.jackvanlightly.rabbittesttool.clients.MessageUtils;
import com.jackvanlightly.rabbittesttool.clients.publishers.MessageGenerator;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.model.ReceivedMessage;
import com.jackvanlightly.rabbittesttool.statistics.Stats;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class EventingConsumer extends DefaultConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventingConsumer.class);
    private Stats stats;
    private MessageModel messageModel;
    private int ackInterval;
    private long lastAcked;
    private int prefetch;
    private int processingMs;
    private ConsumerStats consumerStats;

    public EventingConsumer(Channel channel,
                            Stats stats,
                            MessageModel messageModel,
                            ConsumerStats consumerStats,
                            int prefetch,
                            int ackInterval,
                            int processingMs) {
        super(channel);
        this.stats = stats;
        this.messageModel = messageModel;
        this.ackInterval = ackInterval;
        this.prefetch = prefetch;
        this.processingMs = processingMs;
        this.consumerStats = consumerStats;

        lastAcked = -1;
    }

    public void setProcessingMs(int processingMs) {
        this.processingMs = processingMs;
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
            LOGGER.info("Could not ack message as connection was lost");
            lastAcked = -1;
        }
    }

    void handleMessage(Envelope envelope, BasicProperties properties, byte[] body, Channel ch) throws IOException {
        MessagePayload mp = MessageGenerator.toMessagePayload(body);
        long lag = MessageUtils.getLag(mp.getTimestamp());
        messageModel.received(new ReceivedMessage(mp, envelope.isRedeliver(), lag));

        int headerCount = 0;
        if(properties != null && properties.getHeaders() != null)
            headerCount = properties.getHeaders().size();
        stats.handleRecv(lag, body.length, headerCount, prefetch, ackInterval);
        consumerStats.incrementReceivedCount();

        long deliveryTag = envelope.getDeliveryTag();
        if(ackInterval == 1) {
            getChannel().basicAck(deliveryTag, false);
            lastAcked = deliveryTag;
        } else if (ackInterval > 1) {
            if(deliveryTag - lastAcked > ackInterval) {
                getChannel().basicAck(deliveryTag, true);
                lastAcked = deliveryTag;
            }
        }
    }

    @Override
    public void handleCancel(String consumerTag) throws IOException {
        LOGGER.info("Consumer cancelled with tag: " + consumerTag);
    }

    @Override
    public void handleRecoverOk(String consumerTag) {
        LOGGER.info("Consumer recovered with tag: " + consumerTag);
    }

    @Override
    public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
        if(sig.isHardError() && !sig.isInitiatedByApplication())
            this.stats.handleConnectionError();
        LOGGER.info("Consumer shutdown with tag: " + consumerTag + " for reason: " + sig.getMessage());
    }

    private void waitFor(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
