package com.jackvanlightly.rabbittesttool.model;

import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class NullMessageModel implements MessageModel {
    @Override
    public void received(ReceivedMessage messagePayload) {

    }

    @Override
    public void sent(MessagePayload messagePayload) {

    }

    @Override
    public void clientConnected(String clientId) {

    }

    @Override
    public void clientDisconnected(String clientId, boolean finished) {

    }

    @Override
    public void endDisconnectionValidity() {

    }

    @Override
    public void setBenchmarkId(String benchmarkId) {

    }

    @Override
    public void setIsSafe(boolean isSafe) {

    }

    @Override
    public boolean isSafe() {
        return false;
    }

    @Override
    public void monitorProperties(ExecutorService executorService) {

    }

    @Override
    public void stopMonitoring() {

    }

    @Override
    public void sendComplete() {

    }

    @Override
    public boolean allMessagesReceived() {
        return true;
    }

    @Override
    public long missingMessageCount() {
        return 0;
    }

    @Override
    public List<ConsumeInterval> getConsumeIntervals() {
        return new ArrayList<>();
    }

    @Override
    public List<DisconnectedInterval> getDisconnectedIntervals() {
        return new ArrayList<>();
    }

    @Override
    public double getConsumeAvailability() {
        return 0;
    }

    @Override
    public double getConnectionAvailability() {
        return 100;
    }

    @Override
    public long getFinalPublishedCount() {
        return 0;
    }

    @Override
    public long getFinalConsumedCount() {
        return 0;
    }

    @Override
    public long getFinalRedeliveredCount() {
        return 0;
    }

    @Override
    public long getUnconsumedRemainderCount() {
        return 0;
    }

    @Override
    public Duration durationSinceLastReceipt() {
        return Duration.ofSeconds(0);
    }

    @Override
    public List<Violation> getViolations() {
        return new ArrayList<>();
    }

    @Override
    public Map<Integer, FinalSeqNos> getFinalSeqNos() {
        return new HashMap<>();
    }

    @Override
    public Summary generateSummary() {
        return new Summary();
    }
}
