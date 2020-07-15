package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.*;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.MetricGroup;
import com.jackvanlightly.rabbittesttool.statistics.MetricType;
import com.jackvanlightly.rabbittesttool.topology.Broker;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.TopologyException;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.StreamPublishMode;
import com.rabbitmq.stream.Client;
import com.rabbitmq.stream.MessageBatch;
import com.rabbitmq.stream.codec.SimpleCodec;

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
    MetricGroup metricGroup;
    PublisherSettings publisherSettings;
    QueueHosts queueHosts;
    AtomicBoolean isCancelled;
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
    int pendingPublish;
    int currentInFlightLimit;
    Instant lastSentBatch;
    boolean instrumentMessagePayloads;

    MessageGenerator messageGenerator;

    // for publishing rate
    boolean rateLimit;
    RateLimiter rateLimiter;
    long sentCount;
    long sendLimit;
    int checkHostInterval = 100000;

    // batching
    StreamPublishMode publishMode;
    int maxBatchSize;
    int maxBatchSizeBytes;
    int maxBatchWaitMs;
    int maxSubEntryBytes;

    // down-samples the tracking of non sub-entry batch publishing
    // to one message per bucket size - to avoid this tool being the bottleneck
    int singleMessageBucketSize;

    public StreamPublisher(String publisherId,
                           MessageModel messageModel,
                           MetricGroup metricGroup,
                           ConnectionSettings connectionSettings,
                           QueueHosts queueHosts,
                           PublisherSettings publisherSettings,
                           List<String> rmqStreams) {
        this.logger = new BenchmarkLogger("PUBLISHER");
        this.isCancelled = new AtomicBoolean();
        this.publisherId = publisherId;
        this.messageModel = messageModel;
        this.metricGroup = metricGroup;
        this.publisherSettings = publisherSettings;
        this.connectionSettings = connectionSettings;
        this.queueHosts = queueHosts;
        this.useConfirms = publisherSettings.getPublisherMode().isUseConfirms();
        this.streamQueues = rmqStreams;
        this.fixedStreamQueue = getQueueCounterpart();

        this.maxStream = publisherSettings.getStreams().size()-1;

        // when batching is used, initialize the accumulated message queues
        this.publishMode = publisherSettings.getPublisherMode().getStreamPublishMode();
        this.maxBatchSize = publisherSettings.getPublisherMode().getMaxBatchSize();
        this.maxBatchSizeBytes = publisherSettings.getPublisherMode().getMaxBatchSizeBytes();
        this.maxBatchWaitMs = publisherSettings.getPublisherMode().getMaxBatchWaitMs();
        this.maxSubEntryBytes = publisherSettings.getPublisherMode().getMaxSubEntryBytes();

        if(this.maxBatchSize > 1 && this.publishMode == StreamPublishMode.SubEntryBatch)
            singleMessageBucketSize = 1;
        else
            singleMessageBucketSize = publisherSettings.getPublisherMode().getSingleMessageBucketSize();

        this.accumulatedMessages = new HashMap<>();
        for(Integer stream : publisherSettings.getStreams())
            this.accumulatedMessages.put(stream, new ArrayDeque<>());

        messageGenerator = new MessageGenerator();
        messageGenerator.setBaseMessageSize(publisherSettings.getMessageSize());

        this.inFlightLimit = getAdjustedInFlightLimit(this.publisherSettings.getPublisherMode().getInFlightLimit());
        this.messageSize = this.publisherSettings.getMessageSize();
        this.instrumentMessagePayloads = this.publisherSettings.shouldInstrumentMessagePayloads();
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
        this.inFlightLimit = getAdjustedInFlightLimit(inFlightLimit);
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
        return metricGroup.getRecordedDeltaScalarValueForStepStats(MetricType.PublisherSentMessage);
    }

    public long getRealSendCount() {
        return metricGroup.getRealDeltaScalarValueForStepStats(MetricType.PublisherSentMessage);
    }

    public void resetSendCount() {
        this.sentCount = 0;
    }

    public MetricGroup getMetricGroup() {
        return metricGroup;
    }

    public void performInitialSend() {
        if(publisherSettings.getInitialPublish() == 0)
            return;

        while(!isCancelled.get()) {
            Client client = null;
            try {
                int initialPubInflightLimit = Math.max(1000, inFlightLimit);

                FlowController flowController = new FlowController(initialPubInflightLimit, singleMessageBucketSize);
                boolean isBatchListener = false;
                if(publishMode == StreamPublishMode.SubEntryBatch) {
                    flowController.configureForBinaryProtocolBatches(instrumentMessagePayloads);
                    isBatchListener = true;
                }
                else
                    flowController.configureForBinaryProtocol(instrumentMessagePayloads);

                StreamPublisherListener initSendListener = new StreamPublisherListener(messageModel, metricGroup, flowController, instrumentMessagePayloads, isBatchListener);
                this.lastSentBatch = Instant.now();

                client = getClient(initSendListener);
                logger.info("Publisher " + publisherId + " opened connection to " + currentHost.getNodeName() + " for initial publish");

                int currentStream = 0;
                while (!isCancelled.get()) {
                    boolean lastSend = sentCount >= publisherSettings.getInitialPublish()-1;
                    boolean stillConnected = publish(client, flowController, currentStream, true, lastSend);
                    if(!stillConnected)
                        break;

                    sentCount++;
                    currentStream++;

                    if (currentStream > maxStream)
                        currentStream = 0;

                    if(lastSend)
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
                FlowController flowController = new FlowController(inFlightLimit, singleMessageBucketSize);
                boolean isBatchListener = false;
                if(maxBatchSize > 1 && publishMode == StreamPublishMode.SubEntryBatch) {
                    flowController.configureForBinaryProtocolBatches(instrumentMessagePayloads);
                    isBatchListener = true;
                }
                else
                    flowController.configureForBinaryProtocol(instrumentMessagePayloads);

                listener = new StreamPublisherListener(messageModel, metricGroup, flowController, instrumentMessagePayloads, isBatchListener);

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

                currentInFlightLimit = getAdjustedInFlightLimit(publisherSettings.getPublisherMode().getInFlightLimit());
                int currentStream = 0;
                boolean reconnect = false;
                this.lastSentBatch = Instant.now();

                while (!isCancelled.get() && !reconnect) {
                    boolean send = (sendLimit == 0 || (sendLimit > 0 && sentCount < sendLimit)) && !reconnect && !isCancelled.get();

                    if(send) {
                        boolean stillConnected;
                        if(instrumentMessagePayloads)
                            stillConnected = publish(client, flowController, currentStream, false, false);
                        else
                            stillConnected = uninstrumentedPublish(client, flowController, false, false);

                        if(!stillConnected)
                            break;

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

    private boolean publish(Client client,
                         FlowController flowController,
                         int currentStream,
                         boolean isInitialPublish,
                         boolean flushBatch) throws IOException, InterruptedException {
        Integer stream = publisherSettings.getStreams().get(currentStream);
        Long streamSeqNo = getAndIncrementStreamCounter(stream);

        if(maxBatchSize > 1 || maxBatchSizeBytes > 1) {
            accumulatedMessages.get(stream).add(streamSeqNo);
            pendingPublish++;

            boolean sendBatch = (maxBatchSize > 0 && pendingPublish >= maxBatchSize)
                    || (maxBatchSizeBytes > 0 && pendingPublish * messageSize >= maxBatchSizeBytes)
                    || (maxBatchWaitMs > 0 && Duration.between(lastSentBatch, Instant.now()).toMillis() > maxBatchWaitMs)
                    || flushBatch;

            if(sendBatch) {
                List<MessagePayload> payloads = new ArrayList<>();
                for(Integer streamKey : accumulatedMessages.keySet()) {
                    Queue<Long> seqNosToSend = accumulatedMessages.get(streamKey);
                    while(!seqNosToSend.isEmpty()) {
                        Long seqNoToSend = seqNosToSend.remove();
                        long timestamp = MessageUtils.getTimestamp();
                        MessagePayload mp = new MessagePayload(streamKey, seqNoToSend, timestamp);
                        payloads.add(mp);
                    }
                }

                if(publishMode == StreamPublishMode.SimpleBatch) {
                    List<byte[]> messageBodies = new ArrayList<>();
                    for(MessagePayload mp : payloads) {
                        byte[] body = getMessage(mp);
                        messageBodies.add(body);
                    }

                    if(!controlFlow(flowController, client, pendingPublish))
                        return false;

                    List<Long> seqNos = client.publishBinary(fixedStreamQueue, messageBodies);
                    int sent = seqNos.size();
                    for(int i=0; i<sent; i++)
                        registerPublish(isInitialPublish, flowController, seqNos.get(i), payloads.get(i), messageBodies.get(i));
                }
                else {
                    List<List<MessagePayload>> payloadBatches = new ArrayList<>();
                    List<MessagePayload> currPayloadBatch = new ArrayList<>();
                    payloadBatches.add(currPayloadBatch);

                    List<MessageBatch> batches = new ArrayList<>();
                    MessageBatch currBatch = new MessageBatch();
                    batches.add(currBatch);

                    int totalLength = 0;
                    int currBatchLength = 0;
                    int count = payloads.size();
                    for (int i = 0; i < count; i++) {
                        MessagePayload mp = payloads.get(i);
                        byte[] body = getMessage(mp);
                        totalLength += body.length;
                        currBatchLength += body.length;
                        currBatch.add(body);
                        currPayloadBatch.add(mp);

                        // if we've past the max individual batch size and we're not on the last message,
                        // then create a new batch
                        if (currBatchLength > maxSubEntryBytes && i < count - 1) {
                            MessageBatch newBatch = new MessageBatch();
                            batches.add(newBatch);
                            currBatch = newBatch;

                            List<MessagePayload> newPayloadBatch = new ArrayList<>();
                            payloadBatches.add(newPayloadBatch);
                            currPayloadBatch = newPayloadBatch;
                            currBatchLength = 0;
                        }
                    }

                    if(!controlFlow(flowController, client, batches.size()))
                        return false;

                    List<Long> batchNos = client.publishBatches(fixedStreamQueue, batches);
                    Map<Long, List<MessagePayload>> batchNosTracking = new HashMap<>();
                    for(int i=0; i<batchNos.size(); i++) {
                        batchNosTracking.put(batchNos.get(i), payloadBatches.get(i));
                    }
                    registerBatchPublish(isInitialPublish, flowController, batchNosTracking, payloads, totalLength);
                }
//                System.out.println("Publish batch " + batchNo);
                lastSentBatch = Instant.now();
                pendingPublish = 0;
            }
        }
        else {
            if(singleMessageBucketSize == 1) {
                if (!controlFlow(flowController, client, 1))
                    return false;
            }
            else {
                if((sentCount < singleMessageBucketSize && sentCount == 0)
                    || (sentCount >= singleMessageBucketSize && sentCount % singleMessageBucketSize == 0)) {
                    if (!controlFlow(flowController, client, 1))
                        return false;
                }
            }

            long timestamp = MessageUtils.getTimestamp();
            MessagePayload mp = new MessagePayload(stream, streamSeqNo, timestamp);

            byte[] body = getMessage(mp);

            long seqNo = client.publish(
                    fixedStreamQueue,
                    body
            );

            //System.out.println("PUBLISHED: " + seqNo);

            registerPublish(isInitialPublish, flowController, seqNo, mp, body);
        }

        return true;
    }

    private boolean uninstrumentedPublish(Client client,
                                          FlowController flowController,
                                          boolean isInitialPublish,
                                          boolean flushBatch) throws IOException, InterruptedException {
        if(maxBatchSize > 1 || maxBatchSizeBytes > 1) {
            pendingPublish++;

            boolean sendBatch =
                    (maxBatchSize > 0 && pendingPublish >= maxBatchSize)
                    || (maxBatchSizeBytes > 0 && pendingPublish * messageSize >= maxBatchSizeBytes)
                    || (maxBatchWaitMs > 0 && Duration.between(lastSentBatch, Instant.now()).toMillis() > maxBatchWaitMs && pendingPublish > 0)
                    || flushBatch;

            if(sendBatch) {
                if(publishMode == StreamPublishMode.SimpleBatch) {
                    List<byte[]> messageBodies = new ArrayList<>();
                    for(int i=0; i<pendingPublish; i++) {
                        byte[] body = getMessage(null);
                        messageBodies.add(body);
                    }

                    if(!controlFlow(flowController, client, pendingPublish))
                        return false;

                    List<Long> seqNos = client.publishBinary(fixedStreamQueue, messageBodies);
                    int sent = seqNos.size();
                    for(int i=0; i<sent; i++)
                        registerUninstrumentedPublish(isInitialPublish, flowController, seqNos.get(i), messageBodies.get(i));
                }
                else {
                    List<Integer> batchSizes = new ArrayList<>();
                    List<MessageBatch> batches = new ArrayList<>();
                    MessageBatch currBatch = new MessageBatch();
                    batches.add(currBatch);

                    int totalLength = 0;
                    int currBatchLength = 0;
                    int currBatchSize = 0;
                    for (int i = 0; i < pendingPublish; i++) {
                        byte[] body = getMessage(null);
                        totalLength += body.length;
                        currBatchLength += body.length;
                        currBatch.add(body);
                        currBatchSize++;

                        // if we've past the max individual batch size and we're not on the last message,
                        // then create a new batch
                        if (currBatchLength > maxSubEntryBytes && i < pendingPublish - 1) {
                            MessageBatch newBatch = new MessageBatch();
                            batches.add(newBatch);
                            currBatch = newBatch;
                            batchSizes.add(currBatchSize);
                            currBatchLength = 0;
                            currBatchSize = 0;
                        }
                    }
                    batchSizes.add(currBatchSize++);

                    if(!controlFlow(flowController, client, batches.size()))
                        return false;

                    List<Long> batchNos = client.publishBatches(fixedStreamQueue, batches);
                    Map<Long, Integer> batchNosTracking = new HashMap<>();
                    for(int i=0; i<batchNos.size(); i++) {
                        batchNosTracking.put(batchNos.get(i), batchSizes.get(i));
                    }
                    registerUninstrumentedBatchPublish(isInitialPublish, flowController, batchNosTracking, totalLength);
                }
//                System.out.println("Publish batch " + batchNo);
                lastSentBatch = Instant.now();
                pendingPublish = 0;
            }
        }
        else {
            if(!controlFlow(flowController, client, 1))
                return false;

            byte[] body = getMessage(null);
            long seqNo = client.publish(
                    fixedStreamQueue,
                    body
            );

            //System.out.println("PUBLISHED: " + seqNo);

            registerUninstrumentedPublish(isInitialPublish, flowController, seqNo, body);
        }

        return true;
    }

    private boolean controlFlow(FlowController flowController,
                                Client client,
                                int count) throws InterruptedException {
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

            int permits;
            if(singleMessageBucketSize == 1)
                permits = count;
            else {
                if (count < singleMessageBucketSize)
                    permits = 1;
                else if (count % singleMessageBucketSize == 0)
                    permits = count / singleMessageBucketSize;
                else
                    permits = count / singleMessageBucketSize + 1;
            }

            // keep trying to acquire until the cancelation or connection dies
            while(!isCancelled.get() && !flowController.tryGetSendPermits(permits, 1000, TimeUnit.MILLISECONDS)) {
                if (!client.isOpen()) {
                    return false;
                }

                listener.checkForTimeouts(confirmTimeoutThresholdNs);
            }
        }

        return true;
    }

    private byte[] getMessage(MessagePayload mp) throws IOException {
        if(instrumentMessagePayloads && mp != null)
            return messageGenerator.getMessageBytes(mp);
        else
            return messageGenerator.getUninstrumentedMessageBytes();
    }

    private void registerPublish(boolean isInitialPublish,
                                 FlowController flowController,
                                 Long seqNo,
                                 MessagePayload mp,
                                 byte[] body) {
        if (isInitialPublish || this.useConfirms) {
            flowController.trackBinaryProtocolMessage(seqNo, mp);
        }
        else {
            messageModel.sent(mp);
        }

        metricGroup.increment(MetricType.PublisherSentMessage);
        metricGroup.increment(MetricType.PublisherSentBytes, body.length);
    }

    private void registerUninstrumentedPublish(boolean isInitialPublish,
                                 FlowController flowController,
                                 Long seqNo,
                                 byte[] body) {
        if (isInitialPublish || this.useConfirms) {
            flowController.trackUninstrumentedBinaryProtocol(seqNo, 1);
        }

        metricGroup.increment(MetricType.PublisherSentMessage);
        metricGroup.increment(MetricType.PublisherSentBytes, body.length);
    }

    private void registerBatchPublish(boolean isInitialPublish,
                                      FlowController flowController,
                                      Map<Long, List<MessagePayload>> batchNosTracking,
                                      List<MessagePayload> messagePayloads,
                                      long totalLength) {
        int msgCount = messagePayloads.size();
        if (isInitialPublish || this.useConfirms) {
            for(Long batchNo : batchNosTracking.keySet())
                flowController.trackBinaryProtocolBatch(batchNo, batchNosTracking.get(batchNo));
        }
        else if(this.instrumentMessagePayloads){
            for(MessagePayload mp : messagePayloads)
                messageModel.sent(mp);
        }

        if(msgCount > 0) {
            metricGroup.increment(MetricType.PublisherSentMessage, msgCount);
            metricGroup.increment(MetricType.PublisherSentBytes, totalLength);
        }
    }

    private void registerUninstrumentedBatchPublish(boolean isInitialPublish,
                                      FlowController flowController,
                                      Map<Long, Integer> batchNosTracking,
                                      long totalLength) {
        if (isInitialPublish || this.useConfirms) {
            for(Long batchNo : batchNosTracking.keySet())
                flowController.trackUninstrumentedBinaryProtocol(batchNo, batchNosTracking.get(batchNo));
        }

        int msgCount = batchNosTracking.values().stream().reduce(Integer::sum).get();
        if(msgCount > 0) {
            metricGroup.increment(MetricType.PublisherSentMessage, msgCount);
            metricGroup.increment(MetricType.PublisherSentBytes, totalLength);
        }
    }

    private Client getClient(StreamPublisherListener listener) {
        currentHost = getBrokerToConnectTo();
        return new Client(new Client.ClientParameters()
                .host(currentHost.getIp())
                .port(currentHost.getStreamPort())
                .username(connectionSettings.getUser())
                .password(connectionSettings.getPassword())
                .virtualHost(connectionSettings.getVhost())
                .publishConfirmListener(listener)
                .publishErrorListener(listener)
                .codec(new SimpleCodec())
                .requestedHeartbeat(Duration.ofSeconds(3600))
        );
    }

    private Broker getBrokerToConnectTo() {
        while(!isCancelled.get()) {
            Broker host = queueHosts.getHost(fixedStreamQueue);

            if(host != null) {
                return host;
            } else {
                logger.info(this.publisherId + " unable to identify the queue to connect to");
                ClientUtils.waitFor(1000, isCancelled);
            }
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

    private int getAdjustedInFlightLimit(int original) {
        if(publishMode == StreamPublishMode.SubEntryBatch || singleMessageBucketSize == 1)
            return original;
        else if(original < singleMessageBucketSize)
            return 1;

        return original / singleMessageBucketSize;
    }
}
