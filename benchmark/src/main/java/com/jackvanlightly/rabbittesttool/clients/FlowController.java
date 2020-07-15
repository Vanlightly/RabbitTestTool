package com.jackvanlightly.rabbittesttool.clients;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

// TODO
public class FlowController extends Semaphore {

    ConcurrentMap<Long, MessagePayload> pendingBinaryProtocolMessageConfirms;
    ConcurrentMap<Long, List<MessagePayload>> pendingBinaryProtocolBatchConfirms;
    ConcurrentNavigableMap<Long, MessagePayload> pendingAmqpConfirms;
    int singleMessageBucketSize;

    public FlowController(int inflightLimit, int singleMessageBucketSize) {
        super(inflightLimit);
        this.singleMessageBucketSize = singleMessageBucketSize;
    }

    public int getSingleMessageBucketSize() {
        return singleMessageBucketSize;
    }

    public void configureForBinaryProtocol() {
        this.pendingBinaryProtocolMessageConfirms = new ConcurrentHashMap<>();
    }

    public void configureForBinaryProtocolBatches() {
        this.pendingBinaryProtocolBatchConfirms = new ConcurrentHashMap<>();
    }

    public void configureForAmqp() {
        this.pendingAmqpConfirms = new ConcurrentSkipListMap<>();
    }

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
        if(isTracked(seqNo))
            pendingBinaryProtocolMessageConfirms.put(seqNo, mp);
    }

    public void trackBinaryProtocolBatch(long seqNo, List<MessagePayload> mps) {
        pendingBinaryProtocolBatchConfirms.put(seqNo, mps);
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
        if (isTracked(seqNo)) {
            MessagePayload mp = pendingBinaryProtocolMessageConfirms.remove(seqNo);
            if(mp != null) {
                this.release(1);

                return mp;
            }
            else {
                // wasn't added to tracking yet - race condition
                return null;
            }
        } else {
            // we didn't add it to the tracking due to bucket size
            // but we need to differentiate it not being tracked vs it not yet added to tracking
            // due to stream client race condition
            return new MessagePayload(0, -1L, 0);
        }
    }

    public List<MessagePayload> confirmBinaryProtocolBatch(long seqNo) {
        List<MessagePayload> mps = pendingBinaryProtocolBatchConfirms.remove(seqNo);
        this.release(1);
        return mps;
    }

    public int discardTimedOutBatches(long confirmTimeoutThresholdNs) {
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

    public int discardTimedOutBinaryProtocolMessages(long confirmTimeoutThresholdNs) {
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

    public int discardTimedOutAmqpMessages(long confirmTimeoutThresholdNs) {
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

    public void increaseInflightLimit(int amount) {
        this.release(amount);
    }

    public void decreaseInflightLimit(int amount) {
        this.reducePermits(-amount);
    }

    public int getPendingCount() {
        if(pendingAmqpConfirms != null)
            return pendingAmqpConfirms.size();
        else if(pendingBinaryProtocolBatchConfirms != null)
            return pendingBinaryProtocolBatchConfirms.size();
        else if(pendingBinaryProtocolMessageConfirms != null)
            return pendingBinaryProtocolMessageConfirms.size();

        return -1;
    }

    private boolean isTracked(long seqNo) {
        return singleMessageBucketSize == 1
                || seqNo == 0
                || (seqNo >= singleMessageBucketSize && seqNo % singleMessageBucketSize == 0);
    }
}
