package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.jackvanlightly.rabbittesttool.clients.*;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.Stats;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.*;
import com.rabbitmq.client.*;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Publisher implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger("PUBLISHER");

    private String publisherId;
    private MessageModel messageModel;
    private Stats stats;
    private ConnectionSettings connectionSettings;
    private ConnectionFactory factory;
    private ExecutorService executorService;
    private PublisherSettings publisherSettings;
    private boolean isCancelled;
    private int routingKeyIndex;
    private PublisherStats publisherStats;

    // for stream round robin
    private int maxStream;
    private Map<Integer, Integer> streamCounter;

    // for send to queue round robin
    private List<String> queuesInGroup;
    private int queueCount;

    // for quick logic decisions
    private boolean useConfirms;
    private boolean isFixedRoutingKey;
    private String fixedExchange;
    private String fixedRoutingKey;
    private int routingKeyCount;
    private int inFlightLimit;

    // for message publishing and confirms
    //private ConcurrentNavigableMap<Long,MessagePayload> pendingConfirms;
    //private Semaphore inflightSemaphore;
    private MessageGenerator messageGenerator;
    private List<Map<String,Object>> availableMessageHeaderCombinations;
    private int availableHeaderCount;
    private Random rand = new Random();

    // for publishing rate
    private int sentInPeriod;
    private int limitInPeriod;
    private int periodNs;
    private long sentCount;
    private long sendLimit;

    public Publisher(String publisherId,
                     MessageModel messageModel,
                     Stats stats,
                     ConnectionSettings connectionSettings,
                     PublisherSettings publisherSettings,
                     List<String> queuesInGroup) {
        this.publisherId = publisherId;
        this.messageModel = messageModel;
        this.stats = stats;
        this.publisherSettings = publisherSettings;
        this.connectionSettings = connectionSettings;
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

        this.executorService = Executors.newSingleThreadExecutor(new NamedThreadFactory("Publisher-" + publisherId));

        this.availableMessageHeaderCombinations = initializeHeaders(publisherSettings.getMessageHeadersPerMessage());
        this.availableHeaderCount = this.availableMessageHeaderCombinations.size();
        this.inFlightLimit = this.publisherSettings.getPublisherMode().getInFlightLimit();
        this.sendLimit = this.publisherSettings.getMessageLimit();
        initializeStreamCounter();
        initializeRouting();
    }

    public void signalStop() {
        this.isCancelled = true;
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
        configureRateLimit();
    }

    public void modifyPublishRatePerSecond(double percentModification) {
        int newPublishRate = (int)(percentModification * this.publisherSettings.getPublishRatePerSecond());
        this.publisherSettings.setPublishRatePerSecond(newPublishRate);
        configureRateLimit();
    }

    public int getInFlightLimit() {
        return publisherSettings.getPublisherMode().getInFlightLimit();
    }

    public void setInFlightLimit(int inFlightLimit) {
        int diff = inFlightLimit - publisherSettings.getPublisherMode().getInFlightLimit();
        if(diff < 0)
            throw new RuntimeException("Can only increase InFlightLimit");

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
        while(!isCancelled) {
            Connection connection = null;
            Channel channel = null;
            try {
                connection = getConnection();
                channel = connection.createChannel();
                ConcurrentNavigableMap<Long,MessagePayload> pendingConfirms = new ConcurrentSkipListMap<>();
                Semaphore inflightSemaphore = new Semaphore(1000);
                PublisherListener listener = new PublisherListener(messageModel, stats, pendingConfirms, inflightSemaphore);

                LOGGER.info("Publisher " + publisherId + " opened channel for initial publish");

                channel.confirmSelect();
                channel.addConfirmListener(listener);
                channel.addReturnListener(listener);

                int currentStream = 0;
                while (!isCancelled) {
                    inflightSemaphore.acquire();

                    publish(channel, currentStream, pendingConfirms, true);
                    sentCount++;
                    currentStream++;

                    if (currentStream > maxStream)
                        currentStream = 0;

                    if(sentCount >= publisherSettings.getInitialPublish())
                        break;
                }

                LOGGER.info("Publisher " + publisherId + " stopping initial publish");

                tryClose(channel);
                tryClose(connection);
            } catch (Exception e) {
                LOGGER.error("Publisher" + publisherId + " failed in initial publish", e);
                tryClose(channel);
                tryClose(connection);
                waitFor(5000);
            }

            if(sentCount >= publisherSettings.getInitialPublish())
                break;

            if(!isCancelled) {
                LOGGER.info("Publisher" + publisherId + " restarting to complete initial publish");
                waitFor(1000);
            }
        }

        LOGGER.info("Publisher " + publisherId + " completed initial send");
    }

    @Override
    public void run() {
        while(!isCancelled) {
            Connection connection = null;
            Channel channel = null;
            try {
                ConcurrentNavigableMap<Long,MessagePayload> pendingConfirms = new ConcurrentSkipListMap<>();
                Semaphore inflightSemaphore = new Semaphore(publisherSettings.getPublisherMode().getInFlightLimit());
                PublisherListener listener = new PublisherListener(messageModel, stats, pendingConfirms, inflightSemaphore);

                connection = getConnection();
                channel = connection.createChannel();
                LOGGER.info("Publisher " + publisherId + " opened channel. Has streams: " + String.join(",", publisherSettings.getStreams().stream().map(x -> String.valueOf(x)).collect(Collectors.toList())));

                if (this.useConfirms) {
                    channel.confirmSelect();
                    channel.addConfirmListener(listener);
                }

                if (publisherSettings.useMandatoryFlag()) {
                    channel.addReturnListener(listener);
                }

                connection.addBlockedListener(listener);

                // period and limit for rate limiting
                long periodStartNs = System.nanoTime();
                boolean rateLimit = publisherSettings.getPublishRatePerSecond() > 0;

                if (rateLimit)
                    configureRateLimit();

                // examples:
                //nextPublishRatePerSecondStep
                //  Msgs per sec: 1	    measurementPeriod: 1000ms		limit: 1
                //  Msgs per sec: 2	    measurementPeriod: 500ms		limit: 1
                //  Msgs per sec: 10    measurementPeriod: 100ms		limit: 1
                //  Msgs per sec: 100   measurementPeriod: 10ms		    limit: 1
                //  Msgs per sec: 10000	measurementPeriod: 10ms		    limit: 100

                int currentInFlightLimit = publisherSettings.getPublisherMode().getInFlightLimit();
                int currentStream = 0;
                while (!isCancelled) {
                    if (this.useConfirms) {
                        if(this.inFlightLimit != currentInFlightLimit) {
                            int diff = this.inFlightLimit - currentInFlightLimit;
                            if(diff > 0)
                                inflightSemaphore.release(diff);
                            currentInFlightLimit = this.inFlightLimit;
                        }
                        inflightSemaphore.acquire();
                    }

                    boolean send = sendLimit == 0 || (sendLimit > 0 && sentCount < sendLimit);

                    if(send) {
                        publish(channel, currentStream, pendingConfirms, false);
                        sentCount++;

                        currentStream++;
                        if (currentStream > maxStream)
                            currentStream = 0;

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
                    else {
                        waitFor(10);
                    }
                }

                LOGGER.info("Publisher " + publisherId + " stopping");

                tryClose(channel);
                tryClose(connection);
            } catch (Exception e) {
                LOGGER.error("Publisher" + publisherId + " failed", e);
                tryClose(channel);
                tryClose(connection);
                waitFor(5000);
            }

            if(!isCancelled) {
                LOGGER.info("Publisher" + publisherId + " restarting");
                waitFor(1000);
            }
        }

        LOGGER.info("Publisher " + publisherId + " stopped successfully");
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

    private void configureRateLimit() {
        int measurementPeriodMs = 1000 / publisherSettings.getPublishRatePerSecond();

        if (measurementPeriodMs >= 10) {
            this.limitInPeriod = 1;
        } else {
            measurementPeriodMs = 10;
            int periodsPerSecond = 1000 / measurementPeriodMs;
            this.limitInPeriod = publisherSettings.getPublishRatePerSecond() / periodsPerSecond;
        }

        this.periodNs = measurementPeriodMs * 1000000;
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
        Integer streamSeqNo = getAndIncrementStreamCounter(stream);
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
        stats.handleSend(body.length,
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

        String host = "";
        if(connectionSettings.isTryConnectToLocalBroker()
                && publisherSettings.getSendToMode() == SendToMode.QueueGroup
                && publisherSettings.getSendToQueueGroup().getQueueGroupMode() == QueueGroupMode.Counterpart) {
            host = QueueHosts.getHost(connectionSettings.getVhost(), getQueueCounterpart());
        }
        else {
            host = connectionSettings.getNextHostAndPort();
        }

        factory.setHost(host.split(":")[0]);
        factory.setPort(Integer.valueOf(host.split(":")[1]));

        factory.setConnectionTimeout(5000);
        factory.setAutomaticRecoveryEnabled(false);
        factory.setRequestedFrameMax(publisherSettings.getFrameMax());
        factory.setRequestedHeartbeat(10);
        factory.setSharedExecutor(this.executorService);
        factory.setThreadFactory(r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        });

        if(!connectionSettings.isNoTcpDelay())
            factory.setSocketConfigurator(new WithNagleSocketConfigurator());

        return factory.newConnection();
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
        for(String queue : this.queuesInGroup) {
            int ordinal = Integer.valueOf(queue.split("_")[1]);
            if(ordinal == pubOrdinal)
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
            streamCounter.put(stream, 0);
    }

    private Integer getAndIncrementStreamCounter(Integer stream) {
        Integer current = streamCounter.get(stream);
        streamCounter.put(stream, current +  1);
        return current;
    }

}
