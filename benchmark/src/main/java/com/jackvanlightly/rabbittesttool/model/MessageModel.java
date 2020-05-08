package com.jackvanlightly.rabbittesttool.model;

import com.jackvanlightly.rabbittesttool.clients.MessagePayload;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

public interface MessageModel {
    void received(ReceivedMessage messagePayload);
    void sent(MessagePayload messagePayload);
    void clientConnected(String clientId);
    void clientDisconnected(String clientId);

    void setBenchmarkId(String benchmarkId);
    void monitorProperties(ExecutorService executorService);
    void stopMonitoring();
    void sendComplete();

    boolean allMessagesReceived();
    long missingMessageCount();
    List<ConsumeInterval> getConsumeIntervals();
    List<DisconnectedInterval> getDisconnectedIntervals();
    double getConsumeAvailability();
    double getConnectionAvailability();
    long getFinalPublishedCount();
    long getFinalConsumedCount();
    long getFinalRedeliveredCount();
    long getUnconsumedRemainderCount();
    Duration durationSinceLastReceipt();
    List<Violation> getViolations();
    Map<Integer, FinalSeqNos> getFinalSeqNos();
}
