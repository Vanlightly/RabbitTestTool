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

public class SimpleMessageModel implements MessageModel {
    private Set<MessagePayload> expectsToReceive;
    private Set<MessagePayload> actualReceived;
    private Queue<ReceivedMessage> receiveQueue;
    private Map<String, ReceivedMessage> consumerMessages;
    private List<Violation> violations;
    private List<ConsumeInterval> consumeIntervals;
    private AtomicBoolean sendComplete;
    private AtomicBoolean isCancelled;
    private AtomicBoolean monitoringStopped;
    private AtomicBoolean enabled;
    private int unavailabilityThresholdMs;
    private Instant lastReceivedTime;
    private Instant lastReceivedPrinted = Instant.MIN;
    private Instant lastSentPrinted = Instant.MIN;
    private final ReadWriteLock actLock;
    private final ReadWriteLock expLock;
    private Instant monitorStart;
    private Instant monitorStop;
    private double availability;
    private long publishedCount;
    private boolean checkOrdering;
    private boolean checkDataLoss;
    private boolean checkDuplicates;
    private boolean isSafe;

    public SimpleMessageModel(boolean enabled) {
        this(enabled, 30, true, true, true);
    }

    public SimpleMessageModel(boolean enabled,
                              int unavailabilityThresholdSeconds,
                              boolean checkOrdering,
                              boolean checkDataLoss,
                              boolean checkDuplicates) {
        this.enabled = new AtomicBoolean(enabled);
        this.sendComplete = new AtomicBoolean();
        isCancelled = new AtomicBoolean();
        monitoringStopped  = new AtomicBoolean();

        receiveQueue = new LinkedBlockingQueue<>();
        expectsToReceive = new HashSet<>();
        actualReceived = new HashSet<>();
        violations = new ArrayList<>();
        consumerMessages = new HashMap<>();
        consumeIntervals = new ArrayList<>();
        actLock = new ReentrantReadWriteLock();
        expLock = new ReentrantReadWriteLock();
        lastReceivedTime = Instant.now();
        this.unavailabilityThresholdMs = unavailabilityThresholdSeconds * 1000;
        this.checkOrdering = checkOrdering;
        this.checkDataLoss = checkDataLoss;
        this.checkDuplicates = checkDuplicates;
    }

    public void setBenchmarkId(String benchmarkId) {
        // not used
    }

    @Override
    public void setIsSafe(boolean isSafe) {
        this.isSafe = isSafe;
    }

    @Override
    public boolean isSafe() {
        return isSafe;
    }

    public void stopMonitoring() {
        isCancelled.set(true);

        while(!monitoringStopped.get())
            ClientUtils.waitFor(1000);
    }

    public void received(ReceivedMessage messagePayload) {
        if(enabled.get()) {
            receiveQueue.add(messagePayload);
            lastReceivedTime = Instant.now();
            if(lastReceivedTime.getEpochSecond()-lastReceivedPrinted.getEpochSecond() > 10) {
                //System.out.println("Received " + messagePayload.getMessagePayload().getSequenceNumber());
                lastReceivedPrinted = lastReceivedTime;
            }
        }
    }

    public void sent(MessagePayload messagePayload) {
        if(enabled.get()) {
            expLock.writeLock().lock();
            try {
                expectsToReceive.add(messagePayload);
            }
            finally {
                expLock.writeLock().unlock();
            }
            if(Instant.now().getEpochSecond()-lastSentPrinted.getEpochSecond() > 10) {
                //System.out.println("Sent " + messagePayload.getSequenceNumber());
                lastSentPrinted = Instant.now();
            }
        }
    }

    @Override
    public void clientConnected(String clientId) {

    }

    @Override
    public void clientDisconnected(String clientId, boolean finished) {

    }

    public void sendComplete() {
        sendComplete.set(true);
    }

    public boolean allMessagesReceived() {
        if(sendComplete.get())
            return getReceivedMissing().isEmpty();
        else
            return false;
    }

    @Override
    public long missingMessageCount() {
        return getReceivedMissing().size();
    }

    public void monitorProperties(ExecutorService executorService) {
        executorService.submit(() -> {
            try {
                monitorStart = Instant.now();
                Map<Integer, ReceivedMessage> sequenceMessages = new HashMap<>();

                // detect ordering and duplication in real-time
                while (!isCancelled.get()) {
                    ReceivedMessage msg = receiveQueue.poll();
                    if (msg == null) {
                        ClientUtils.waitFor(100, this.isCancelled);
                    } else {
                        Integer sequence = msg.getMessagePayload().getSequence();

                        // check ordering property
                        if (sequenceMessages.containsKey(sequence)) {
                            ReceivedMessage lastMsg = sequenceMessages.get(sequence);

                            if (checkOrdering && lastMsg.getMessagePayload().getSequenceNumber() > msg.getMessagePayload().getSequenceNumber()
                                    && !msg.isRedelivered()) {
                                violations.add(new Violation(ViolationType.Ordering, msg.getMessagePayload(), lastMsg.getMessagePayload()));
                            }
                        }
                        sequenceMessages.put(sequence, msg);

                        // check duplicate property
                        actLock.writeLock().lock();

                        try {

                            if (checkDuplicates && actualReceived.contains(msg.getMessagePayload()) && !msg.isRedelivered())
                                violations.add(new Violation(ViolationType.NonRedeliveredDuplicate, msg.getMessagePayload()));

                            actualReceived.add(msg.getMessagePayload());
                        } finally {
                            actLock.writeLock().unlock();
                        }

                        // check consume interval
                        if (consumerMessages.containsKey(msg.getConsumerId())) {
                            ReceivedMessage lastConsumerMsg = consumerMessages.get(msg.getConsumerId());
                            if (msg.getReceiveTimestamp() - lastConsumerMsg.getReceiveTimestamp() > unavailabilityThresholdMs) {
                                consumeIntervals.add(new ConsumeInterval(lastConsumerMsg, msg));
                            }
                        }
                        consumerMessages.put(msg.getConsumerId(), msg);
                    }
                }

                // detect missing at the end
                if (checkDataLoss) {
                    Set<MessagePayload> missing = getReceivedMissing();

                    for (MessagePayload mp : missing)
                        violations.add(new Violation(ViolationType.Missing, mp));
                }

                monitoringStopped.set(true);
                monitorStop = Instant.now();

                // calculate availability based on sequences. Assumed that one sequence is one queue.
                int sequences = sequenceMessages.keySet().size();
                long totalRunTime = Duration.between(monitorStart, monitorStop).getSeconds() * sequences;
                long totalSeconds = 0;
                for (ConsumeInterval interval : consumeIntervals) {
                    Instant start = Instant.ofEpochMilli(interval.getStartMessage().getReceiveTimestamp());
                    Instant end = Instant.ofEpochMilli(interval.getEndMessage().getReceiveTimestamp());
                    long seconds = Duration.between(start, end).getSeconds();
                    totalSeconds += seconds;
                }
                availability = 100.0d - (100.0d * ((double) totalSeconds / (double) totalRunTime));
            }
            catch (Exception e) {
                // TODO proper exception handling here
                e.printStackTrace();
            }
        });
    }

    public List<Violation> getViolations() {
        return violations
                .stream()
                .sorted(Comparator.<Violation,Integer>comparing(x -> x.getMessagePayload().getSequence())
                        .thenComparing(x -> x.getMessagePayload().getSequenceNumber()))
                .collect(Collectors.toList());
    }

    public Duration durationSinceLastReceipt() {
        return Duration.between(lastReceivedTime, Instant.now());
    }

    public List<ConsumeInterval> getConsumeIntervals() {
        return consumeIntervals;
    }

    @Override
    public List<DisconnectedInterval> getDisconnectedIntervals() {
        return null; // TODO
    }

    public long getSentCount() {
        return expectsToReceive.size();
    }

    public Set<MessagePayload> getReceivedMissing() {
        Set<MessagePayload> actualCopy = null;
        Set<MessagePayload> expectedCopy = null;

        actLock.readLock().lock();
        try {
            actualCopy = new HashSet<>(actualReceived);
        }
        finally {
            actLock.readLock().unlock();
        }

        expLock.readLock().lock();
        try {
            expectedCopy = new HashSet<>(expectsToReceive);
        }
        finally {
            expLock.readLock().unlock();
        }

        expectedCopy.removeAll(actualCopy);

        return expectedCopy;
    }

    public double getConsumeAvailability() {
        return availability;
    }

    @Override
    public double getConnectionAvailability() {
        return 0;
    }

    public long getFinalPublishedCount() {
        return expectsToReceive.size();
    }

    public long getFinalConsumedCount() {
        return actualReceived.size();
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
    public Map<Integer, FinalSeqNos> getFinalSeqNos() {
        return new HashMap<>();
    }

    @Override
    public void endDisconnectionValidity() {
        throw new NotImplementedException();
    }

    @Override
    public Summary generateSummary() {
        throw new NotImplementedException();
    }
}
