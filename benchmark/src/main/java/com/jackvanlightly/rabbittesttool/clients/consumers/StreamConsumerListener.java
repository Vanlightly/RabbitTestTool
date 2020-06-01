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
import com.rabbitmq.stream.Client;
import com.rabbitmq.stream.Message;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class StreamConsumerListener implements Client.ChunkListener, Client.MessageListener {

    BenchmarkLogger logger;
    String consumerId;
    String vhost;
    String queue;
    Stats stats;
    MessageModel messageModel;
    volatile int ackInterval;
    volatile int ackIntervalMs;
    volatile int prefetch;
    volatile int processingMs;
    volatile int pendingCreditCounter;
    volatile Instant lastGrantedCreditTime;
    ConsumerStats consumerStats;
    AtomicLong lastOffset;

    public StreamConsumerListener(String consumerId,
                                  String vhost,
                                  String queue,
                                  Stats stats,
                                  MessageModel messageModel,
                                  ConsumerStats consumerStats,
                                  AtomicLong lastOffset,
                                  int prefetch,
                                  int ackInterval,
                                  int ackIntervalMs,
                                  int processingMs) {
        this.logger = new BenchmarkLogger("STREAM CONSUMER");
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
    public void handle(int subscriptionId, long offset, Message message) {
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
        long lag = MessageUtils.getLag(mp.getTimestamp());
        //long seqNO = mp.getSequenceNumber();
        messageModel.received(new ReceivedMessage(consumerId, vhost, queue, mp, false, lag, System.currentTimeMillis()));

        stats.handleRecv(lag, message.getBodyAsBinary().length, 0, prefetch, ackInterval, ackIntervalMs, true);
        consumerStats.incrementReceivedCount();
    }

    @Override
    public void handle(Client client, int subscriptionId, long offset, long messageCount, long dataSize) {
        stats.handleRecvBatch();

        if(this.ackInterval == 1) {
            client.credit(subscriptionId, 1);
        }
        else {
            pendingCreditCounter++;
            if(pendingCreditCounter >= ackInterval && pendingCreditCounter % ackInterval == 0) {
                client.credit(subscriptionId, pendingCreditCounter);
                pendingCreditCounter = 0;
                lastGrantedCreditTime = Instant.now();
            }
            else if(Duration.between(lastGrantedCreditTime, Instant.now()).toMillis() > ackIntervalMs) {
                client.credit(subscriptionId, pendingCreditCounter);
                pendingCreditCounter = 0;
                lastGrantedCreditTime = Instant.now();
            }
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
