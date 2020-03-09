package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import com.jackvanlightly.rabbittesttool.clients.MessageUtils;
import com.jackvanlightly.rabbittesttool.clients.consumers.Consumer;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.Stats;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BlockedListener;
import com.rabbitmq.client.ConfirmListener;
import com.rabbitmq.client.ReturnListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.Semaphore;

public class PublisherListener implements ConfirmListener, ReturnListener, BlockedListener {

    private BenchmarkLogger logger;
    private MessageModel messageModel;
    private Stats stats;
    private ConcurrentNavigableMap<Long,MessagePayload> pendingConfirms;
    private Semaphore inflightSemaphore;
    private Set<MessagePayload> undeliverable;

    public PublisherListener(MessageModel messageModel, Stats stats, ConcurrentNavigableMap<Long, MessagePayload> pendingConfirms, Semaphore inflightSemaphore) {
        this.logger = new BenchmarkLogger("PUBLISHER");
        this.messageModel = messageModel;
        this.stats = stats;
        this.pendingConfirms = pendingConfirms;
        this.inflightSemaphore = inflightSemaphore;
        this.undeliverable = new HashSet<>();
    }

    @Override
    public void handleAck(long seqNo, boolean multiple) {
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

                if(!undeliverable.contains(mp))
                    messageModel.sent(mp);
                else
                    undeliverable.remove(mp);
            }
            confirmed.clear();
        } else {
            MessagePayload mp = pendingConfirms.remove(seqNo);
            if(mp != null) {
                latencies = new long[]{MessageUtils.getDifference(mp.getTimestamp(), currentTime)};
                numConfirms = 1;

                if(!undeliverable.contains(mp))
                    messageModel.sent(mp);
                else
                    undeliverable.remove(mp);
            }
            else
            {
                latencies = new long[0];
                numConfirms = 0;
            }
        }

        if(numConfirms > 0) {
            inflightSemaphore.release(numConfirms);
            stats.handleConfirm(numConfirms, latencies);
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
        stats.handleNack(numConfirms);
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
        stats.handleReturn();
    }

    @Override
    public void handleBlocked(String reason) {
        stats.handleBlockedConnection();
    }

    @Override
    public void handleUnblocked() {
        stats.handleUnblockedConnection();
    }
}
