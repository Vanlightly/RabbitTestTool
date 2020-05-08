package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import com.jackvanlightly.rabbittesttool.clients.MessageUtils;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.PublisherGroupStats;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.ReturnListener;

import java.util.*;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PublisherListener implements ConfirmListener, ReturnListener, BlockedListener {

    private BenchmarkLogger logger;
    private MessageModel messageModel;
    private PublisherGroupStats publisherGroupStats;
    private ConcurrentNavigableMap<Long,MessagePayload> pendingConfirms;
    private Semaphore inflightSemaphore;
    private Set<MessagePayload> undeliverable;
    private AtomicInteger pendingConfirmCount;
    private Lock timeoutLock;

    public PublisherListener(MessageModel messageModel,
                             PublisherGroupStats publisherGroupStats,
                             ConcurrentNavigableMap<Long, MessagePayload> pendingConfirms,
                             Semaphore inflightSemaphore) {
        this.logger = new BenchmarkLogger("PUBLISHER");
        this.messageModel = messageModel;
        this.publisherGroupStats = publisherGroupStats;
        this.pendingConfirms = pendingConfirms;
        this.inflightSemaphore = inflightSemaphore;
        this.undeliverable = new HashSet<>();
        this.pendingConfirmCount = new AtomicInteger();
        this.timeoutLock = new ReentrantLock();
    }

    public void checkForTimeouts(long confirmTimeoutThresholdNs) {
        int removed = 0;
        timeoutLock.lock();
        int before = inflightSemaphore.availablePermits();

        try {
            Long nanoNow = System.nanoTime();
            for (Map.Entry<Long, MessagePayload> mp : pendingConfirms.entrySet()) {
                if (nanoNow - mp.getValue().getTimestamp() > confirmTimeoutThresholdNs) {
                    pendingConfirms.remove(mp.getKey());
                    inflightSemaphore.release(1);
                    removed++;
                }
            }
        }
        finally {
            timeoutLock.unlock();
        }

        if (removed > 0) {
            int after = inflightSemaphore.availablePermits();
            logger.info("Discarded " + removed + " pending confirms due to timeout. Permits before: " + before + " after: " + after);
        }
    }

    @Override
    public void handleAck(long seqNo, boolean multiple) {
        timeoutLock.lock();
        try {
            int numConfirms = 0;
            long currentTime = MessageUtils.getTimestamp();
            long[] latencies;
            if (multiple) {
                ConcurrentNavigableMap<Long, MessagePayload> confirmed = pendingConfirms.headMap(seqNo, true);
                List<MessagePayload> confirmedList = new ArrayList<>();
                for (Map.Entry<Long, MessagePayload> entry : confirmed.entrySet())
                    confirmedList.add(entry.getValue());

                numConfirms = confirmedList.size();
                latencies = new long[numConfirms];
                int index = 0;
                for (MessagePayload mp : confirmedList) {
                    latencies[index] = MessageUtils.getDifference(mp.getTimestamp(), currentTime);
                    index++;

                    if (!undeliverable.contains(mp))
                        messageModel.sent(mp);
                    else
                        undeliverable.remove(mp);
                }
                confirmed.clear();
            } else {
                MessagePayload mp = pendingConfirms.remove(seqNo);
                if (mp != null) {
                    latencies = new long[]{MessageUtils.getDifference(mp.getTimestamp(), currentTime)};
                    numConfirms = 1;

                    if (!undeliverable.contains(mp))
                        messageModel.sent(mp);
                    else
                        undeliverable.remove(mp);
                } else {
                    latencies = new long[0];
                    numConfirms = 0;
                }
            }

            if (numConfirms > 0) {
                inflightSemaphore.release(numConfirms);
                publisherGroupStats.handleConfirm(numConfirms, latencies);
                pendingConfirmCount.set(pendingConfirms.size());
            }
        }
        finally {
            timeoutLock.unlock();
        }
    }

    @Override
    public void handleNack(long seqNo, boolean multiple) {
        int numConfirms;
        if (multiple) {
            ConcurrentNavigableMap<Long, MessagePayload> confirmed = pendingConfirms.headMap(seqNo, true);
            numConfirms = confirmed.size();
            confirmed.clear();
        } else {
            pendingConfirms.remove(seqNo);
            numConfirms = 1;
        }
        inflightSemaphore.release(numConfirms);
        publisherGroupStats.handleNack(numConfirms);
        pendingConfirmCount.set(pendingConfirms.size());
    }

    @Override
    public void handleReturn(int replyCode,
                             String replyText,
                             String exchange,
                             String routingKey,
                             AMQP.BasicProperties properties,
                             byte[] body) {
        try {
            MessagePayload mp = MessageGenerator.toMessagePayload(body);
            undeliverable.add(mp);
        }
        catch(Exception e) {
            logger.error("Failed registering basic return", e);
        }
        publisherGroupStats.handleReturn();
    }

    @Override
    public void handleBlocked(String reason) {
        publisherGroupStats.handleBlockedConnection();
    }

    @Override
    public void handleUnblocked() {
        publisherGroupStats.handleUnblockedConnection();
    }

    public int getPendingConfirmCount() {
        return pendingConfirmCount.get();
    }
}
