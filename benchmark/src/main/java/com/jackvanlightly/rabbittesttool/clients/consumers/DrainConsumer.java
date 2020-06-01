package com.jackvanlightly.rabbittesttool.clients.consumers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.topology.Broker;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.TopologyException;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.micrometer.core.instrument.util.NamedThreadFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class DrainConsumer {
    BenchmarkLogger logger;
    String consumerId;
    ConnectionSettings connectionSettings;
    ConnectionFactory factory;
    QueueHosts queueHosts;
    AtomicBoolean isCancelled;
    DrainEventingConsumer eventingConsumer;
    Broker currentHost;
    boolean drainComplete;

    public DrainConsumer(String consumerId,
                         ConnectionSettings connectionSettings,
                         QueueHosts queueHosts,
                         AtomicBoolean isCancelled) {
        this.logger = new BenchmarkLogger("DRAINCONSUMER");
        this.consumerId = consumerId;
        this.connectionSettings = connectionSettings;
        this.queueHosts = queueHosts;
        this.isCancelled = isCancelled;
    }

    public void drain(String queueName, int thresholdSeconds) {
        while(!isCancelled.get() && !drainComplete) {
            try {
                Connection connection = null;
                try {
                    connection = getConnection(queueName);
                    if(connection.isOpen()) {
                        logger.info("Consumer " + consumerId + " opened connection");
                        startChannel(connection, queueName, thresholdSeconds);
                    }
                }
                finally {
                    if (connection != null && connection.isOpen()) {
                        connection.close(AMQP.REPLY_SUCCESS, "Closed by RabbitTestTool", 3000);
                        logger.info("Consumer " + consumerId + " closed connection");
                    }
                }
            } catch (Exception e) {
                logger.error("Consumer " + consumerId + " has failed unexpectedly", e);
            }

            if(!isCancelled.get() && !drainComplete) {
                recreateConsumerExecutor();
            }
        }


    }

    private void recreateConsumerExecutor() {
        logger.info("Consumer " + consumerId + " will restart in 5 seconds");
        ClientUtils.waitFor(5000, isCancelled);
    }

    private void startChannel(Connection connection, String queueName, int thresholdSeconds) throws IOException, TimeoutException {
        Channel channel = connection.createChannel();
        logger.info("Consumer " + consumerId + " opened channel");
        try {
            channel.confirmSelect();
            channel.basicQos(1000);

            eventingConsumer = new DrainEventingConsumer(channel);

            String consumerTag = channel.basicConsume(queueName, false, eventingConsumer);
            logger.info("Consumer " + consumerId + " consuming with tag: " + consumerTag + " from " + currentHost.getNodeName());

            while (!isCancelled.get() && channel.isOpen() && !eventingConsumer.isConsumerCancelled()) {
                ClientUtils.waitFor(1000, this.isCancelled);

                // detect when queue has been drained
                if(eventingConsumer.getConsumeCount() > 0
                        && eventingConsumer.durationSinceLastConsume().getSeconds() > thresholdSeconds) {
                    drainComplete = true;
                    logger.info("Drain complete for queue: " + queueName);
                    break;
                }

                if(reconnectToNewHost(queueName)) {
                    break;
                }
            }
        }
        catch(Exception e) {
            logger.error("Failed setting up a consumer", e);
            throw e;
        }
        finally {
            if(channel.isOpen()) {
                try {
                    channel.close();
                    logger.info("Consumer " + consumerId + " closed channel to " + currentHost.getNodeName());
                }
                catch(Exception e) {
                    logger.error("Consumer " + consumerId + " could not close channel to " + currentHost.getNodeName(), e);
                }
            }

            ClientUtils.waitFor(1000, isCancelled);
        }
    }

    private Connection getConnection(String queueName) throws IOException, TimeoutException {
        factory = new ConnectionFactory();
        factory.setUsername(connectionSettings.getUser());
        factory.setPassword(connectionSettings.getPassword());
        factory.setVirtualHost(connectionSettings.getVhost());

        Broker host = getBrokerToConnectTo(queueName);
        factory.setHost(host.getIp());
        factory.setPort(Integer.valueOf(host.getPort()));
        currentHost = host;

        factory.setConnectionTimeout(5000);
        factory.setAutomaticRecoveryEnabled(false);
        factory.setRequestedHeartbeat(10);
        //factory.setSharedExecutor(this.executorService);
        factory.setThreadFactory(new NamedThreadFactory("DrainConsumerConnection-" + consumerId));

        return factory.newConnection();
    }

    private Broker getBrokerToConnectTo(String queueName) {
        while(!isCancelled.get()) {
            Broker host = queueHosts.getHost(queueName);

            if(host != null) {
                return host;
            }
            else {
                ClientUtils.waitFor(1000, isCancelled);
            }
        }

        throw new TopologyException("Could not identify a broker to connect to");
    }

    private boolean reconnectToNewHost(String queueName) {
        if(isCancelled.get())
            return false;

        Broker host = getBrokerToConnectTo(queueName);
        if (!host.getNodeName().equals(currentHost.getNodeName())) {
            logger.info("Detected change of queue host. No longer: " + currentHost.getNodeName() + " now: " + host.getNodeName());
            return true;
        }

        return false;
    }

}
