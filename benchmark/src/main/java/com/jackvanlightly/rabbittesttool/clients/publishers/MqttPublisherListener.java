package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedContext;
import com.hivemq.client.mqtt.lifecycle.MqttClientConnectedListener;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.FlowController;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import com.jackvanlightly.rabbittesttool.clients.MessageUtils;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.MetricGroup;
import com.jackvanlightly.rabbittesttool.statistics.MetricType;

import java.util.Map;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MqttPublisherListener implements MqttClientConnectedListener, MqttClientDisconnectedListener {
    BenchmarkLogger logger;
    String publisherId;
    MessageModel messageModel;
    MetricGroup metricGroup;
    ConcurrentNavigableMap<Long, MessagePayload> pendingConfirms;
    FlowController flowController;
    Lock timeoutLock;
    AtomicBoolean isConnected;

    public MqttPublisherListener(String publisherId,
                                 MessageModel messageModel,
                                 MetricGroup metricGroup,
                                 ConcurrentNavigableMap<Long, MessagePayload> pendingConfirms,
                                 FlowController flowController) {
        this.logger = new BenchmarkLogger("MQTT PUBLISHER");
        this.publisherId = publisherId;
        this.messageModel = messageModel;
        this.metricGroup = metricGroup;
        this.pendingConfirms = pendingConfirms;
        this.flowController = flowController;
        this.timeoutLock = new ReentrantLock();

        this.isConnected = new AtomicBoolean();
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

    public void handleConfirm(long seqNo) {
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
                metricGroup.increment(MetricType.PublisherConfirm, numConfirms);
                metricGroup.add(MetricType.PublisherConfirmLatencies, latencies);
            }
        }
        finally {
            timeoutLock.unlock();
        }
    }

    public void handleError(long seqNo, short i) {
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
        metricGroup.increment(MetricType.PublisherNacked, numConfirms);
    }

    @Override
    public void onConnected(MqttClientConnectedContext mqttClientConnectedContext) {
        messageModel.clientConnected(publisherId);
        this.isConnected.set(true);
    }

    @Override
    public void onDisconnected(MqttClientDisconnectedContext mqttClientDisconnectedContext) {
        messageModel.clientDisconnected(publisherId);
        this.isConnected.set(false);
    }

    public boolean isConnected() {
        return this.isConnected.get();
    }
}
