package com.jackvanlightly.rabbittesttool.clients.consumers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.ConnectToNode;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.clients.WithNagleSocketConfigurator;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.Stats;
import com.jackvanlightly.rabbittesttool.topology.Broker;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.TopologyException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.stream.Client;
import io.micrometer.core.instrument.util.NamedThreadFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class StreamConsumer implements Runnable  {
    BenchmarkLogger logger;
    String consumerId;
    ConnectionSettings connectionSettings;
    QueueHosts queueHosts;
    AtomicBoolean isCancelled;
    Integer step;
    Stats stats;
    StreamConsumerListener consumerListener;
    MessageModel messageModel;
    ConsumerSettings consumerSettings;
    ConsumerStats consumerStats;
    Broker currentHost;
    AtomicLong lastOffset;

    public StreamConsumer(String consumerId,
                          ConnectionSettings connectionSettings,
                          QueueHosts queueHosts,
                          ConsumerSettings consumerSettings,
                          Stats stats,
                          MessageModel messageModel) {
        this.logger = new BenchmarkLogger("STREAM CONSUMER");
        this.isCancelled = new AtomicBoolean();
        this.consumerId = consumerId;
        this.connectionSettings = connectionSettings;
        this.queueHosts = queueHosts;
        this.stats = stats;
        this.messageModel = messageModel;
        this.consumerSettings = consumerSettings;
        this.step = 0;
        this.consumerStats = new ConsumerStats();
        this.lastOffset = new AtomicLong();
    }

    public void signalStop() {
        isCancelled.set(true);
    }

    public void setAckInterval(int ackInterval) {
        this.consumerSettings.getAckMode().setAckInterval(ackInterval);
        if(this.consumerListener != null)
            this.consumerListener.setAckInterval(ackInterval);
    }

    public void setAckIntervalMs(int ackIntervalMs) {
        this.consumerSettings.getAckMode().setAckIntervalMs(ackIntervalMs);
        if(this.consumerListener != null)
            this.consumerListener.setAckIntervalMs(ackIntervalMs);
    }

    public void setPrefetch(short prefetch) {
        this.consumerSettings.getAckMode().setConsumerPrefetch(prefetch);
        // we do not update the consumer as a new channel is required
    }

    public void setProcessingMs(int processingMs) {
        this.consumerSettings.setProcessingMs(processingMs);
        if(this.consumerListener != null)
            this.consumerListener.setProcessingMs(processingMs);
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
                Client client = null;
                try {
                    consumerListener = new StreamConsumerListener(consumerId,
                            connectionSettings.getVhost(),
                            consumerSettings.getQueue(),
                            stats,
                            messageModel,
                            consumerStats,
                            lastOffset,
                            consumerSettings.getAckMode().getConsumerPrefetch(),
                            consumerSettings.getAckMode().getAckInterval(),
                            consumerSettings.getAckMode().getAckIntervalMs(),
                            consumerSettings.getProcessingMs());

                    client = getClient(consumerListener);
                    messageModel.clientConnected(consumerId);

                    ConsumerExitReason exitReason = ConsumerExitReason.None;
                    while (!isCancelled.get() && exitReason != ConsumerExitReason.ConnectionFailed) {
                        int currentStep = step;
                        exitReason = startConsumption(client, currentStep);
                    }
                } finally {
                    tryClose(client);
                }
            } catch (TimeoutException | IOException e) {
                if(!isCancelled.get())
                    stats.handleConnectionError();
                logger.error("Consumer " + consumerId + " connection failed in step " + step);
                messageModel.clientDisconnected(consumerId);
            } catch (Exception e) {
                if(!isCancelled.get())
                    stats.handleConnectionError();
                logger.error("Consumer " + consumerId + " has failed unexpectedly in step " + step, e);
                messageModel.clientDisconnected(consumerId);
            }

            if(!isCancelled.get()) {
                recreateWait();
            }
        }
    }

    private void tryClose(Client client) {
        try {
            messageModel.clientDisconnected(consumerId);
            client.close();
        }
        catch(Exception e){}
    }

    private void recreateWait() {
        logger.info("Consumer " + consumerId + " will restart in 5 seconds");
        ClientUtils.waitFor(5000, isCancelled);
    }

    private ConsumerExitReason startConsumption(Client client, Integer currentStep) throws IOException, TimeoutException {
        ConsumerExitReason exitReason = ConsumerExitReason.None;
        logger.info("Consumer " + consumerId + " subscribed");
        try {
            consumerListener = new StreamConsumerListener(consumerId,
                    connectionSettings.getVhost(),
                    consumerSettings.getQueue(),
                    stats,
                    messageModel,
                    consumerStats,
                    this.lastOffset,
                    consumerSettings.getAckMode().getConsumerPrefetch(),
                    consumerSettings.getAckMode().getAckInterval(),
                    consumerSettings.getAckMode().getAckIntervalMs(),
                    consumerSettings.getProcessingMs());

            while (!isCancelled.get() && currentStep.equals(step)) {
                ClientUtils.waitFor(1000, this.isCancelled);
//                if(consumerSettings.getAckMode().isManualAcks())
//                    consumerListener.ensureAckTimeLimitEnforced();
            }

            if(exitReason == ConsumerExitReason.None) {
                if (isCancelled.get())
                    exitReason = ConsumerExitReason.Cancelled;
                else if (!currentStep.equals(step))
                    exitReason = ConsumerExitReason.NextStep;
                else
                    exitReason = ConsumerExitReason.ConnectionFailed;
            }
        }
        catch(Exception e) {
            logger.error("Failed setting up a consumer", e);
            throw e;
        }
        finally {
            if(client.isOpen()) {
                try {
                    client.close();
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

    private Client getClient(StreamConsumerListener listener) {
        currentHost = getBrokerToConnectTo();
        Client consumer = new Client(new Client.ClientParameters()
                .host(currentHost.getIp())
                .port(currentHost.getStreamPort())
                .username(connectionSettings.getUser())
                .password(connectionSettings.getPassword())
                .virtualHost(connectionSettings.getVhost())
                .chunkListener(listener)
                .messageListener(listener));

        Client.Response response = consumer.subscribe(
                this.consumerId.hashCode(),
                consumerSettings.getQueue(),
                this.lastOffset.get(),
                Math.max(10, consumerSettings.getAckMode().getConsumerPrefetch())
        );

        if (!response.isOk()) {
            logger.error("Stream consumer failed to subscribe" + response.getResponseCode());
            throw new RuntimeException("TODO");
        }
        else {
            logger.info("Consumer " + consumerId + " opened connection to " + currentHost.getNodeName());
        }

        return consumer;
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

        throw new TopologyException("Could not identify a broker to connect to");
    }
}
