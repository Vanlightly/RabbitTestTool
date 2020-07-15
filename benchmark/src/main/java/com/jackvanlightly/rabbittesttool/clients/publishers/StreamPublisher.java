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

    // for sequence round robin
    int maxSequence;
    Map<Integer, Long> sequenceCounter;

    // for when publishing to one or more streams
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

    int checkHostInterval = 100000;
    MessageGenerator messageGenerator;

    // for publishing rate
    boolean rateLimit;
    RateLimiter rateLimiter;
    long sentCount;
    long sendLimit;

    // batching
    Map<Integer, Queue<Long>> accumulatedMessages;
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

        this.maxSequence = publisherSettings.getSequences().size()-1;

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
        for(Integer sequence : publisherSettings.getSequences())
            this.accumulatedMessages.put(sequence, new ArrayDeque<>());

        messageGenerator = new MessageGenerator();
        messageGenerator.setBaseMessageSize(publisherSettings.getMessageSize());

        this.inFlightLimit = getAdjustedInFlightLimit(this.publisherSettings.getPublisherMode().getInFlightLimit());
        this.messageSize = this.publisherSettings.getMessageSize();
        this.sendLimit = this.publisherSettings.getMessageLimit();
        this.rateLimiter = new RateLimiter();
        this.rateLimit = this.publisherSettings.getPublishRatePerSecond() > 0;
        initializeSequenceCounter();

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
                boolean isSubEntryBatchListener = false;
                if(publishMode == StreamPublishMode.SubEntryBatch) {
                    flowController.configureForBinaryProtocolBatches();
                    isSubEntryBatchListener = true;
                }
                else
                    flowController.configureForBinaryProtocol();

                StreamPublisherListener initSendListener = new StreamPublisherListener(messageModel, metricGroup, flowController, isSubEntryBatchListener);
                this.lastSentBatch = Instant.now();

                client = getClient(initSendListener);
                logger.info("Publisher " + publisherId + " opened connection to " + currentHost.getNodeName() + " for initial publish");

                int currentSequence = 0;
                while (!isCancelled.get()) {
                    boolean lastSend = sentCount >= publisherSettings.getInitialPublish()-1;
                    boolean stillConnected = publish(client, flowController, currentSequence, true, lastSend);
                    if(!stillConnected)
                        break;

                    sentCount++;
                    currentSequence++;

                    if (currentSequence > maxSequence)
                        currentSequence = 0;

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
                    flowController.configureForBinaryProtocolBatches();
                    isBatchListener = true;
                }
                else
                    flowController.configureForBinaryProtocol();

                listener = new StreamPublisherListener(messageModel, metricGroup, flowController, isBatchListener);

                client = getClient(listener);

                String[] streams = new String[streamQueues.size()];
                for(int i=0; i<streamQueues.size();i++)
                    streams[i] = streamQueues.get(i);

                messageModel.clientConnected(publisherId);
                logger.info("Publisher " + publisherId + " opened connection to " + currentHost.getNodeName() + ". Has sequences: " + String.join(",", publisherSettings.getSequences().stream().map(x -> String.valueOf(x)).collect(Collectors.toList())));

                if (rateLimit) {
                    this.rateLimiter.configureRateLimit(publisherSettings.getPublishRatePerSecond());
                    this.checkHostInterval = this.rateLimiter.getLimitInSecond()*30;
                }

                currentInFlightLimit = getAdjustedInFlightLimit(publisherSettings.getPublisherMode().getInFlightLimit());
                int currentSequence = 0;
                boolean reconnect = false;
                this.lastSentBatch = Instant.now();

                while (!isCancelled.get() && !reconnect) {
                    boolean send = (sendLimit == 0 || (sendLimit > 0 && sentCount < sendLimit)) && !reconnect && !isCancelled.get();

                    if(send) {
                        boolean stillConnected = publish(client, flowController, currentSequence, false, false);

                        if(!stillConnected)
                            break;

                        sentCount++;

                        currentSequence++;
                        if (currentSequence > maxSequence)
                            currentSequence = 0;

                        if (rateLimit)
                            rateLimiter.rateLimit();

                        if(sentCount % this.checkHostInterval == 0) {
                            // when publisher configured to follow a leader around, when the leader changes
                            // the publisher needs to close and reconnect
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
                         int currentSequence,
                         boolean isInitialPublish,
                         boolean flushBatch) throws IOException, InterruptedException {
        Integer sequence = publisherSettings.getSequences().get(currentSequence);
        Long sequenceSeqNo = getAndIncrementSequenceCounter(sequence);

        if(maxBatchSize > 1 || maxBatchSizeBytes > 1)
            return publishInBatch(client, flowController, sequence, sequenceSeqNo, isInitialPublish, flushBatch);
        else
            return sendSingleMessage(client, flowController, sequence, sequenceSeqNo, isInitialPublish);
    }

    private boolean publishInBatch(Client client,
                                   FlowController flowController,
                                   Integer sequence,
                                   Long sequenceSeqNo,
                                   boolean isInitialPublish,
                                   boolean flushBatch) throws IOException, InterruptedException {
        accumulatedMessages.get(sequence).add(sequenceSeqNo);
        pendingPublish++;

        boolean sendBatch = (maxBatchSize > 0 && pendingPublish >= maxBatchSize)
                || (maxBatchSizeBytes > 0 && pendingPublish * messageSize >= maxBatchSizeBytes)
                || (maxBatchWaitMs > 0 && Duration.between(lastSentBatch, Instant.now()).toMillis() > maxBatchWaitMs)
                || flushBatch;

        if(sendBatch) {
            // here we create a single list of message payloads based on the accumulated messages
            // we can be sending more than one monotonic sequence
            List<MessagePayload> payloads = new ArrayList<>();
            for(Integer sequenceKey : accumulatedMessages.keySet()) {
                Queue<Long> seqNosToSend = accumulatedMessages.get(sequenceKey);
                while(!seqNosToSend.isEmpty()) {
                    Long seqNoToSend = seqNosToSend.remove();
                    long timestamp = MessageUtils.getTimestamp();
                    MessagePayload mp = new MessagePayload(sequenceKey, seqNoToSend, timestamp);
                    payloads.add(mp);
                }
            }

            // send the messages as batches
            boolean stillConnected;
            if(publishMode == StreamPublishMode.SimpleBatch)
                stillConnected = sendAsSimpleBatches(client, flowController, isInitialPublish, payloads);
            else
                stillConnected = publishAsSubEntries(client, flowController, isInitialPublish, payloads);

            if(stillConnected) {
                lastSentBatch = Instant.now();
                pendingPublish = 0;
            }

            return stillConnected;
        }

        return true;
    }

    private boolean sendAsSimpleBatches(Client client,
                                        FlowController flowController,
                                        boolean isInitialPublish,
                                        List<MessagePayload> payloads) throws IOException, InterruptedException {
        List<byte[]> messageBodies = new ArrayList<>();
        for(MessagePayload mp : payloads) {
            byte[] body = getMessage(mp);
            messageBodies.add(body);
        }

        // ensure inflight limit not breached
        if(!controlFlow(flowController, client, pendingPublish))
            return false;

        // send the messages as simple batches
        List<Long> seqNos = client.publishBinary(fixedStreamQueue, messageBodies);
        int sent = seqNos.size();

        // track the messages
        for(int i=0; i<sent; i++)
            trackPublish(isInitialPublish, flowController, seqNos.get(i), payloads.get(i), messageBodies.get(i));

        return true;
    }

    private boolean publishAsSubEntries(Client client,
                                        FlowController flowController,
                                        boolean isInitialPublish,
                                        List<MessagePayload> payloads) throws IOException, InterruptedException {

        // split up the payloads into one or more sub-entries based on the max size in bytes for each sub entry
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

        // ensure we don't exceed thr inflight sub-entries limit
        if(!controlFlow(flowController, client, batches.size()))
            return false;

        // send the sub entries
        List<Long> batchNos = client.publishBatches(fixedStreamQueue, batches);
        Map<Long, List<MessagePayload>> batchNosTracking = new HashMap<>();
        for(int i=0; i<batchNos.size(); i++)
            batchNosTracking.put(batchNos.get(i), payloadBatches.get(i));

        // track the sub-entries
        trackBatchPublish(isInitialPublish, flowController, batchNosTracking, payloads, totalLength);

        return true;
    }

    private boolean sendSingleMessage(Client client,
                                      FlowController flowController,
                                      Integer sequence,
                                      Long sequenceSeqNo,
                                      boolean isInitialPublish) throws IOException, InterruptedException {
        if(isFirstInBucket()) {
            if (!controlFlow(flowController, client, 1))
                return false;
        }

        long timestamp = MessageUtils.getTimestamp();
        MessagePayload mp = new MessagePayload(sequence, sequenceSeqNo, timestamp);
        byte[] body = getMessage(mp);

        long seqNo = client.publish(
                fixedStreamQueue,
                body
        );

        trackPublish(isInitialPublish, flowController, seqNo, mp, body);
        return true;
    }

    private boolean isFirstInBucket() {
        return singleMessageBucketSize == 1
                || (sentCount < singleMessageBucketSize && sentCount == 0)
                || (sentCount >= singleMessageBucketSize && sentCount % singleMessageBucketSize == 0);
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

            // keep trying to acquire until the cancellation or connection dies
            // check if any confirms have exceeded the timeout
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
        return messageGenerator.getMessageBytes(mp);
    }

    private void trackPublish(boolean isInitialPublish,
                              FlowController flowController,
                              Long seqNo,
                              MessagePayload mp,
                              byte[] body) {
        if (isInitialPublish || this.useConfirms) {
            flowController.trackBinaryProtocolMessage(seqNo, mp);
        }
        else {
            // without confirms, we add it to the model now rather than when the confirm is received
            messageModel.sent(mp);
        }

        metricGroup.increment(MetricType.PublisherSentMessage);
        metricGroup.increment(MetricType.PublisherSentBytes, body.length);
    }

    private void trackBatchPublish(boolean isInitialPublish,
                                   FlowController flowController,
                                   Map<Long, List<MessagePayload>> batchNosTracking,
                                   List<MessagePayload> messagePayloads,
                                   long totalLength) {
        int msgCount = messagePayloads.size();
        if (isInitialPublish || this.useConfirms) {
            for(Long batchNo : batchNosTracking.keySet())
                flowController.trackBinaryProtocolBatch(batchNo, batchNosTracking.get(batchNo));
        }
        else {
            for(MessagePayload mp : messagePayloads)
                messageModel.sent(mp);
        }

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

    private void initializeSequenceCounter() {
        sequenceCounter = new HashMap<>();

        for(Integer sequence : publisherSettings.getSequences())
            sequenceCounter.put(sequence, 0L);
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

    private Long getAndIncrementSequenceCounter(Integer sequence) {
        Long current = sequenceCounter.get(sequence);
        sequenceCounter.put(sequence, current +  1);
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
