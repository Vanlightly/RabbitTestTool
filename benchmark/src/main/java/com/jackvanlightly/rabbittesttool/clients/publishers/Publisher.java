package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.*;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.PublisherGroupStats;
import com.jackvanlightly.rabbittesttool.topology.Broker;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.TopologyException;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.*;
import com.rabbitmq.client.*;
import io.micrometer.core.instrument.util.NamedThreadFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class Publisher implements Runnable {

    BenchmarkLogger logger;
    long confirmTimeoutThresholdNs;

    String publisherId;
    MessageModel messageModel;
    PublisherGroupStats publisherGroupStats;
    ConnectionSettings connectionSettings;
    ConnectionFactory factory;
    QueueHosts queueHosts;
    ExecutorService executorService;
    PublisherSettings publisherSettings;
    AtomicBoolean isCancelled;
    int routingKeyIndex;
    PublisherStats publisherStats;
    PublisherListener listener;
    Broker currentHost;
    int checkHostInterval = 100000;

    // for stream round robin
    int maxStream;
    Map<Integer, Long> streamCounter;

    // for send to queue round robin
    List<String> queuesInGroup;
    int queueCount;

    // for quick logic decisions
    boolean useConfirms;
    boolean isFixedRoutingKey;
    String fixedExchange;
    String fixedRoutingKey;
    int routingKeyCount;
    int inFlightLimit;

    // for message publishing and confirms
    MessageGenerator messageGenerator;
    List<Map<String,Object>> availableMessageHeaderCombinations;
    int availableHeaderCount;
    Random rand = new Random();

    // for publishing rate
    private boolean rateLimit;
    RateLimiter rateLimiter;
    private long sentCount;
    private long sendLimit;

    public Publisher(String publisherId,
                     MessageModel messageModel,
                     PublisherGroupStats publisherGroupStats,
                     ConnectionSettings connectionSettings,
                     QueueHosts queueHosts,
                     PublisherSettings publisherSettings,
                     List<String> queuesInGroup,
                     ExecutorService executorService) {
        this.logger = new BenchmarkLogger("PUBLISHER");
        this.isCancelled = new AtomicBoolean();
        this.publisherId = publisherId;
        this.messageModel = messageModel;
        this.publisherGroupStats = publisherGroupStats;
        this.publisherSettings = publisherSettings;
        this.connectionSettings = connectionSettings;
        this.queueHosts = queueHosts;
        this.executorService = executorService;
        this.useConfirms = publisherSettings.getPublisherMode().isUseConfirms();
        this.publisherStats = new PublisherStats();

        this.maxStream = publisherSettings.getStreams().size()-1;

        messageGenerator = new MessageGenerator();
        messageGenerator.setBaseMessageSize(publisherSettings.getMessageSize());

        if(publisherSettings.getSendToMode() == SendToMode.QueueGroup) {
            this.queuesInGroup = queuesInGroup;
            this.queueCount = queuesInGroup.size();
        }
        else if(publisherSettings.getSendToExchange().getRoutingKeyMode() == RoutingKeyMode.MultiValue) {
            this.routingKeyCount = publisherSettings.getSendToExchange().getRoutingKeys().length;
        }

        this.availableMessageHeaderCombinations = initializeHeaders(publisherSettings.getMessageHeadersPerMessage());
        this.availableHeaderCount = this.availableMessageHeaderCombinations.size();
        this.inFlightLimit = this.publisherSettings.getPublisherMode().getInFlightLimit();

        this.sendLimit = this.publisherSettings.getMessageLimit();
        this.rateLimit = this.publisherSettings.getPublishRatePerSecond() > 0;

        this.rateLimiter = new RateLimiter();

        initializeStreamCounter();
        initializeRouting();

        confirmTimeoutThresholdNs = 1000000000L*300L; // 5 minutes. TODO add as an arg
    }

    public void signalStop() {
        this.isCancelled.set(true);
    }

    public int getPendingConfirmCount() {
        if(useConfirms) {
            if(listener == null)
                return 0;

            return listener.getPendingConfirmCount();
        }
        else
            return 0;
    }

    public void addQueue(String queue) {
        if(publisherSettings.getSendToMode() == SendToMode.QueueGroup) {
            queuesInGroup.add(queue);
            queueCount = queuesInGroup.size();
        }
    }

    public void setMessageSize(int bytes) {
        this.messageGenerator.setBaseMessageSize(bytes);
    }

    public void setMessageHeaders(int headers) {
        this.availableMessageHeaderCombinations = initializeHeaders(headers);
        this.availableHeaderCount = availableMessageHeaderCombinations.size();
        publisherSettings.setMessageHeadersPerMessage(headers);
    }

    public int getPublishRatePerSecond() {
        return this.publisherSettings.getPublishRatePerSecond();
    }

    public void setPublishRatePerSecond(int msgsPerSecond) {
        this.publisherSettings.setPublishRatePerSecond(msgsPerSecond);
        this.rateLimiter.configureRateLimit(this.publisherSettings.getPublishRatePerSecond());
    }

    public void modifyPublishRatePerSecond(double percentModification) {
        int newPublishRate = (int)(percentModification * this.publisherSettings.getPublishRatePerSecond());
        this.publisherSettings.setPublishRatePerSecond(newPublishRate);
        this.rateLimiter.configureRateLimit(this.publisherSettings.getPublishRatePerSecond());
    }

    public void setWarmUpModifier(double warmUpModifier) {
        this.rateLimit = true;
        this.rateLimiter.setWarmUpModifier(warmUpModifier);
        int rate = this.publisherSettings.getPublishRatePerSecond() > 0
                ? this.publisherSettings.getPublishRatePerSecond()
                : 10000;
        this.rateLimiter.configureRateLimit(rate);
    }

    public void endWarmUp() {
        this.rateLimiter.setWarmUpModifier(1.0);
        rateLimit = publisherSettings.getPublishRatePerSecond() > 0;
        if(rateLimit)
            this.rateLimiter.configureRateLimit(publisherSettings.getPublishRatePerSecond());
    }

    public int getInFlightLimit() {
        return publisherSettings.getPublisherMode().getInFlightLimit();
    }

    public void setInFlightLimit(int inFlightLimit) {
        publisherSettings.getPublisherMode().setInFlightLimit(inFlightLimit);
        this.inFlightLimit = inFlightLimit;
    }

    public void setRoutingKeyIndex(int rkIndex) {
        this.routingKeyIndex = rkIndex;
    }

    public long getRecordedSendCount() {
        return publisherStats.getAndResetRecordedSent();
    }

    public long getRealSendCount() {
        return publisherStats.getAndResetRealSent();
    }

    public void resetSendCount() {
        this.sentCount = 0;
    }

    public void performInitialSend() {
        if(publisherSettings.getInitialPublish() == 0)
            return;

        while(!isCancelled.get()) {
            Connection connection = null;
            Channel channel = null;
            try {
                connection = getConnection();
                channel = connection.createChannel();
                ConcurrentNavigableMap<Long,MessagePayload> pendingConfirms = new ConcurrentSkipListMap<>();
                FlowController flowController = new FlowController(1000);
                PublisherListener initSendListener = new PublisherListener(messageModel, publisherGroupStats, pendingConfirms, flowController);

                logger.info("Publisher " + publisherId + " opened channel for initial publish");

                channel.confirmSelect();
                channel.addConfirmListener(initSendListener);
                channel.addReturnListener(initSendListener);

                int currentStream = 0;
                while (!isCancelled.get()) {
                    flowController.getSendPermit();

                    publish(channel, currentStream, pendingConfirms, true);
                    sentCount++;
                    currentStream++;

                    if (currentStream > maxStream)
                        currentStream = 0;

                    if(sentCount >= publisherSettings.getInitialPublish())
                        break;
                }

                logger.info("Publisher " + publisherId + " stopping initial publish");

                tryClose(channel);
                tryClose(connection);
            } catch (Exception e) {
                logger.error("Publisher" + publisherId + " failed in initial publish", e);
                tryClose(channel);
                tryClose(connection);
                waitFor(5000);
            }

            if(sentCount >= publisherSettings.getInitialPublish())
                break;

            if(!isCancelled.get()) {
                logger.info("Publisher" + publisherId + " restarting to complete initial publish");
                waitFor(1000);
            }
        }

        logger.info("Publisher " + publisherId + " completed initial send");
    }

    @Override
    public void run() {
        while(!isCancelled.get()) {
            Connection connection = null;
            Channel channel = null;
            try {
                ConcurrentNavigableMap<Long,MessagePayload> pendingConfirms = new ConcurrentSkipListMap<>();

                FlowController flowController = new FlowController(publisherSettings.getPublisherMode().getInFlightLimit());
                listener = new PublisherListener(messageModel, publisherGroupStats, pendingConfirms, flowController);

                connection = getConnection();
                channel = connection.createChannel();
                messageModel.clientConnected(publisherId);
                logger.info("Publisher " + publisherId + " opened channel to " + currentHost.getNodeName() + ". Has streams: " + String.join(",", publisherSettings.getStreams().stream().map(x -> String.valueOf(x)).collect(Collectors.toList())));

                if (this.useConfirms) {
                    channel.confirmSelect();
                    channel.addConfirmListener(listener);
                }

                if (publisherSettings.useMandatoryFlag()) {
                    channel.addReturnListener(listener);
                }

                connection.addBlockedListener(listener);

                if (rateLimit) {
                    this.rateLimiter.configureRateLimit(publisherSettings.getPublishRatePerSecond());
                    this.checkHostInterval = this.rateLimiter.getLimitInSecond()*30;
                }

                int currentInFlightLimit = publisherSettings.getPublisherMode().getInFlightLimit();
                int currentStream = 0;
                boolean reconnect = false;
                while (!isCancelled.get() && !reconnect) {
                    if (this.useConfirms) {
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
                            if (!channel.isOpen()) {
                                reconnect = true;
                                break;
                            }

                            listener.checkForTimeouts(confirmTimeoutThresholdNs);
                        }
                    }

                    boolean send = (sendLimit == 0 || (sendLimit > 0 && sentCount < sendLimit)) && !reconnect && !isCancelled.get();

                    if(send) {
                        publish(channel, currentStream, pendingConfirms, false);
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

                tryClose(channel);
                tryClose(connection);
            } catch (TimeoutException | IOException e) {
                logger.error("Publisher" + publisherId + " connection failed");
                waitFor(5000);
            } catch (Exception e) {
                logger.error("Publisher" + publisherId + " failed unexpectedly", e);
                tryClose(channel);
                tryClose(connection);
                waitFor(5000);
            }

            if(!isCancelled.get()) {
                logger.info("Publisher" + publisherId + " restarting");
                waitFor(1000);
            }
        }

        logger.info("Publisher " + publisherId + " stopped successfully");
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
            if (connection.isOpen()) {
                connection.close();
            }

            messageModel.clientDisconnected(publisherId);
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

    private void publish(Channel channel, int currentStream, ConcurrentNavigableMap<Long,MessagePayload> pendingConfirms, boolean isInitialPublish) throws IOException {
        long seqNo = channel.getNextPublishSeqNo();
        long timestamp = MessageUtils.getTimestamp();

        int stream = publisherSettings.getStreams().get(currentStream);
        Long streamSeqNo = getAndIncrementStreamCounter(stream);
        MessagePayload mp = new MessagePayload(stream, streamSeqNo, timestamp);
        AMQP.BasicProperties messageProperties = getProperties();

        if(isInitialPublish)
            pendingConfirms.put(seqNo, mp);
        else if(this.useConfirms)
            pendingConfirms.put(seqNo, mp);
        else
            messageModel.sent(mp);

        byte[] body = messageGenerator.getMessageBytes(mp);
        String routingKey = getRoutingKey(currentStream);

        channel.basicPublish(fixedExchange,
                    routingKey,
                    publisherSettings.useMandatoryFlag(), false,
                    messageProperties,
                    body);

        int headerCount = messageProperties.getHeaders() != null ? messageProperties.getHeaders().size() : 0;
        int deliveryMode = publisherSettings.getDeliveryMode() == DeliveryMode.Persistent ? 2 : 1;

        publisherGroupStats.handleSend(body.length,
                headerCount,
                deliveryMode,
                routingKey.length());
        publisherStats.incrementSendCount();
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

        if(publisherSettings.getFrameMax() > 0)
            factory.setRequestedFrameMax(publisherSettings.getFrameMax());

        factory.setRequestedHeartbeat(10);
        factory.setSharedExecutor(executorService);
        //factory.setSharedExecutor(this.executorService);
        factory.setThreadFactory(new NamedThreadFactory("PublisherConnection-" + publisherId));

        if(!connectionSettings.isNoTcpDelay())
            factory.setSocketConfigurator(new WithNagleSocketConfigurator());

        return factory.newConnection();
    }

    private Broker getBrokerToConnectTo() {
        while(!isCancelled.get()) {
            Broker host = null;
            if(connectionSettings.getPublisherConnectToNode().equals(ConnectToNode.RoundRobin))
                host = queueHosts.getHostRoundRobin();
            else if (connectionSettings.getPublisherConnectToNode().equals(ConnectToNode.Random))
                host = queueHosts.getRandomHost();
            else if (connectionSettings.getPublisherConnectToNode().equals(ConnectToNode.Local)
                    && publisherSettings.getSendToMode() == SendToMode.QueueGroup
                    && publisherSettings.getSendToQueueGroup().getQueueGroupMode() == QueueGroupMode.Counterpart)
                host = queueHosts.getHost(getQueueCounterpart());
            else if (connectionSettings.getPublisherConnectToNode().equals(ConnectToNode.NonLocal)
                    && publisherSettings.getSendToMode() == SendToMode.QueueGroup
                    && publisherSettings.getSendToQueueGroup().getQueueGroupMode() == QueueGroupMode.Counterpart)
                host = queueHosts.getRandomOtherHost(getQueueCounterpart());
            else
                host = queueHosts.getRandomHost();


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
        if(connectionSettings.getPublisherConnectToNode().equals(ConnectToNode.Local)
                && publisherSettings.getSendToMode() == SendToMode.QueueGroup
                && publisherSettings.getSendToQueueGroup().getQueueGroupMode() == QueueGroupMode.Counterpart) {
            Broker host = getBrokerToConnectTo();
            if (!host.getNodeName().equals(currentHost.getNodeName())) {
                logger.info("Detected change of queue host. No longer: " + currentHost.getNodeName() + " now: " + host.getNodeName());
                return true;
            }
        }
        else if (connectionSettings.getPublisherConnectToNode().equals(ConnectToNode.NonLocal)
                && publisherSettings.getSendToMode() == SendToMode.QueueGroup
                && publisherSettings.getSendToQueueGroup().getQueueGroupMode() == QueueGroupMode.Counterpart) {
            if(queueHosts.isQueueHost(getQueueCounterpart(), currentHost)) {
                logger.info("Detected change of queue host. Now connected to the queue host in non-local mode! " + currentHost.getNodeName() +   " hosts the queue");
                return true;
            }
        }

        return false;
    }

    private void initializeRouting() {
        initializeExchange();
        initializeRoutingKey();
    }

    private void initializeExchange() {
        if(publisherSettings.getSendToMode() == SendToMode.Exchange) {
            this.fixedExchange = publisherSettings.getSendToExchange().getExchange();
        }
        else {
            this.fixedExchange = "";
        }
    }

    private void initializeRoutingKey() {
        switch(publisherSettings.getSendToMode()) {
            case Exchange:
                switch (publisherSettings.getSendToExchange().getRoutingKeyMode()) {
                    case None:
                        isFixedRoutingKey = true;
                        fixedRoutingKey = "";
                        break;
                    case FixedValue:
                        isFixedRoutingKey = true;
                        fixedRoutingKey = publisherSettings.getSendToExchange().getRoutingKey();
                        break;
                    case MultiValue:
                        isFixedRoutingKey = false;
                        break;
                    case Random:
                        isFixedRoutingKey = false;
                        break;
                    case RoutingKeyIndex:
                        isFixedRoutingKey = false;
                        break;
                    case StreamKey:
                        if (publisherSettings.getStreams().size() > 1) {
                            isFixedRoutingKey = false;
                        } else {
                            isFixedRoutingKey = true;
                            fixedRoutingKey = "0";
                        }
                        break;
                    default:
                        throw new RuntimeException("RoutingKeyMode" + publisherSettings.getSendToExchange().getRoutingKeyMode() + " not currently supported");
                }
                break;
            case QueueGroup:
                switch (publisherSettings.getSendToQueueGroup().getQueueGroupMode()) {
                    case Counterpart:
                        isFixedRoutingKey = true;
                        fixedRoutingKey = getQueueCounterpart();
                }
        }
    }

    private String getQueueCounterpart() {
        int pubOrdinal = Integer.valueOf(publisherId.split("_")[2]);
        int maxQueue = this.queuesInGroup.stream().map(x -> Integer.valueOf(x.split("_")[1])).max(Integer::compareTo).get();
        int mod_result = pubOrdinal % maxQueue;
        int queueOrdinal = mod_result == 0 ? maxQueue : mod_result;

        for(String queue : this.queuesInGroup) {
            int ordinal = Integer.valueOf(queue.split("_")[1]);
            if(ordinal == queueOrdinal)
                return queue;
        }

        throw new RuntimeException("No queue counterpart exists for publisher: " + this.publisherId);
    }

    private String getRoutingKey(int currentStream) {
        if(isFixedRoutingKey)
            return fixedRoutingKey;

        switch(publisherSettings.getSendToMode()) {
            case Exchange:
                switch (publisherSettings.getSendToExchange().getRoutingKeyMode()) {
                    case Random:
                        return UUID.randomUUID().toString();
                    case StreamKey:
                        return String.valueOf(currentStream);
                    case MultiValue:
                        int rkIndex = this.rand.nextInt(routingKeyCount);
                        return publisherSettings.getSendToExchange().getRoutingKeys()[rkIndex];
                    case RoutingKeyIndex:
                        return publisherSettings.getSendToExchange().getRoutingKeys()[this.routingKeyIndex];
                    default:
                        throw new RuntimeException("Only Random or StreamKey RoutingKeyMode is compatible with a changing routing key when sending to a named exchange");
                }
            case QueueGroup:
                switch(publisherSettings.getSendToQueueGroup().getQueueGroupMode()) {
                    case Random:
                        int queueIndex = rand.nextInt(queueCount);
                        String queue = queuesInGroup.get(queueIndex);
                        return queue;
                    default:
                        throw new RuntimeException("Only Random QueueGroupMode is compatible with a changing routing key when sending to a queue group");
                }
            default:
                throw new RuntimeException("Non-supported SendToMode " + publisherSettings.getSendToMode());
        }
    }

    private AMQP.BasicProperties getProperties() {
        AMQP.BasicProperties.Builder propertiesBuilder = new AMQP.BasicProperties.Builder();
        if (publisherSettings.getDeliveryMode() == DeliveryMode.Persistent) {
            propertiesBuilder.deliveryMode(2);
        }
        else {
            propertiesBuilder.deliveryMode(1);
        }

        if(publisherSettings.getMessageHeadersPerMessage() > 0) {
            Map<String, Object> messageHeaders = availableMessageHeaderCombinations.get(rand.nextInt(this.availableHeaderCount));
            propertiesBuilder.headers(messageHeaders);
        }

        return propertiesBuilder.build();
    }

    private List<Map<String,Object>> initializeHeaders(int headersPerMessage) {
        if(headersPerMessage > publisherSettings.getAvailableHeaders().size())
            throw new RuntimeException("Cannot scale headers to a greater number than have been defined");

        List<Map<String,Object>> headerCombos = new ArrayList<>();
        int totalHeadersAvailable = publisherSettings.getAvailableHeaders().size();
        for(int i=0; i<totalHeadersAvailable; i++) {
            Map<String,Object> headers = new HashMap<>();

            for(int j=0; j<headersPerMessage; j++) {
                int headerIndex = (i + j) % totalHeadersAvailable;
                MessageHeader messageHeader = publisherSettings.getAvailableHeaders().get(headerIndex);
                headers.put(messageHeader.getKey(), messageHeader.getValue());
            }

            headerCombos.add(headers);
        }

        return headerCombos;
    }

    private void initializeStreamCounter() {
        streamCounter = new HashMap<>();

        for(Integer stream : publisherSettings.getStreams())
            streamCounter.put(stream, 0L);
    }

    private Long getAndIncrementStreamCounter(Integer stream) {
        Long current = streamCounter.get(stream);
        streamCounter.put(stream, current +  1);
        return current;
    }

}
