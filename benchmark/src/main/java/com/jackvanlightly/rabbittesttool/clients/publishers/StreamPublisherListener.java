package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.FlowController;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import com.jackvanlightly.rabbittesttool.clients.MessageUtils;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.MetricGroup;
import com.jackvanlightly.rabbittesttool.statistics.MetricType;
import com.rabbitmq.stream.impl.Client;

import java.util.*;

public class StreamPublisherListener implements Client.PublishConfirmListener, Client.PublishErrorListener {

    BenchmarkLogger logger;
    MessageModel messageModel;
    MetricGroup metricGroup;
    FlowController flowController;
    boolean isSubEntryBatchListener;

    long lastRecordedLatency;
    long summedLatency = 0;
    long latencyMeasurements = 0;

    public StreamPublisherListener(MessageModel messageModel,
                                   MetricGroup metricGroup,
                                   FlowController flowController,
                                   boolean isSubEntriesBatchListener) {
        this.logger = new BenchmarkLogger("PUBLISHER");
        this.messageModel = messageModel;
        this.metricGroup = metricGroup;
        this.flowController = flowController;
        this.isSubEntryBatchListener = isSubEntriesBatchListener;
    }

    public void checkForTimeouts(long confirmTimeoutThresholdNs) {
        if(isSubEntryBatchListener) {
            int removed = flowController.discardTimedOutBatches(confirmTimeoutThresholdNs);
            if (removed > 0) {
                logger.info("Discarded " + removed + " pending batches due to timeout.");
            }
        }
        else {
            int removed = flowController.discardTimedOutBinaryProtocolMessages(confirmTimeoutThresholdNs);
            if (removed > 0) {
                logger.info("Discarded " + removed + " pending messages due to timeout.");
            }
        }
    }

    public int getPendingConfirmCount() {
        return flowController.getPendingCount();
    }

    // confirmListener
    @Override
    public void handle(byte publishererId, long seqNo) {
        if(isSubEntryBatchListener)
            handleBatch(seqNo);
        else
            handleSingle(seqNo);
    }

    // publishErrorListener
    @Override
    public void handle(byte publishererId, long seqNo, short i) {
        if(isSubEntryBatchListener)
            handleBatch(seqNo, i);
        else
            handleSingle(seqNo, i);
    }

    private void handleSingle(long seqNo) {
        int numConfirms = 0;
        long currentTime = MessageUtils.getTimestamp();

        // this loop is due to a current race condition where the
        // confirm is faster than adding the seq no to the map
        int attempts = 0;
        int limit = 10;
        while(attempts <= limit){
            attempts++;
            MessagePayload mp = flowController.confirmBinaryProtocolMessage(seqNo);
            if (mp != null) {
                // we didn't track it due to bucket size
                if(!isTracked(mp))
                    return;

                // we only track one message per bucket size, so compensate the number of confirms here
                numConfirms = 1*flowController.getSingleMessageBucketSize();
                messageModel.sent(mp);
                long latency = MessageUtils.getDifference(mp.getTimestamp(), currentTime);
                recordLatency(latency, currentTime);
            } else {
                // race condition! try again!
                if (attempts <= limit) {
                    ClientUtils.waitFor(5);
                    continue;
                }
                numConfirms = 0;
            }
            break;
        }

        if (numConfirms > 0) {
            metricGroup.increment(MetricType.PublisherConfirm, numConfirms);
        }
    }

    private boolean isTracked(MessagePayload mp) {
        // kind of a hack, but -1 represents a payload that was sent but not tracked
        return mp.getSequenceNumber() == -1;
    }

    private void recordLatency(long latency, long now) {
        summedLatency+=latency;
        latencyMeasurements++;

        if(now-lastRecordedLatency > 100000000) {
            long avgLag = summedLatency/latencyMeasurements;
            metricGroup.add(MetricType.PublisherConfirmLatencies, avgLag);
            summedLatency = 0;
            latencyMeasurements = 0;
            lastRecordedLatency = now;
        }
    }

    private void handleSingle(long seqNo, short i) {
        flowController.confirmBinaryProtocolMessage(seqNo);
        metricGroup.increment(MetricType.PublisherNacked, 1);
    }

    private void handleBatch(long seqNo) {
        int numConfirms = 0;
        // this loop is due to a current race condition where the
        // confirm is faster than adding the seq no to the map
        int attempts = 0;
        int limit = 10;
        while(attempts <= limit) {
            attempts++;

            long currentTime = MessageUtils.getTimestamp();
            List<MessagePayload> confirmedList = flowController.confirmBinaryProtocolBatch(seqNo);

            // we track all sub-entries, so if we don';t have it, then it's the race condition
            if(confirmedList == null && attempts <= limit) {
                ClientUtils.waitFor(5);
                continue;
            }

            // send all messages to the model
            for (MessagePayload mp : confirmedList)
                messageModel.sent(mp);

            numConfirms = confirmedList.size();
            if (numConfirms > 0) {
                long latency = MessageUtils.getDifference(confirmedList.get(0).getTimestamp(), currentTime);
                metricGroup.add(MetricType.PublisherConfirmLatencies, latency);
                metricGroup.increment(MetricType.PublisherConfirm, numConfirms);
            }

            break;
        }
    }

    private void handleBatch(long seqNo, short i) {
        List<MessagePayload> messagePayloads = flowController.confirmBinaryProtocolBatch(seqNo);
        if (messagePayloads != null) {
            metricGroup.increment(MetricType.PublisherNacked, messagePayloads.size());
        }
    }
}
