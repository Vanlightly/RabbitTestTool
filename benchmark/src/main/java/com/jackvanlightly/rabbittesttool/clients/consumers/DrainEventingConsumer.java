package com.jackvanlightly.rabbittesttool.clients.consumers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import com.jackvanlightly.rabbittesttool.clients.MessageUtils;
import com.jackvanlightly.rabbittesttool.clients.consumers.ConsumerStats;
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

public class DrainEventingConsumer extends DefaultConsumer {

    BenchmarkLogger logger;
    AtomicBoolean consumerCancelled;
    Instant lastConsumeTime;
    int consumeCount;

    public DrainEventingConsumer(Channel channel) {
        super(channel);
        this.logger = new BenchmarkLogger("DRAINCONSUMER");
        consumerCancelled = new AtomicBoolean();
        lastConsumeTime = Instant.now();
        consumeCount = 0;
    }

    @Override
    public void handleDelivery(String consumerTag,
                               Envelope envelope,
                               BasicProperties properties,
                               byte[] body) throws IOException {
        try {
            getChannel().basicAck(envelope.getDeliveryTag(), false);
            lastConsumeTime = Instant.now();
            consumeCount++;
        }
        catch(AlreadyClosedException e) {
            logger.info("Drain consumer could not ack message as connection was lost");
        }
    }

    @Override
    public void handleCancel(String consumerTag) {
        logger.info("Drain consumer cancelled with tag: " + consumerTag);
        consumerCancelled.set(true);
    }

    @Override
    public void handleRecoverOk(String consumerTag) {
        logger.info("Drain consumer recovered with tag: " + consumerTag);
    }

    @Override
    public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
        logger.info("Drain consumer shutdown with tag: " + consumerTag + " for reason: " + sig.getMessage());
    }

    public boolean isConsumerCancelled() {
        return consumerCancelled.get();
    }

    public Duration durationSinceLastConsume() {
        return Duration.between(lastConsumeTime, Instant.now());
    }

    public int getConsumeCount() {
        return consumeCount;
    }
}
