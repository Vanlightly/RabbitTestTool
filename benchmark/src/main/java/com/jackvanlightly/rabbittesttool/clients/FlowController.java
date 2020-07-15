package com.jackvanlightly.rabbittesttool.clients;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

// TODO
public class FlowController extends Semaphore {

    boolean instrumentedMessages;
    ConcurrentMap<Long, MessagePayload> pendingBinaryProtocolMessageConfirms;
    ConcurrentMap<Long, List<MessagePayload>> pendingBinaryProtocolBatchConfirms;
    ConcurrentMap<Long, Integer> pendingUninstrumentedBinaryProtocolConfirms;
    ConcurrentNavigableMap<Long, MessagePayload> pendingAmqpConfirms;
    int singleMessageBucketSize;

    public FlowController(int inflightLimit, int singleMessageBucketSize) {
        super(inflightLimit);
        this.singleMessageBucketSize = singleMessageBucketSize;
    }

    public int getSingleMessageBucketSize() {
        return singleMessageBucketSize;
    }

    public void configureForBinaryProtocol(boolean instrumentedMessages) {
        if(instrumentedMessages)
            this.pendingBinaryProtocolMessageConfirms = new ConcurrentHashMap<>();
        else
            this.pendingUninstrumentedBinaryProtocolConfirms = new ConcurrentHashMap<>();

        this.instrumentedMessages = instrumentedMessages;
    }

    public void configureForBinaryProtocolBatches(boolean instrumentedMessages) {
        if(instrumentedMessages)
            this.pendingBinaryProtocolBatchConfirms = new ConcurrentHashMap<>();
        else
            this.pendingUninstrumentedBinaryProtocolConfirms = new ConcurrentHashMap<>();

        this.instrumentedMessages = instrumentedMessages;
    }

    public void configureForAmqp() {
        this.pendingAmqpConfirms = new ConcurrentSkipListMap<>();
        this.instrumentedMessages = true;
    }

//    public static FlowController getAmqpFlowController(int inflightLimit) {
//        FlowController fc = new FlowController(inflightLimit);
//        fc.configureForAmqp();
//        return fc;
//    }
//
//    public static FlowController getBinaryProtocolFlowController(int inflightLimit, boolean instrumentedMessages) {
//        FlowController fc = new FlowController(inflightLimit);
//        fc.configureForBinaryProtocol(instrumentedMessages);
//        return fc;
//    }
//
//    public static FlowController getBinaryProtocolBatchFlowController(int inflightLimit, boolean instrumentedMessages) {
//        FlowController fc = new FlowController(inflightLimit);
//        fc.configureForBinaryProtocolBatches(instrumentedMessages);
//        return fc;
//    }

    public void getSendPermit() throws InterruptedException {
        this.acquire();
    }

    public boolean tryGetSendPermit(long l, TimeUnit timeUnit) throws InterruptedException {
        return this.tryAcquire(l, timeUnit);
    }

    public boolean tryGetSendPermits(int count, long l, TimeUnit timeUnit) throws InterruptedException {
        return this.tryAcquire(count, l, timeUnit);
    }

    public void returnSendPermits(int count) {
        this.release(count);
    }

    public void trackAmqpMessage(long seqNo, MessagePayload mp) {
        pendingAmqpConfirms.put(seqNo, mp);
    }

    public void trackBinaryProtocolMessage(long seqNo, MessagePayload mp) {
        if(singleMessageBucketSize == 1) {
            pendingBinaryProtocolMessageConfirms.put(seqNo, mp);
        } else if(seqNo == 0 || (seqNo >= singleMessageBucketSize && seqNo % singleMessageBucketSize == 0)) {
            // only track one message per bucket size
            pendingBinaryProtocolMessageConfirms.put(seqNo, mp);
        }
    }

    public void trackBinaryProtocolBatch(long seqNo, List<MessagePayload> mps) {
        pendingBinaryProtocolBatchConfirms.put(seqNo, mps);
    }

    public void trackUninstrumentedBinaryProtocol(long seqNo, int messageCount) {
        if(messageCount == 1) {
            if(singleMessageBucketSize == 1)
                pendingUninstrumentedBinaryProtocolConfirms.put(seqNo, messageCount);
            else if(seqNo == 0 || (seqNo >= singleMessageBucketSize && seqNo % singleMessageBucketSize == 0)) {
                // only track one message per bucket size
                pendingUninstrumentedBinaryProtocolConfirms.put(seqNo, messageCount);
            }
        }
        else {
            pendingUninstrumentedBinaryProtocolConfirms.put(seqNo, messageCount);
        }
    }

    public List<MessagePayload> confirmAmqpMessages(long seqNo, boolean multiple) {
        List<MessagePayload> confirmedPayloads = null;
        int numConfirms;
        if (multiple) {
            ConcurrentNavigableMap<Long, MessagePayload> confirmed = pendingAmqpConfirms.headMap(seqNo, true);
            numConfirms = confirmed.size();
            confirmedPayloads = confirmed.values().stream().collect(Collectors.toList());
            confirmed.clear();
        } else {
            MessagePayload mp = pendingAmqpConfirms.remove(seqNo);
            numConfirms = 1;
            confirmedPayloads = new ArrayList<>();
            confirmedPayloads.add(mp);
        }
        this.release(numConfirms);

        return confirmedPayloads;
    }

    public MessagePayload confirmBinaryProtocolMessage(long seqNo) {
        MessagePayload mp = pendingBinaryProtocolMessageConfirms.remove(seqNo);
        if(mp != null) {
            if (singleMessageBucketSize == 1) {
                this.release(1);
            } else if (seqNo == 0 || (seqNo >= singleMessageBucketSize && seqNo % singleMessageBucketSize == 0)) {
                // flow control is based on one message per bucket
                this.release(1);
            }
            return mp;
        }
        else if((seqNo < singleMessageBucketSize && seqNo != 0) ||
                (seqNo >= singleMessageBucketSize && seqNo % singleMessageBucketSize != 0)) {
            // we didn't add it to the tracking due to bucket size
            // but we need to differentiate it not being tracked vs it not yet added to tracking
            // due to stream client race condition
            return new MessagePayload(0, -1L, 0);
        }

        // wasn't added to tracking yet
        return null;
    }

    public List<MessagePayload> confirmBinaryProtocolBatch(long seqNo) {
        List<MessagePayload> mps = pendingBinaryProtocolBatchConfirms.remove(seqNo);
        this.release(1);
        return mps;
    }

    public int confirmUninstrumentedBinaryProtocol(long seqNo) {
        // this is weird ugly logic, but required for current race condition in stream client
        // when that is fixed, this can be simplified
        Integer batchSize = pendingUninstrumentedBinaryProtocolConfirms.remove(seqNo);
        if(batchSize != null) {
            if(singleMessageBucketSize == 1) {
                this.release(1);
                return batchSize;
            }
            else {
                // if we are using buckets, then this represents the bucket size worth of messages
                this.release(1);
                return batchSize* singleMessageBucketSize;
            }
        }
        else if((seqNo < singleMessageBucketSize && seqNo != 0) ||
                    (seqNo >= singleMessageBucketSize && seqNo % singleMessageBucketSize != 0)) {
            // we didn't add it to the tracking due to bucket size
            return 0;
        }

        // not added to tracking yet
        return -1;
    }

    public int discardTimedOutBatches(long confirmTimeoutThresholdNs) {
        if(instrumentedMessages) {
            int removed = 0;
            Long nanoNow = System.nanoTime();
            for (Map.Entry<Long, List<MessagePayload>> mps : pendingBinaryProtocolBatchConfirms.entrySet()) {
                for (MessagePayload mp : mps.getValue()) {
                    if (nanoNow - mp.getTimestamp() > confirmTimeoutThresholdNs) {
                        List<MessagePayload> mayBeRemoved = pendingBinaryProtocolBatchConfirms.remove(mps.getKey());
                        if(mayBeRemoved != null)
                            returnSendPermits(1);
                        removed++;
                    }
                }
            }

            return removed;
        }

        return 0;
    }

    public int discardTimedOutBinaryProtocolMessages(long confirmTimeoutThresholdNs) {
        if(instrumentedMessages) {
            int removed = 0;
            Long nanoNow = System.nanoTime();
            for (Map.Entry<Long, MessagePayload> mpEntry : pendingBinaryProtocolMessageConfirms.entrySet()) {
                MessagePayload mp = mpEntry.getValue();
                if (nanoNow - mp.getTimestamp() > confirmTimeoutThresholdNs) {
                    MessagePayload mayBeRemoved = pendingBinaryProtocolMessageConfirms.remove(mpEntry.getKey());
                    if(mayBeRemoved != null)
                        returnSendPermits(1);
                    removed++;
                }
            }

            return removed;
        }

        return 0;
    }

    public int discardTimedOutAmqpMessages(long confirmTimeoutThresholdNs) {
        if(instrumentedMessages) {
            int removed = 0;
            Long nanoNow = System.nanoTime();
            for (Map.Entry<Long, MessagePayload> mp : pendingAmqpConfirms.entrySet()) {
                if (nanoNow - mp.getValue().getTimestamp() > confirmTimeoutThresholdNs) {
                    MessagePayload mayBeRemoved = pendingAmqpConfirms.remove(mp.getKey());
                    if(mayBeRemoved != null) {
                        returnSendPermits(1);
                        removed++;
                    }
                }
            }

            return removed;
        }

        return 0;
    }

    public void increaseInflightLimit(int amount) {
        this.release(amount);
    }

    public void decreaseInflightLimit(int amount) {
        this.reducePermits(-amount);
    }

    public int getPendingCount() {
        if(pendingAmqpConfirms != null)
            return pendingAmqpConfirms.size();
        else if(pendingUninstrumentedBinaryProtocolConfirms != null)
            return pendingUninstrumentedBinaryProtocolConfirms.size();
        else if(pendingBinaryProtocolBatchConfirms != null)
            return pendingBinaryProtocolBatchConfirms.size();
        else if(pendingBinaryProtocolMessageConfirms != null)
            return pendingBinaryProtocolMessageConfirms.size();

        return -1;
    }
}
