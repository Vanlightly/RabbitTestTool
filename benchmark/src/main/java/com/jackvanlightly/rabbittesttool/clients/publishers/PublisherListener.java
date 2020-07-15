package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.FlowController;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import com.jackvanlightly.rabbittesttool.clients.MessageUtils;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.MetricGroup;
import com.jackvanlightly.rabbittesttool.statistics.MetricType;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.ReturnListener;

import java.util.*;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class PublisherListener implements ConfirmListener, ReturnListener, BlockedListener {

    BenchmarkLogger logger;
    MessageModel messageModel;
    MetricGroup metricGroup;
    ConcurrentNavigableMap<Long,MessagePayload> pendingConfirms;
    FlowController flowController;
    Set<MessagePayload> undeliverable;
    AtomicInteger pendingConfirmCount;
    Lock timeoutLock;

    public PublisherListener(MessageModel messageModel,
                             MetricGroup metricGroup,
                             ConcurrentNavigableMap<Long, MessagePayload> pendingConfirms,
                             FlowController flowController) {
        this.logger = new BenchmarkLogger("PUBLISHER");
        this.messageModel = messageModel;
        this.metricGroup = metricGroup;
        this.pendingConfirms = pendingConfirms;
        this.flowController = flowController;
        this.undeliverable = new HashSet<>();
        this.pendingConfirmCount = new AtomicInteger();
        this.timeoutLock = new ReentrantLock();
    }

    public void checkForTimeouts(long confirmTimeoutThresholdNs) {
        int removed = flowController.discardTimedOutAmqpMessages(confirmTimeoutThresholdNs);
        if (removed > 0) {
            logger.info("Discarded " + removed + " pending confirms due to timeout.");
        }
    }

    @Override
    public void handleAck(long seqNo, boolean multiple) {
        long currentTime = MessageUtils.getTimestamp();
        List<MessagePayload> confirmedList = flowController.confirmAmqpMessages(seqNo, multiple);
        int numConfirms = confirmedList.size();
        long[] latencies = new long[numConfirms];
        int index = 0;
        for (MessagePayload mp : confirmedList) {
            latencies[index] = MessageUtils.getDifference(mp.getTimestamp(), currentTime);
            index++;

            if (!undeliverable.contains(mp))
                messageModel.sent(mp);
            else
                undeliverable.remove(mp);
        }

        if (numConfirms > 0) {
            metricGroup.increment(MetricType.PublisherConfirm, numConfirms);
            metricGroup.add(MetricType.PublisherConfirmLatencies, latencies);
            pendingConfirmCount.set(pendingConfirms.size());
        }

//            int numConfirms = 0;
//            long currentTime = MessageUtils.getTimestamp();
//            long[] latencies;
//            if (multiple) {
//                ConcurrentNavigableMap<Long, MessagePayload> confirmed = pendingConfirms.headMap(seqNo, true);
//                List<MessagePayload> confirmedList = new ArrayList<>();
//                for (Map.Entry<Long, MessagePayload> entry : confirmed.entrySet())
//                    confirmedList.add(entry.getValue());
//
//                numConfirms = confirmedList.size();
//                latencies = new long[numConfirms];
//                int index = 0;
//                for (MessagePayload mp : confirmedList) {
//                    latencies[index] = MessageUtils.getDifference(mp.getTimestamp(), currentTime);
//                    index++;
//
//                    if (!undeliverable.contains(mp))
//                        messageModel.sent(mp);
//                    else
//                        undeliverable.remove(mp);
//                }
//                confirmed.clear();
//            } else {
//                MessagePayload mp = pendingConfirms.remove(seqNo);
//                if (mp != null) {
//                    latencies = new long[]{MessageUtils.getDifference(mp.getTimestamp(), currentTime)};
//                    numConfirms = 1;
//
//                    if (!undeliverable.contains(mp))
//                        messageModel.sent(mp);
//                    else
//                        undeliverable.remove(mp);
//                } else {
//                    latencies = new long[0];
//                    numConfirms = 0;
//                }
//            }
//
//            if (numConfirms > 0) {
//                flowController.returnSendPermits(numConfirms);
//                metricGroup.increment(MetricType.PublisherConfirm, numConfirms);
//                metricGroup.add(MetricType.PublisherConfirmLatencies, latencies);
//                pendingConfirmCount.set(pendingConfirms.size());
//            }
    }

    @Override
    public void handleNack(long seqNo, boolean multiple) {
        List<MessagePayload> confirmedList = flowController.confirmAmqpMessages(seqNo, multiple);
        int numConfirms = confirmedList.size();
        metricGroup.increment(MetricType.PublisherNacked, numConfirms);
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
        metricGroup.increment(MetricType.PublisherReturned);
    }

    @Override
    public void handleBlocked(String reason) {
        metricGroup.increment(MetricType.PublisherBlockedConnection);
    }

    @Override
    public void handleUnblocked() {
        metricGroup.increment(MetricType.PublisherUnblockedConnection);
    }

    public int getPendingConfirmCount() {
        return pendingConfirmCount.get();
    }
}
