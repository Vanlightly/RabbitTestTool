package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.FlowController;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import com.jackvanlightly.rabbittesttool.clients.MessageUtils;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.PublisherGroupStats;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.stream.Client;

import java.util.*;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class StreamPublisherListener implements Client.ConfirmListener, Client.PublishErrorListener {

    private BenchmarkLogger logger;
    private MessageModel messageModel;
    private PublisherGroupStats publisherGroupStats;
    private ConcurrentNavigableMap<Long,MessagePayload> pendingConfirms;
    private FlowController flowController;
    private Lock timeoutLock;

    public StreamPublisherListener(MessageModel messageModel,
                             PublisherGroupStats publisherGroupStats,
                             ConcurrentNavigableMap<Long, MessagePayload> pendingConfirms,
                             FlowController flowController) {
        this.logger = new BenchmarkLogger("PUBLISHER");
        this.messageModel = messageModel;
        this.publisherGroupStats = publisherGroupStats;
        this.pendingConfirms = pendingConfirms;
        this.flowController = flowController;
        this.timeoutLock = new ReentrantLock();
    }

    public void checkForTimeouts(long confirmTimeoutThresholdNs) {
        int removed = 0;
        timeoutLock.lock();
        int before = flowController.availablePermits();

        try {
            Long nanoNow = System.nanoTime();
            for (Map.Entry<Long, MessagePayload> mp : pendingConfirms.entrySet()) {
                if (nanoNow - mp.getValue().getTimestamp() > confirmTimeoutThresholdNs) {
                    pendingConfirms.remove(mp.getKey());
                    flowController.returnSendPermits(1);
                    removed++;
                }
            }
        }
        finally {
            timeoutLock.unlock();
        }

        if (removed > 0) {
            int after = flowController.availablePermits();
            logger.info("Discarded " + removed + " pending confirms due to timeout. Permits before: " + before + " after: " + after);
        }
    }

    public int getPendingConfirmCount() {
        return pendingConfirms.size();
    }

    // confirmListener
    @Override
    public void handle(long seqNo) {
        timeoutLock.lock();
        try {
            int numConfirms = 0;
            long currentTime = MessageUtils.getTimestamp();
            long[] latencies;

            MessagePayload mp = pendingConfirms.remove(seqNo);
            if (mp != null) {
                latencies = new long[]{MessageUtils.getDifference(mp.getTimestamp(), currentTime)};
                numConfirms = 1;

                messageModel.sent(mp);
            } else {
                latencies = new long[0];
                numConfirms = 0;
            }

            if (numConfirms > 0) {
                flowController.returnSendPermits(numConfirms);
                publisherGroupStats.handleConfirm(numConfirms, latencies);
            }
        }
        finally {
            timeoutLock.unlock();
        }
    }

    // publishErrorListener
    @Override
    public void handle(long seqNo, short i) {
        int numConfirms;
        boolean multiple = false;
        if (multiple) {
            ConcurrentNavigableMap<Long, MessagePayload> confirmed = pendingConfirms.headMap(seqNo, true);
            numConfirms = confirmed.size();
            confirmed.clear();
        } else {
            pendingConfirms.remove(seqNo);
            numConfirms = 1;
        }
        flowController.returnSendPermits(numConfirms);
        publisherGroupStats.handleNack(numConfirms);
    }
}
