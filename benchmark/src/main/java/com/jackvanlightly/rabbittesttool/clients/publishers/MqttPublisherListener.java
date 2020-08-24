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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MqttPublisherListener implements MqttClientConnectedListener, MqttClientDisconnectedListener {
    BenchmarkLogger logger;
    String publisherId;
    MessageModel messageModel;
    MetricGroup metricGroup;
    FlowController flowController;
    Lock timeoutLock;
    AtomicBoolean isConnected;

    public MqttPublisherListener(String publisherId,
                                 MessageModel messageModel,
                                 MetricGroup metricGroup,
                                 FlowController flowController) {
        this.logger = new BenchmarkLogger("MQTT PUBLISHER");
        this.publisherId = publisherId;
        this.messageModel = messageModel;
        this.metricGroup = metricGroup;
        this.flowController = flowController;
        this.timeoutLock = new ReentrantLock();

        this.isConnected = new AtomicBoolean();
    }

    public void checkForTimeouts(long confirmTimeoutThresholdNs) {
//        TODO
    }

    public int getPendingConfirmCount() {
        return flowController.getPendingCount();
    }

    public void handleConfirm(long seqNo) {
        // TODO
    }

    public void handleError(long seqNo, short i) {
        //TODO
    }

    @Override
    public void onConnected(MqttClientConnectedContext mqttClientConnectedContext) {
        messageModel.clientConnected(publisherId);
        this.isConnected.set(true);
    }

    @Override
    public void onDisconnected(MqttClientDisconnectedContext mqttClientDisconnectedContext) {
        messageModel.clientDisconnected(publisherId, false); //TODO isCancelled.get()
        this.isConnected.set(false);
    }

    public boolean isConnected() {
        return this.isConnected.get();
    }
}
