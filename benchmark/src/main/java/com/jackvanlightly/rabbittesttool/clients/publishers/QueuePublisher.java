package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.*;
import com.jackvanlightly.rabbittesttool.topology.Broker;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.TopologyException;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.DeliveryMode;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.QueueGroupMode;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.SendToMode;
import com.rabbitmq.client.*;
import io.micrometer.core.instrument.util.NamedThreadFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueuePublisher implements ConfirmListener, ReturnListener, BlockedListener {
    BenchmarkLogger logger;
    String publisherId;
    ConnectionSettings connectionSettings;
    ConnectionFactory factory;
    MessageGenerator messageGenerator;
    AtomicBoolean isCancelled;
    QueueHosts queueHosts;
    ConcurrentNavigableMap<Long, MessagePayload> pendingConfirms;
    Semaphore inflightSemaphore;

    // for publishing rate
    private int sentInPeriod;
    private int limitInPeriod;
    private int periodNs;

    public QueuePublisher(String publisherId,
                          ConnectionSettings connectionSettings,
                          QueueHosts queueHosts,
                          AtomicBoolean isCancelled) {
        this.publisherId = publisherId;
        this.connectionSettings = connectionSettings;
        this.queueHosts = queueHosts;
        this.logger = new BenchmarkLogger("QUEUEPUBLISHER");
        this.isCancelled = isCancelled;
    }

    public void fill(String queueName, int messageSize, int messageCount, int targetRate) {
        int sentCount = 0;
        messageGenerator = new MessageGenerator();
        messageGenerator.setBaseMessageSize(messageSize);

        while(!isCancelled.get()) {
            Connection connection = null;
            Channel channel = null;
            try {
                connection = getConnection(queueName);
                channel = connection.createChannel();
                pendingConfirms = new ConcurrentSkipListMap<>();
                inflightSemaphore = new Semaphore(1000);

                logger.info("Queue publisher " + publisherId + " opened channel");

                channel.confirmSelect();
                channel.addConfirmListener(this);

                long periodStartNs = System.nanoTime();
                boolean rateLimit = targetRate > 0;

                if(rateLimit)
                    configureRateLimit(targetRate);

                while (!isCancelled.get()) {
                    inflightSemaphore.acquire();

                    publish(channel, queueName);
                    sentCount++;

                    if(sentCount >= messageCount)
                        break;

                    if (rateLimit) {
                        this.sentInPeriod++;
                        long now = System.nanoTime();
                        long elapsedNs = now - periodStartNs;

                        if (this.sentInPeriod >= this.limitInPeriod) {
                            long waitNs = this.periodNs - elapsedNs;
                            if (waitNs > 0)
                                waitFor((int) (waitNs / 999000));

                            // may need to adjust for drift over time
                            periodStartNs = System.nanoTime();
                            this.sentInPeriod = 0;
                        } else if (now - periodStartNs > this.periodNs) {
                            periodStartNs = now;
                            this.sentInPeriod = 0;
                        }
                    }
                }

                logger.info("Queue publisher " + publisherId + " stopping");

                tryClose(channel);
                tryClose(connection);
            } catch (Exception e) {
                logger.error("Queue publisher" + publisherId + " failed", e);
                tryClose(channel);
                tryClose(connection);
                waitFor(5000);
            }

            if(sentCount >= messageCount)
                break;

            if(!isCancelled.get()) {
                logger.info("Queue publisher" + publisherId + " restarting to complete publish operation");
                waitFor(1000);
            }
        }

        logger.info("Publisher " + publisherId + " completed initial send");
    }

    private void configureRateLimit(int targetRate) {
        int measurementPeriodMs = 1000 / targetRate;

        if (measurementPeriodMs >= 10) {
            this.limitInPeriod = 1;
        } else {
            measurementPeriodMs = 10;
            int periodsPerSecond = 1000 / measurementPeriodMs;
            this.limitInPeriod = targetRate / periodsPerSecond;
        }

        this.periodNs = measurementPeriodMs * 1000000;
    }

    private void publish(Channel channel, String queueName) throws IOException {
        long seqNo = channel.getNextPublishSeqNo();
        long timestamp = MessageUtils.getTimestamp();

        MessagePayload mp = new MessagePayload(1, 1, timestamp);
        AMQP.BasicProperties messageProperties = getProperties();

        pendingConfirms.put(seqNo, mp);
        byte[] body = messageGenerator.getMessageBytes(mp);

        channel.basicPublish("",
                queueName,
                true, false,
                messageProperties,
                body);
    }

    private AMQP.BasicProperties getProperties() {
        AMQP.BasicProperties.Builder propertiesBuilder = new AMQP.BasicProperties.Builder();
        propertiesBuilder.deliveryMode(2);
        return propertiesBuilder.build();
    }

    private Connection getConnection(String queueName) throws IOException, TimeoutException {
        factory = new ConnectionFactory();
        factory.setUsername(connectionSettings.getUser());
        factory.setPassword(connectionSettings.getPassword());
        factory.setVirtualHost(connectionSettings.getVhost());

        Broker host = getBrokerToConnectTo(queueName);
        factory.setHost(host.getIp());
        factory.setPort(Integer.valueOf(host.getPort()));

        factory.setConnectionTimeout(5000);
        factory.setAutomaticRecoveryEnabled(false);
        factory.setRequestedHeartbeat(10);
        factory.setThreadFactory(new NamedThreadFactory("QueuePublisherConnection-" + publisherId));

        return factory.newConnection();
    }

    private Broker getBrokerToConnectTo(String queueName) {
        while(!isCancelled.get()) {
            Broker host = queueHosts.getHost(connectionSettings.getVhost(), queueName);

            if(host != null) {
                return host;
            }
            else {
                ClientUtils.waitFor(1000, isCancelled);
            }
        }

        throw new TopologyException("Could not identify a broker to connect to");
    }

    @Override
    public void handleBlocked(String s) throws IOException {

    }

    @Override
    public void handleUnblocked() throws IOException {

    }

    @Override
    public void handleAck(long seqNo, boolean multiple) throws IOException {
        int numConfirms = 0;
        if (multiple) {
            ConcurrentNavigableMap<Long, MessagePayload> confirmed = pendingConfirms.headMap(seqNo, true);
            numConfirms = confirmed.size();
            confirmed.clear();
        } else {
            MessagePayload mp = pendingConfirms.remove(seqNo);
            numConfirms = mp != null ? 1 : 0;
        }

        if(numConfirms > 0) {
            inflightSemaphore.release(numConfirms);
        }
    }

    @Override
    public void handleNack(long seqNo, boolean multiple) {
        int numConfirms = 0;
        if (multiple) {
            ConcurrentNavigableMap<Long, MessagePayload> confirmed = pendingConfirms.headMap(seqNo, true);
            numConfirms = confirmed.size();
            confirmed.clear();
        } else {
            MessagePayload mp = pendingConfirms.remove(seqNo);
            numConfirms = mp != null ? 1 : 0;
        }

        if(numConfirms > 0) {
            inflightSemaphore.release(numConfirms);
        }
    }

    @Override
    public void handleReturn(int i, String s, String s1, String s2, AMQP.BasicProperties basicProperties, byte[] bytes) throws IOException {

    }

    private void tryClose(Channel channel) {
        try {
            if (channel.isOpen())
                channel.close();
        }
        catch(Exception e){}
    }

    private void tryClose(Connection connection) {
        try {
            if (connection.isOpen())
                connection.close();
        }
        catch(Exception e){}
    }

    private void waitFor(int milliseconds) {
        try
        {
            Thread.sleep(milliseconds);
        }
        catch(InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }
}
