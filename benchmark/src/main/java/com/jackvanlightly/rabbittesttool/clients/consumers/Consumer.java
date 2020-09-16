package com.jackvanlightly.rabbittesttool.clients.consumers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.*;
import com.jackvanlightly.rabbittesttool.statistics.MetricGroup;
import com.jackvanlightly.rabbittesttool.statistics.MetricType;
import com.jackvanlightly.rabbittesttool.topology.Broker;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.topology.TopologyException;
import com.rabbitmq.client.*;
import io.micrometer.core.instrument.util.NamedThreadFactory;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Consumer implements Runnable  {
    BenchmarkLogger logger;
    String consumerId;
    ConnectionSettings connectionSettings;
    ConnectionFactory factory;
    QueueHosts queueHosts;
    AtomicBoolean isCancelled;
    Integer step;
    MetricGroup metricGroup;
    MessageModel messageModel;
    ConsumerSettings consumerSettings;
    EventingConsumer eventingConsumer;
    Broker currentHost;
    ExecutorService consumerExecutorService;

    public Consumer(String consumerId,
                    ConnectionSettings connectionSettings,
                    QueueHosts queueHosts,
                    ConsumerSettings consumerSettings,
                    MetricGroup metricGroup,
                    MessageModel messageModel,
                    ExecutorService consumerExecutorService) {
        this.logger = new BenchmarkLogger("CONSUMER");
        this.isCancelled = new AtomicBoolean();
        this.consumerId = consumerId;
        this.connectionSettings = connectionSettings;
        this.queueHosts = queueHosts;
        this.metricGroup = metricGroup;
        this.messageModel = messageModel;
        this.consumerExecutorService = consumerExecutorService;
        this.consumerSettings = consumerSettings;
        this.step = 0;
    }

    public void signalStop() {
        isCancelled.set(true);
    }

    public void setAckInterval(int ackInterval) {
        this.consumerSettings.getAckMode().setAckInterval(ackInterval);
        if(this.eventingConsumer != null)
            this.eventingConsumer.setAckInterval(ackInterval);
    }

    public void setAckIntervalMs(int ackIntervalMs) {
        this.consumerSettings.getAckMode().setAckIntervalMs(ackIntervalMs);
        if(this.eventingConsumer != null)
            this.eventingConsumer.setAckIntervalMs(ackIntervalMs);
    }

    public void setPrefetch(short prefetch) {
        this.consumerSettings.getAckMode().setConsumerPrefetch(prefetch);
        // we do not update the consumer as a new channel is required
    }

    public void setProcessingMs(int processingMs) {
        this.consumerSettings.setProcessingMs(processingMs);
        if(this.eventingConsumer != null)
            this.eventingConsumer.setProcessingMs(processingMs);
    }

    public void triggerNewChannel() {
        step++;
    }

    public long getRecordedReceiveCount() {
        return metricGroup.getRecordedDeltaScalarValueForStepStats(MetricType.ConsumerReceivedMessage);
    }

    public long getRealReceiveCount() {
        return metricGroup.getRealDeltaScalarValueForStepStats(MetricType.ConsumerReceivedMessage);
    }

    public MetricGroup getMetricGroup() {
        return metricGroup;
    }

    @Override
    public void run() {
        while(!isCancelled.get()) {
            try {
                Connection connection = null;
                try {
                    connection = getConnection();
                    if (connection.isOpen()) {
                        messageModel.clientConnected(consumerId);
                        logger.info("Consumer " + consumerId + " opened connection");

                        ConsumerExitReason exitReason = ConsumerExitReason.None;
                        while (!isCancelled.get() && exitReason != ConsumerExitReason.ConnectionFailed) {
                            int currentStep = step;
                            exitReason = startChannel(connection, currentStep);
                        }
                    }
                } finally {
                    tryClose(connection);
                }
            } catch(ClientCancelledException e) {
                // do nothing
                messageModel.clientDisconnected(consumerId, isCancelled.get());
            } catch (TimeoutException | IOException e) {
                if(!isCancelled.get())
                    metricGroup.increment(MetricType.ConsumerConnectionErrors);
                logger.error("Consumer " + consumerId + " connection failed in step " + step);
                messageModel.clientDisconnected(consumerId, isCancelled.get());
            } catch (Exception e) {
                if(!isCancelled.get())
                    metricGroup.increment(MetricType.ConsumerConnectionErrors);
                logger.error("Consumer " + consumerId + " has failed unexpectedly in step " + step, e);
                messageModel.clientDisconnected(consumerId, isCancelled.get());
            }

            if(!isCancelled.get()) {
                recreateWait();
            }
        }
    }

    private void tryClose(Connection connection) {
        try {
            messageModel.clientDisconnected(consumerId, isCancelled.get());
            if (connection != null && connection.isOpen()) {
                connection.close(AMQP.REPLY_SUCCESS, "Closed by RabbitTestTool", 3000);
            }
        }
        catch(Exception e){}
    }

    private void recreateWait() {
        logger.info("Consumer " + consumerId + " will restart in 5 seconds");
        ClientUtils.waitFor(5000, isCancelled);
    }

    private ConsumerExitReason startChannel(Connection connection, Integer currentStep) throws IOException, TimeoutException {
        ConsumerExitReason exitReason = ConsumerExitReason.None;
        Channel channel = connection.createChannel();
        logger.info("Consumer " + consumerId + " opened channel");
        try {
            boolean autoAck = !consumerSettings.getAckMode().isManualAcks();

            if (consumerSettings.getAckMode().getConsumerPrefetch() > 0) {
                channel.basicQos(consumerSettings.getAckMode().getConsumerPrefetch(),
                        consumerSettings.getAckMode().isGlobalPrefetch());
            }

            eventingConsumer = new EventingConsumer(consumerId,
                    connectionSettings.getVhost(),
                    consumerSettings.getQueue(),
                    channel,
                    metricGroup,
                    messageModel,
                    consumerSettings.getAckMode().getConsumerPrefetch(),
                    consumerSettings.getAckMode().getAckInterval(),
                    consumerSettings.getAckMode().getAckIntervalMs(),
                    consumerSettings.getProcessingMs(),
                    consumerSettings.getAckMode().getRequeueEveryNth());

            String consumerTag = channel.basicConsume(consumerSettings.getQueue(), autoAck, eventingConsumer);
            logger.info("Consumer " + consumerId + " consuming queue " + consumerSettings.getQueue() + " with tag: " + consumerTag + " from " + currentHost.getNodeName());

            while (!isCancelled.get() && currentStep.equals(step) && channel.isOpen() && !eventingConsumer.isConsumerCancelled()) {
                if(reconnectToNewHost()) {
                    exitReason = ConsumerExitReason.Cancelled;
                    break;
                }

                if(consumerSettings.getAckMode().isManualAcks())
                    eventingConsumer.ensureAckTimeLimitEnforced();

                ClientUtils.waitFor(1000, this.isCancelled);
            }

            if(isCancelled.get() && consumerSettings.getAckMode().isManualAcks())
                eventingConsumer.tryAcknowledgeRemaining();

            if(exitReason == ConsumerExitReason.None) {
                if (isCancelled.get())
                    exitReason = ConsumerExitReason.Cancelled;
                else if (!currentStep.equals(step))
                    exitReason = ConsumerExitReason.NextStep;
                else
                    exitReason = ConsumerExitReason.ConnectionFailed;
            }
        }
        catch(ClientCancelledException e) {
            // do nothing
        }
        catch(Exception e) {
            logger.error("Failed setting up a consumer", e);
            throw e;
        }
        finally {
            if(channel.isOpen()) {
                try {
                    channel.close();
                    logger.info("Consumer " + consumerId + " closed channel to " + currentHost.getNodeName() + " with exit reason " + exitReason);
                }
                catch(Exception e) {
                    logger.error("Consumer " + consumerId + " could not close channel to " + currentHost.getNodeName() + " with exit reason " + exitReason, e);
                }
            }
            else {
                exitReason = ConsumerExitReason.ConnectionFailed;
            }

            ClientUtils.waitFor(1000, isCancelled);

            return exitReason;
        }
    }

    private Connection getConnection() throws IOException, TimeoutException {
        factory = new ConnectionFactory();
        factory.setUsername(connectionSettings.getUser());
        factory.setPassword(connectionSettings.getPassword());
        factory.setVirtualHost(connectionSettings.getVhost());

        Broker host = getBrokerToConnectTo();
        factory.setHost(host.getIp());
        factory.setPort(Integer.valueOf(host.getPort()));
        currentHost = host;

        factory.setConnectionTimeout(5000);
        factory.setAutomaticRecoveryEnabled(false);

        if(consumerSettings.getFrameMax() > 0)
            factory.setRequestedFrameMax(consumerSettings.getFrameMax());

        factory.setRequestedHeartbeat(connectionSettings.getConsumerHeartbeatSeconds());
        factory.setSharedExecutor(consumerExecutorService);
        factory.setShutdownExecutor(consumerExecutorService);
        factory.setThreadFactory(new NamedThreadFactory("ConsumerConnection-" + consumerId));

        if(!connectionSettings.isNoTcpDelay())
            factory.setSocketConfigurator(new WithNagleSocketConfigurator());


        return factory.newConnection();
    }

    private Broker getBrokerToConnectTo() {
        while(!isCancelled.get()) {
            Broker host = null;
            if (connectionSettings.getConsumerConnectToNode().equals(ConnectToNode.RoundRobin))
                host = queueHosts.getHostRoundRobin();
            else if (connectionSettings.getConsumerConnectToNode().equals(ConnectToNode.Random))
                host = queueHosts.getRandomHost();
            else if (connectionSettings.getConsumerConnectToNode().equals(ConnectToNode.Local))
                host = queueHosts.getHost(consumerSettings.getQueue());
            else if (connectionSettings.getConsumerConnectToNode().equals(ConnectToNode.NonLocal))
                host = queueHosts.getRandomOtherHost(consumerSettings.getQueue());
            else
                throw new TopologyException("ConnectToNode value not supported: " + connectionSettings.getConsumerConnectToNode());

            if(host != null)
                return host;
            else
                ClientUtils.waitFor(1000, isCancelled);
        }

        if(!isCancelled.get())
            throw new TopologyException("Could not identify a broker to connect to");
        else
            throw new ClientCancelledException("Consumer " + consumerId + " has been cancelled");
    }

    private boolean reconnectToNewHost() {
        if(connectionSettings.getConsumerConnectToNode().equals(ConnectToNode.Local)) {
            Broker host = getBrokerToConnectTo();
            if (!host.getNodeName().equals(currentHost.getNodeName())) {
                logger.info("Detected change of queue host. No longer: " + currentHost.getNodeName() + " now: " + host.getNodeName());
                return true;
            }
        }
        else if(connectionSettings.getConsumerConnectToNode().equals(ConnectToNode.NonLocal)) {
            if(queueHosts.isQueueHost(consumerSettings.getQueue(), currentHost)) {
                logger.info("Detected change of queue host. Now connected to the queue host in non-local mode! " + currentHost.getNodeName() +   " hosts the queue");
                return true;
            }
        }

        return false;
    }

}
