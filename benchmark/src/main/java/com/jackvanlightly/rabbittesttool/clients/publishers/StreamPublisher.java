package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.*;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.PublisherGroupStats;
import com.jackvanlightly.rabbittesttool.topology.Broker;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.TopologyException;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.QueueGroupMode;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.SendToMode;
import com.rabbitmq.stream.Client;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class StreamPublisher implements Runnable {

    BenchmarkLogger logger;
    long confirmTimeoutThresholdNs;

    String publisherId;
    MessageModel messageModel;
    PublisherGroupStats publisherGroupStats;
    PublisherSettings publisherSettings;
    QueueHosts queueHosts;
    AtomicBoolean isCancelled;
    PublisherStats publisherStats;
    StreamPublisherListener listener;
    ConnectionSettings connectionSettings;
    Broker currentHost;

    // for stream round robin
    int maxStream;
    Map<Integer, Long> streamCounter;
    Map<Integer, Queue<Long>> accumulatedMessages;
    List<String> streamQueues;
    int streamQueueCount;
    String fixedStreamQueue;

    // for quick logic decisions
    boolean useConfirms;
    int inFlightLimit;
    volatile int messageSize;
    int maxBatchSize;
    int maxBatchSizeBytes;
    int maxBatchWaitMs;
    int pendingPublish;
    Instant lastSentBatch;

    // for message publishing and confirms
    MessageGenerator messageGenerator;

    // for publishing rate
    boolean rateLimit;
    RateLimiter rateLimiter;
    long sentCount;
    long sendLimit;
    int checkHostInterval = 100000;

    public StreamPublisher(String publisherId,
                           MessageModel messageModel,
                           PublisherGroupStats publisherGroupStats,
                           ConnectionSettings connectionSettings,
                           QueueHosts queueHosts,
                           PublisherSettings publisherSettings,
                           List<String> rmqStreams) {
        this.logger = new BenchmarkLogger("PUBLISHER");
        this.isCancelled = new AtomicBoolean();
        this.publisherId = publisherId;
        this.messageModel = messageModel;
        this.publisherGroupStats = publisherGroupStats;
        this.publisherSettings = publisherSettings;
        this.connectionSettings = connectionSettings;
        this.queueHosts = queueHosts;
        this.useConfirms = publisherSettings.getPublisherMode().isUseConfirms();
        this.publisherStats = new PublisherStats();
        this.streamQueues = rmqStreams;
        this.fixedStreamQueue = getQueueCounterpart();

        this.maxStream = publisherSettings.getStreams().size()-1;

        // when batching is used, initialize the accumulated message queues
        this.maxBatchSize = publisherSettings.getPublisherMode().getMaxBatchSize();
        this.maxBatchSizeBytes = publisherSettings.getPublisherMode().getMaxBatchSizeBytes();
        this.maxBatchWaitMs = publisherSettings.getPublisherMode().getMaxBatchWaitMs();
        this.accumulatedMessages = new HashMap<>();
        for(Integer stream : publisherSettings.getStreams())
            this.accumulatedMessages.put(stream, new ArrayDeque<>());

        messageGenerator = new MessageGenerator();
        messageGenerator.setBaseMessageSize(publisherSettings.getMessageSize());

        this.inFlightLimit = this.publisherSettings.getPublisherMode().getInFlightLimit();
        this.messageSize = this.publisherSettings.getMessageSize();
        this.sendLimit = this.publisherSettings.getMessageLimit();
        this.rateLimiter = new RateLimiter();
        this.rateLimit = this.publisherSettings.getPublishRatePerSecond() > 0;
        initializeStreamCounter();

        confirmTimeoutThresholdNs = 1000000000L*300L; // 5 minutes. TODO add as an arg
    }

    public void signalStop() {
        this.isCancelled.set(true);
    }

    public int getPendingConfirmCount() {
        if(useConfirms) {
            return listener.getPendingConfirmCount();
        }
        else
            return 0;
    }

    public void addQueue(String queue) {
        streamQueues.add(queue);
        streamQueueCount = streamQueues.size();
    }

    public void setMessageSize(int bytes) {
        this.messageGenerator.setBaseMessageSize(bytes);
        messageSize = bytes;
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

    public int getMaxBatchSize() {
        return publisherSettings.getPublisherMode().getMaxBatchSize();
    }

    public int getMaxBatchSizeBytes() {
        return publisherSettings.getPublisherMode().getMaxBatchSizeBytes();
    }

    public int getMaxBatchWaitMs() {
        return publisherSettings.getPublisherMode().getMaxBatchWaitMs();
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
            Client client = null;
            try {
                ConcurrentNavigableMap<Long,MessagePayload> pendingConfirms = new ConcurrentSkipListMap<>();
                FlowController flowController = new FlowController(1000);
                StreamPublisherListener initSendListener = new StreamPublisherListener(messageModel, publisherGroupStats, pendingConfirms, flowController);

                client = getClient(initSendListener);
                logger.info("Publisher " + publisherId + " opened connection to " + currentHost.getNodeName() + " for initial publish");

                int currentStream = 0;
                while (!isCancelled.get()) {
                    flowController.getSendPermit();

                    publish(client, currentStream, pendingConfirms, true);
                    sentCount++;
                    currentStream++;

                    if (currentStream > maxStream)
                        currentStream = 0;

                    if(sentCount >= publisherSettings.getInitialPublish())
                        break;
                }

                logger.info("Publisher " + publisherId + " stopping initial publish");


                tryClose(client);
            } catch (Exception e) {
                logger.error("Stream publisher" + publisherId + " failed in initial publish", e);
                tryClose(client);
                waitFor(5000);
            }

            if(sentCount >= publisherSettings.getInitialPublish())
                break;

            if(!isCancelled.get()) {
                logger.info("Stream publisher" + publisherId + " restarting to complete initial publish");
                waitFor(1000);
            }
        }

        logger.info("Stream publisher " + publisherId + " completed initial send");
    }

    @Override
    public void run() {
        while(!isCancelled.get()) {
            Client client = null;

            try {
                ConcurrentNavigableMap<Long,MessagePayload> pendingConfirms = new ConcurrentSkipListMap<>();

                FlowController flowController = new FlowController(publisherSettings.getPublisherMode().getInFlightLimit());
                listener = new StreamPublisherListener(messageModel, publisherGroupStats, pendingConfirms, flowController);

                client = getClient(listener);

                String[] streams = new String[streamQueues.size()];
                for(int i=0; i<streamQueues.size();i++)
                    streams[i] = streamQueues.get(i);

                messageModel.clientConnected(publisherId);
                logger.info("Publisher " + publisherId + " opened connection to " + currentHost.getNodeName() + ". Has streams: " + String.join(",", publisherSettings.getStreams().stream().map(x -> String.valueOf(x)).collect(Collectors.toList())));

                if (rateLimit) {
                    this.rateLimiter.configureRateLimit(publisherSettings.getPublishRatePerSecond());
                    this.checkHostInterval = this.rateLimiter.getLimitInSecond()*30;
                }

                int currentInFlightLimit = publisherSettings.getPublisherMode().getInFlightLimit();
                int currentStream = 0;
                boolean reconnect = false;
                this.lastSentBatch = Instant.now();

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
                            if (!client.isOpen()) {
                                reconnect = true;
                                break;
                            }

                            listener.checkForTimeouts(confirmTimeoutThresholdNs);
                        }
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

                logger.info("Stream publisher " + publisherId + " stopping");

                tryClose(client);
            } catch (IOException e) {
                logger.error("Stream publisher" + publisherId + " connection failed");
                waitFor(5000);
            } catch (Exception e) {
                logger.error("Stream publisher" + publisherId + " failed unexpectedly", e);
                tryClose(client);
                waitFor(5000);
            }

            if(!isCancelled.get()) {
                logger.info("Stream publisher" + publisherId + " restarting");
                waitFor(1000);
            }
        }

        logger.info("Stream publisher " + publisherId + " stopped successfully");
    }

    private void tryClose(Client client) {
        try {
            client.close();
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

    private void publish(Client client,
                         int currentStream,
                         ConcurrentNavigableMap<Long,MessagePayload> pendingConfirms,
                         boolean isInitialPublish) throws IOException {
        Integer stream = publisherSettings.getStreams().get(currentStream);
        Long streamSeqNo = getAndIncrementStreamCounter(stream);

        if(maxBatchSize > 1 || maxBatchSizeBytes > 1) {
            accumulatedMessages.get(stream).add(streamSeqNo);
            pendingPublish++;

            boolean sendBatch = (maxBatchSize > 0 && pendingPublish >= maxBatchSize)
                    || (maxBatchSizeBytes > 0 && pendingPublish * messageSize >= maxBatchSizeBytes)
                    || (maxBatchWaitMs > 0 && Duration.between(lastSentBatch, Instant.now()).toMillis() > maxBatchWaitMs);

            if(sendBatch) {
                List<byte[]> binaries = new ArrayList<>();
                for(Integer streamKey : accumulatedMessages.keySet()) {
                    Queue<Long> seqNosToSend = accumulatedMessages.get(streamKey);
                    while(!seqNosToSend.isEmpty()) {
                        Long seqNoToSend = seqNosToSend.remove();
                        long timestamp = MessageUtils.getTimestamp();
                        MessagePayload mp = new MessagePayload(streamKey, seqNoToSend, timestamp);
                        byte[] body = messageGenerator.getMessageBytes(mp);
                        binaries.add(body);
                        registerPublish(isInitialPublish, pendingConfirms, seqNoToSend, mp, body);
                    }
                }
                client.publishBinary(fixedStreamQueue, binaries);
                lastSentBatch = Instant.now();
                pendingPublish = 0;
            }
        }
        else {
            long timestamp = MessageUtils.getTimestamp();
            MessagePayload mp = new MessagePayload(stream, streamSeqNo, timestamp);

            byte[] body = messageGenerator.getMessageBytes(mp);

            long seqNo = client.publish(
                    fixedStreamQueue,
                    body
            );

            registerPublish(isInitialPublish, pendingConfirms, seqNo, mp, body);
        }
    }

    private void registerPublish(boolean isInitialPublish,
                                 ConcurrentNavigableMap<Long,MessagePayload> pendingConfirms,
                                 Long seqNo,
                                 MessagePayload mp,
                                 byte[] body) {
        if (isInitialPublish)
            pendingConfirms.put(seqNo, mp);
        else if (this.useConfirms)
            pendingConfirms.put(seqNo, mp);
        else
            messageModel.sent(mp);

        publisherGroupStats.handleSend(body.length,
                0,
                2,
                0);
        publisherStats.incrementSendCount();
    }

    private Client getClient(StreamPublisherListener listener) {
        currentHost = getBrokerToConnectTo();
        return new Client(new Client.ClientParameters()
                .host(currentHost.getIp())
                .port(currentHost.getStreamPort())
                .username(connectionSettings.getUser())
                .password(connectionSettings.getPassword())
                .virtualHost(connectionSettings.getVhost())
                .confirmListener(listener)
                .publishErrorListener(listener)
        );
    }

    private Broker getBrokerToConnectTo() {
        while(!isCancelled.get()) {
            Broker host = queueHosts.getHost(fixedStreamQueue);

            if(host != null)
                return host;
            else
                ClientUtils.waitFor(1000, isCancelled);
        }

        throw new TopologyException("Could not identify a broker to connect to");
    }

    private void initializeStreamCounter() {
        streamCounter = new HashMap<>();

        for(Integer stream : publisherSettings.getStreams())
            streamCounter.put(stream, 0L);
    }

    private String getQueueCounterpart() {
        int pubOrdinal = Integer.valueOf(publisherId.split("_")[2]);
        int maxQueue = this.streamQueues.stream().map(x -> Integer.valueOf(x.split("_")[1])).max(Integer::compareTo).get();
        int mod_result = pubOrdinal % maxQueue;
        int queueOrdinal = mod_result == 0 ? maxQueue : mod_result;

        for(String queue : this.streamQueues) {
            int ordinal = Integer.valueOf(queue.split("_")[1]);
            if(ordinal == queueOrdinal)
                return queue;
        }

        throw new RuntimeException("No stream queue counterpart exists for publisher: " + this.publisherId);
    }

    private Long getAndIncrementStreamCounter(Integer stream) {
        Long current = streamCounter.get(stream);
        streamCounter.put(stream, current +  1);
        return current;
    }

    private boolean reconnectToNewHost() {
        Broker host = getBrokerToConnectTo();
        if (!host.getNodeName().equals(currentHost.getNodeName())) {
            logger.info("Detected change of queue host. No longer: " + currentHost.getNodeName() + " now: " + host.getNodeName());
            return true;
        }

        return false;
    }
}
