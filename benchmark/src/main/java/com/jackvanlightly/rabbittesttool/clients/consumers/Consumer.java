package com.jackvanlightly.rabbittesttool.clients.consumers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.ConnectToNode;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.topology.Broker;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.clients.WithNagleSocketConfigurator;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.Stats;
import com.jackvanlightly.rabbittesttool.topology.TopologyException;
import com.rabbitmq.client.*;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Consumer implements Runnable  {
    private BenchmarkLogger logger;
    private String consumerId;
    private ConnectionSettings connectionSettings;
    private ConnectionFactory factory;
    private QueueHosts queueHosts;
    //private ExecutorService executorService;
    private AtomicBoolean isCancelled;
    private Integer step;
    private Stats stats;
    private MessageModel messageModel;
    private ConsumerSettings consumerSettings;
    private EventingConsumer eventingConsumer;
    private ConsumerStats consumerStats;
    private Broker currentHost;

    public Consumer(String consumerId,
                    ConnectionSettings connectionSettings,
                    QueueHosts queueHosts,
                    ConsumerSettings consumerSettings,
                    Stats stats,
                    MessageModel messageModel) {
        this.logger = new BenchmarkLogger("CONSUMER");
        this.isCancelled = new AtomicBoolean();
        this.consumerId = consumerId;
        this.connectionSettings = connectionSettings;
        this.queueHosts = queueHosts;
        this.isCancelled = isCancelled;
        this.stats = stats;
        this.messageModel = messageModel;
        this.consumerSettings = consumerSettings;
        this.step = 0;
        //this.executorService = Executors.newFixedThreadPool(1, new NamedThreadFactory("Consumer-" + consumerId));
        this.consumerStats = new ConsumerStats();
    }

    public void signalStop() {
        isCancelled.set(true);
        //this.executorService.shutdown();
    }

    public void setAckInterval(int ackInterval) {
        this.consumerSettings.getAckMode().setAckInterval(ackInterval);
    }

    public void setPrefetch(int prefetch) {
        this.consumerSettings.getAckMode().setConsumerPrefetch(prefetch);
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
        return consumerStats.getAndResetRecordedReceived();
    }

    public long getRealReceiveCount() {
        return consumerStats.getAndResetRealReceived();
    }

    @Override
    public void run() {
        while(!isCancelled.get()) {
            try {
                Connection connection = null;
                try {
                    connection = getConnection();
                    if(connection.isOpen()) {
                        logger.info("Consumer " + consumerId + " opened connection");

                        int exitReason = 0;
                        while (!isCancelled.get() && exitReason != 3) {
                            int currentStep = step;
                            exitReason = startChannel(connection, currentStep);
                        }
                    }
                }
                finally {
                    if (connection != null && connection.isOpen()) {
                        connection.close(AMQP.REPLY_SUCCESS, "Closed by RabbitTestTool", 3000);
                        logger.info("Consumer " + consumerId + " closed connection");
                    }
                }
            } catch (IOException e) {
                if(!isCancelled.get())
                    stats.handleConnectionError();
                logger.error("Consumer " + consumerId + " has failed in step " + step, e);
            } catch (TimeoutException e) {
                if(!isCancelled.get())
                    stats.handleConnectionError();
                logger.error("Consumer " + consumerId + " failed to connect in step " + step, e);
            } catch (Exception e) {
                if(!isCancelled.get())
                    stats.handleConnectionError();
                logger.error("Consumer " + consumerId + " has failed unexpectedly in step " + step, e);
            }

            if(!isCancelled.get()) {
                recreateConsumerExecutor();
            }
        }
    }

    private void recreateConsumerExecutor() {
//        try {
////            this.executorService.shutdown();
////            this.executorService.awaitTermination(10, TimeUnit.SECONDS);
//            LOGGER.info("Consumer " + consumerId + " connection thread pool stopped");
//        }
//        catch (InterruptedException e) {
//            LOGGER.info("Could not stop consumer " + consumerId + " connection thread pool");
//            Thread.currentThread().interrupt();
//
//            if(isCancelled.get())
//                return;
//        }

        logger.info("Consumer " + consumerId + " will restart in 5 seconds");
        ClientUtils.waitFor(5000, isCancelled);

//        this.executorService = Executors.newFixedThreadPool(1, new NamedThreadFactory("Consumer-" + consumerId));

    }

//    private void closeConnection(Connection connection) throws IOException {
//        boolean closed = false;
//        while (!closed) {
//            try {
//                connection.close();
//                closed = true;
//            } catch (AlreadyClosedException e) {
//                LOGGER.info("Waiting for connection to auto-recover in order to cleanly close");
//                ClientUtils.waitFor(100, false);
//            }
//        }
//    }

    private int startChannel(Connection connection, Integer currentStep) throws IOException, TimeoutException {
        int exitReason = 0;
        Channel channel = connection.createChannel();
        logger.info("Consumer " + consumerId + " opened channel");
        try {
            boolean noAck = false;

            if (consumerSettings.getAckMode().isManualAcks()) {
                channel.confirmSelect();
            } else {
                noAck = true;
            }

            if (consumerSettings.getAckMode().getConsumerPrefetch() > 0) {
                channel.basicQos(consumerSettings.getAckMode().getConsumerPrefetch());
            }

            eventingConsumer = new EventingConsumer(consumerId,
                    connectionSettings.getVhost(),
                    consumerSettings.getQueue(),
                    channel,
                    stats,
                    messageModel,
                    consumerStats,
                    consumerSettings.getAckMode().getConsumerPrefetch(),
                    consumerSettings.getAckMode().getAckInterval(),
                    consumerSettings.getProcessingMs());

            String consumerTag = channel.basicConsume(consumerSettings.getQueue(), noAck, eventingConsumer);
            logger.info("Consumer " + consumerId + " consuming with tag: " + consumerTag + " from " + currentHost.getNodeName());

            while (!isCancelled.get() && currentStep.equals(step) && channel.isOpen() && !eventingConsumer.isConsumerCancelled()) {
                ClientUtils.waitFor(1000, this.isCancelled);

                if(reconnectToNewHost()) {
                    exitReason = 3;
                    break;
                }
            }

            if(isCancelled.get())
                eventingConsumer.tryAcknowledgeRemaining();

            if(exitReason == 0) {
                if (isCancelled.get())
                    exitReason = 1;
                else if (!currentStep.equals(step))
                    exitReason = 2;
                else
                    exitReason = 3;
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
                    logger.info("Consumer " + consumerId + " closed channel to " + currentHost.getNodeName() + " with exit reason " + exitReason);
                }
                catch(Exception e) {
                    logger.error("Consumer " + consumerId + " could not close channel to " + currentHost.getNodeName() + " with exit reason " + exitReason, e);
                }
            }
            else {
                exitReason = 3;
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
        factory.setShutdownTimeout(0);

        if(consumerSettings.getFrameMax() > 0)
            factory.setRequestedFrameMax(consumerSettings.getFrameMax());

        factory.setRequestedHeartbeat(10);
        //factory.setSharedExecutor(this.executorService);
        factory.setThreadFactory(r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        });

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
                host = queueHosts.getHost(connectionSettings.getVhost(), consumerSettings.getQueue());
            else if (connectionSettings.getConsumerConnectToNode().equals(ConnectToNode.NonLocal))
                host = queueHosts.getRandomOtherHost(connectionSettings.getVhost(), consumerSettings.getQueue());
            else
                throw new TopologyException("ConnectToNode value not supported: " + connectionSettings.getConsumerConnectToNode());

            if(host != null)
                return host;
            else
                ClientUtils.waitFor(1000, isCancelled);
        }

        throw new TopologyException("Could not identify a broker to connect to");
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
            if(queueHosts.isQueueHost(connectionSettings.getVhost(), consumerSettings.getQueue(), currentHost)) {
                logger.info("Detected change of queue host. Now connected to the queue host in non-local mode! " + currentHost.getNodeName() +   " hosts the queue");
                return true;
            }
        }

        return false;
    }

}
