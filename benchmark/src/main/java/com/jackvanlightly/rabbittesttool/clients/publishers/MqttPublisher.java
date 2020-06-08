package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.lifecycle.MqttClientAutoReconnect;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.*;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.PublisherGroupStats;
import com.jackvanlightly.rabbittesttool.topology.Broker;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.TopologyException;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.DeliveryMode;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.QueueGroupMode;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.SendToMode;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class MqttPublisher implements Runnable {
    BenchmarkLogger logger;
    String publisherId;

    MessageModel messageModel;
    PublisherGroupStats publisherGroupStats;
    PublisherStats publisherStats;
    ConnectionSettings connectionSettings;
    PublisherSettings publisherSettings;
    QueueHosts queueHosts;
    AtomicBoolean isCancelled;
    Broker currentHost;
    int checkHostInterval = 100000;
    long confirmTimeoutThresholdNs;
    MqttPublisherListener publisherListener;

    // for stream round robin
    int maxStream;
    Map<Integer, Long> streamCounter;
    MessageGenerator messageGenerator;

    // for publishing rate
    private boolean rateLimit;
    RateLimiter rateLimiter;
    private long sentCount;
    private long sendLimit;

    int inFlightLimit;
    boolean useConfirms;
    String topic;

    public MqttPublisher(String publisherId,
                         MessageModel messageModel,
                         PublisherGroupStats publisherGroupStats,
                         ConnectionSettings connectionSettings,
                         QueueHosts queueHosts,
                         PublisherSettings publisherSettings)
     {
        this.logger = new BenchmarkLogger("MQTT PUBLISHER");
        this.publisherId = publisherId;
        this.messageModel = messageModel;
        this.publisherGroupStats = publisherGroupStats;
        this.connectionSettings = connectionSettings;
        this.queueHosts = queueHosts;
        this.publisherSettings = publisherSettings;
        this.publisherStats = new PublisherStats();

        this.isCancelled = new AtomicBoolean();
        this.maxStream = publisherSettings.getStreams().size()-1;

         messageGenerator = new MessageGenerator();
         messageGenerator.setBaseMessageSize(publisherSettings.getMessageSize());

         this.inFlightLimit = this.publisherSettings.getPublisherMode().getInFlightLimit();
         this.useConfirms = this.publisherSettings.getPublisherMode().isUseConfirms();
         this.sendLimit = this.publisherSettings.getMessageLimit();
         this.rateLimit = this.publisherSettings.getPublishRatePerSecond() > 0;

         this.rateLimiter = new RateLimiter();
         confirmTimeoutThresholdNs = 1000000000L*300L; // 5 minutes. TODO add as an arg
    }

    @Override
    public void run() {

        while(!isCancelled.get()) {
            Mqtt3AsyncClient client = null;
            try {

                ConcurrentNavigableMap<Long, MessagePayload> pendingConfirms = new ConcurrentSkipListMap<>();
                FlowController flowController = new FlowController(publisherSettings.getPublisherMode().getInFlightLimit());
                publisherListener = new MqttPublisherListener(publisherId, messageModel, publisherGroupStats, pendingConfirms, flowController);

                client = getClient(publisherListener);
                messageModel.clientConnected(publisherId);
                logger.info("MQTT Publisher " + publisherId + " opened connection to " + currentHost.getNodeName() + ". Has streams: " + String.join(",", publisherSettings.getStreams().stream().map(x -> String.valueOf(x)).collect(Collectors.toList())));

                if (rateLimit) {
                    this.rateLimiter.configureRateLimit(publisherSettings.getPublishRatePerSecond());
                    this.checkHostInterval = this.rateLimiter.getLimitInSecond()*30;
                }

                int currentInFlightLimit = publisherSettings.getPublisherMode().getInFlightLimit();
                int currentStream = 0;
                boolean reconnect = false;
                while (!isCancelled.get() && !reconnect) {
                    // is this is a multi-step benchmark with increasing in flight limit
                    if(this.inFlightLimit != currentInFlightLimit) {
                        int diff = this.inFlightLimit - currentInFlightLimit;
                        if(diff > 0)
                            flowController.increaseInflightLimit(diff);
                        else
                            flowController.decreaseInflightLimit(diff);

                        currentInFlightLimit = this.inFlightLimit;
                    }

                    // keep trying to acquire until the cancelation or connection dies
                    while(!isCancelled.get() && !flowController.tryGetSendPermit(1000, TimeUnit.MILLISECONDS)) {
                        if (!publisherListener.isConnected()) {
                            reconnect = true;
                            break;
                        }

                        publisherListener.checkForTimeouts(confirmTimeoutThresholdNs);
                    }

                    boolean send = (sendLimit == 0 || (sendLimit > 0 && sentCount < sendLimit)) && !reconnect && !isCancelled.get();

                    if(send) {
                        publish(client, currentStream, pendingConfirms, false);
                        sentCount++;

                        currentStream++;
                        if (currentStream > maxStream)
                            currentStream = 0;

                        if (rateLimit)
                            rateLimiter.rateLimit();

                        if(sentCount % this.checkHostInterval == 0) {
                            if(reconnectToNewHost()) {
                                break;
                            }
                        }
                    }
                    else {
                        waitFor(10);
                    }
                }

                logger.info("Publisher " + publisherId + " stopping");

                tryClose(client);
            } catch (IOException e) {
                logger.error("MQTT Publisher" + publisherId + " connection failed");
                waitFor(5000);
            } catch (Exception e) {
                logger.error("MQTT Publisher" + publisherId + " failed unexpectedly", e);
                tryClose(client);
                waitFor(5000);
            }

            if(!isCancelled.get()) {
                logger.info("MQTT Publisher" + publisherId + " restarting");
                waitFor(1000);
            }
        }

        logger.info("MQTT Publisher " + publisherId + " stopped successfully");
    }

    private void tryClose(Mqtt3AsyncClient client) {
        try {
            if (publisherListener.isConnected()) {
                client.toBlocking().disconnect();

                // TODO: do I need this, doubt it
                messageModel.clientDisconnected(publisherId);
            }
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

    private void publish(Mqtt3AsyncClient client, int currentStream, ConcurrentNavigableMap<Long,MessagePayload> pendingConfirms, boolean isInitialPublish) throws IOException {
        long seqNo = sentCount;
        long timestamp = MessageUtils.getTimestamp();

        int stream = publisherSettings.getStreams().get(currentStream);
        Long streamSeqNo = getAndIncrementStreamCounter(stream);
        MessagePayload mp = new MessagePayload(stream, streamSeqNo, timestamp);

        MqttQos qos = MqttQos.AT_MOST_ONCE;
        if(isInitialPublish)
            pendingConfirms.put(seqNo, mp);
        else if(this.useConfirms) {
            pendingConfirms.put(seqNo, mp);
            qos = MqttQos.AT_LEAST_ONCE;
        }
        else
            messageModel.sent(mp);

        byte[] body = messageGenerator.getMessageBytes(mp);

        client.publishWith()
                .topic(this.topic)
                .payload(body)
                .qos(qos)
                .retain(true)
                .send()
                .thenAccept(s -> publisherListener.handleConfirm(seqNo));

        publisherGroupStats.handleSend(body.length,
                0,
                0,
                0);
        publisherStats.incrementSendCount();
    }

    private Mqtt3AsyncClient getClient(MqttPublisherListener publisherListener) throws ExecutionException, InterruptedException {
        Broker host = getBrokerToConnectTo();

        Mqtt3AsyncClient client = Mqtt3Client.builder()
                .identifier(publisherId)
                .serverHost(host.getIp())
                .serverPort(1883)
                .addConnectedListener(publisherListener)
                .addDisconnectedListener(publisherListener)
                .buildAsync();

        client.connect().get();
        currentHost = host;

        return client;
    }

    private Broker getBrokerToConnectTo() {
        while(!isCancelled.get()) {
            Broker host = queueHosts.getRandomHost();

            if(host != null) {
                return host;
            }
            else {
                ClientUtils.waitFor(1000, isCancelled);
            }
        }

        throw new TopologyException("Could not identify a broker to connect to");
    }

    private boolean reconnectToNewHost() {
//        if(connectionSettings.getPublisherConnectToNode().equals(ConnectToNode.Local)
//                && publisherSettings.getSendToMode() == SendToMode.QueueGroup
//                && publisherSettings.getSendToQueueGroup().getQueueGroupMode() == QueueGroupMode.Counterpart) {
//            Broker host = getBrokerToConnectTo();
//            if (!host.getNodeName().equals(currentHost.getNodeName())) {
//                logger.info("Detected change of queue host. No longer: " + currentHost.getNodeName() + " now: " + host.getNodeName());
//                return true;
//            }
//        }
//        else if (connectionSettings.getPublisherConnectToNode().equals(ConnectToNode.NonLocal)
//                && publisherSettings.getSendToMode() == SendToMode.QueueGroup
//                && publisherSettings.getSendToQueueGroup().getQueueGroupMode() == QueueGroupMode.Counterpart) {
//            if(queueHosts.isQueueHost(getQueueCounterpart(), currentHost)) {
//                logger.info("Detected change of queue host. Now connected to the queue host in non-local mode! " + currentHost.getNodeName() +   " hosts the queue");
//                return true;
//            }
//        }

        return false;
    }

    private Long getAndIncrementStreamCounter(Integer stream) {
        Long current = streamCounter.get(stream);
        streamCounter.put(stream, current +  1);
        return current;
    }
}
