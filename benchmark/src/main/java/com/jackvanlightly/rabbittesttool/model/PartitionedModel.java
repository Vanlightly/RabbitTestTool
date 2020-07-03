package com.jackvanlightly.rabbittesttool.model;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import com.jackvanlightly.rabbittesttool.register.BenchmarkRegister;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class PartitionedModel implements MessageModel {
    boolean enabled;
    BenchmarkLogger logger;
    String benchmarkId;
    BenchmarkRegister register;
    AtomicBoolean isCancelled;
    Map<Integer, SpanningMessageModel> models;
    int unavailabilityThresholdMs;
    boolean checkOrdering;
    boolean checkDataLoss;
    boolean checkDuplicates;
    boolean checkConnectivity;
    boolean checkConsumeGaps;
    boolean includeRedelivered;

    boolean logLastMsg;
    boolean logCompaction;
    boolean logJumps;

    private final ReadWriteLock modelLock;
    final ReadWriteLock intervalsLock;
    ExecutorService executorService;

    ConcurrentMap<String, Boolean> clientIds;

    ConcurrentMap<String, ReceivedMessage> consumerMessages;
    List<ConsumeInterval> allConsumeIntervals;
    List<ConsumeInterval> unloggedConsumeIntervals;

    ConcurrentMap<String, Instant> disconnectedClients;
    List<DisconnectedInterval> allDisconnectedIntervals;
    List<DisconnectedInterval> unloggedDisconnectedIntervals;

    Duration houseKeepingInterval;
    Duration messageLossThresholdDuration;
    int messageLossThresholdMsgs;
    Instant started;
    volatile double consumeAvailability;
    volatile double connectionAvailability;

    public PartitionedModel() {
        this.enabled = false;
        this.modelLock = new ReentrantReadWriteLock();
        this.consumerMessages = new ConcurrentHashMap<>();
        this.intervalsLock = new ReentrantReadWriteLock();

    }

    public PartitionedModel(
            BenchmarkRegister register,
            int unavailabilityThresholdSeconds,
            Duration messageLossThresholdDuration,
            int messageLossThresholdMsgs,
            Duration houseKeepingInterval,
            boolean checkOrdering,
            boolean checkDataLoss,
            boolean checkDuplicates,
            boolean checkConnectivity,
            boolean checkConsumeGaps,
            boolean includeRedelivered,
            boolean logLastMsg,
            boolean logCompaction,
            boolean logJumps) {
        this.register = register;
        this.enabled = true;
        this.logger = new BenchmarkLogger("PARTITIONED_MODEL");
        this.modelLock = new ReentrantReadWriteLock();
        this.models = new HashMap<>();

        this.unavailabilityThresholdMs = unavailabilityThresholdSeconds*1000;
        this.checkOrdering = checkOrdering;
        this.checkDataLoss = checkDataLoss;
        this.checkDuplicates = checkDuplicates;
        this.checkConnectivity = checkConnectivity;
        this.checkConsumeGaps = checkConsumeGaps;
        this.includeRedelivered = includeRedelivered;
        this.houseKeepingInterval = houseKeepingInterval;
        this.messageLossThresholdDuration = messageLossThresholdDuration;
        this.messageLossThresholdMsgs = messageLossThresholdMsgs;

        this.isCancelled = new AtomicBoolean();
        this.clientIds = new ConcurrentHashMap<>();
        this.consumerMessages = new ConcurrentHashMap<>();
        this.disconnectedClients = new ConcurrentHashMap<>();
        this.allConsumeIntervals = new ArrayList<>();
        this.unloggedConsumeIntervals = new ArrayList<>();
        this.allDisconnectedIntervals = new ArrayList<>();
        this.unloggedDisconnectedIntervals = new ArrayList<>();
        this.intervalsLock = new ReentrantReadWriteLock();
        this.consumeAvailability = 100.0d;

        this.logLastMsg = logLastMsg;
        this.logCompaction = logCompaction;
        this.logJumps = logJumps;
    }

    public void setBenchmarkId(String benchmarkId) {
        this.benchmarkId = benchmarkId;

        for(SpanningMessageModel model : models.values())
            model.setBenchmarkId(benchmarkId);
    }

    @Override
    public void received(ReceivedMessage msg) {
        if(enabled) {
            SpanningMessageModel model = getModel(msg.getMessagePayload().getStream());
            model.received(msg);

            // check consume interval
            if(checkConsumeGaps) {
                if (consumerMessages.containsKey(msg.getConsumerId())) {
                    ReceivedMessage lastConsumerMsg = consumerMessages.get(msg.getConsumerId());
                    if (msg.getReceiveTimestamp() - lastConsumerMsg.getReceiveTimestamp() > unavailabilityThresholdMs) {
                        addConsumeInterval(new ConsumeInterval(lastConsumerMsg, msg));
                    }
                }
                consumerMessages.put(msg.getConsumerId(), msg);
            }
        }
    }

    private void addConsumeInterval(ConsumeInterval consumeInterval) {
        intervalsLock.writeLock().lock();
        try {
            allConsumeIntervals.add(consumeInterval);
            unloggedConsumeIntervals.add(consumeInterval);
        }finally {
            intervalsLock.writeLock().unlock();
        }
    }

    private double calculateTotalConsumeAvailability(Instant startPeriod, int consumers) {
        intervalsLock.readLock().lock();
        try {
            if (allConsumeIntervals.isEmpty())
                return 100.0d;

            long totalRunTime = Duration.between(startPeriod, Instant.now()).getSeconds() * consumers;
            long totalSeconds = 0;
            for (ConsumeInterval interval : allConsumeIntervals) {
                Instant start = Instant.ofEpochMilli(interval.getStartMessage().getReceiveTimestamp());
                Instant end = Instant.ofEpochMilli(interval.getEndMessage().getReceiveTimestamp());
                long seconds = Duration.between(start, end).getSeconds();
                totalSeconds += seconds;
            }
            return 100.0d - (100.0d * ((double) totalSeconds / (double) totalRunTime));
        }
        finally {
            intervalsLock.readLock().unlock();
        }
    }

    private double calculateTotalConnectionAvailability(Instant startPeriod, int clients) {
        intervalsLock.readLock().lock();
        try {
            if (allDisconnectedIntervals.isEmpty())
                return 100.0d;

            long totalRunTime = Duration.between(startPeriod, Instant.now()).getSeconds() * clients;
            long totalSeconds = 0;
            for (DisconnectedInterval interval : allDisconnectedIntervals) {
                totalSeconds += interval.getDuration().getSeconds();
            }
            return 100.0d - (100.0d * ((double) totalSeconds / (double) totalRunTime));
        }
        finally {
            intervalsLock.readLock().unlock();
        }
    }

    private List<ConsumeInterval> getUnloggedConsumeIntervals() {
        intervalsLock.readLock().lock();
        try {
            List<ConsumeInterval> copy = new ArrayList<>(unloggedConsumeIntervals);
            unloggedConsumeIntervals.clear();
            return copy;
        }
        finally {
            intervalsLock.readLock().unlock();
        }
    }

    private List<DisconnectedInterval> getUnloggedDisconnectedIntervals() {
        intervalsLock.readLock().lock();
        try {
            List<DisconnectedInterval> copy = new ArrayList<>(unloggedDisconnectedIntervals);
            unloggedDisconnectedIntervals.clear();
            return copy;
        }
        finally {
            intervalsLock.readLock().unlock();
        }
    }

    @Override
    public void sent(MessagePayload messagePayload) {
        if(enabled) {
            SpanningMessageModel model = getModel(messagePayload.getStream());
            if(model != null)
                model.sent(messagePayload);
        }
    }

    @Override
    public void clientConnected(String clientId) {
        if(enabled && checkConnectivity) {
            clientIds.put(clientId, true);

            if(disconnectedClients.containsKey(clientId)) {
                Instant disconnectedAt = disconnectedClients.get(clientId);
                Instant now = Instant.now();
                Duration duration = Duration.between(disconnectedAt, now);
                if(duration.toMillis() > unavailabilityThresholdMs) {
                    addDisconnectedInterval(new DisconnectedInterval(clientId, disconnectedAt, now));
                }

                this.disconnectedClients.remove(clientId);
            }
        }
    }

    private void addDisconnectedInterval(DisconnectedInterval disconnectedInterval) {
        intervalsLock.writeLock().lock();
        try {
            allDisconnectedIntervals.add(disconnectedInterval);
            unloggedDisconnectedIntervals.add(disconnectedInterval);
        }finally {
            intervalsLock.writeLock().unlock();
        }
    }

    @Override
    public void clientDisconnected(String clientId) {
        if(enabled && checkConnectivity) {
            if(!disconnectedClients.containsKey(clientId))
                disconnectedClients.put(clientId, Instant.now());
        }
    }

    private SpanningMessageModel getModel(int stream) {
        SpanningMessageModel model = models.get(stream);
        if(model == null) {
            modelLock.writeLock().lock();
            try {
                model = models.get(stream);
                if(model == null) {
                    model = new SpanningMessageModel(
                            benchmarkId,
                            stream,
                            checkOrdering,
                            checkDataLoss,
                            checkDuplicates,
                            includeRedelivered,
                            messageLossThresholdDuration,
                            messageLossThresholdMsgs,
                            houseKeepingInterval,
                            logLastMsg,
                            logCompaction,
                            logJumps);
                    model.monitorProperties(this.executorService);
                    models.put(stream, model);
                }
            }
            finally{
                modelLock.writeLock().unlock();
            }
        }

        return model;
    }

    @Override
    public void monitorProperties(ExecutorService executorService) {
        this.started = Instant.now();
        this.executorService = executorService;

        this.executorService.submit(() -> {
           while(!isCancelled.get()) {
               ClientUtils.waitFor(60000, isCancelled);
               log();
           }
        });
    }

    private void finalUnavailabilityCheck() {
        Instant now = Instant.now();

        for(Map.Entry<String, Instant> disconnectedClient : disconnectedClients.entrySet()) {
            Instant disconnectedAt = disconnectedClient.getValue();
            Duration duration = Duration.between(disconnectedAt, now);
            if(duration.toMillis() > unavailabilityThresholdMs) {
                addDisconnectedInterval(new DisconnectedInterval(disconnectedClient.getKey(), disconnectedAt, now));
            }
        }
    }

    private void log() {
        try {
            List<Violation> violations = new ArrayList<>();
            List<ConsumeInterval> intervals = new ArrayList<>();

            for (SpanningMessageModel model : this.models.values()) {
                violations.addAll(model.getUnloggedViolations());
            }
            Collections.sort(violations);
            if (!violations.isEmpty())
                register.logViolations(benchmarkId, violations);

            List<ConsumeInterval> modelIntervals = getUnloggedConsumeIntervals();
            intervals.addAll(modelIntervals);
            consumeAvailability = calculateTotalConsumeAvailability(started, consumerMessages.size());

            if (!intervals.isEmpty()) {
                register.logConsumeIntervals(benchmarkId,
                        intervals,
                        unavailabilityThresholdMs / 1000,
                        consumeAvailability);
            }

            List<DisconnectedInterval> disconnectedIntervals = getUnloggedDisconnectedIntervals();
            connectionAvailability = calculateTotalConnectionAvailability(started, clientIds.size());

            if(!disconnectedIntervals.isEmpty()) {
                register.logDisconnectedIntervals(benchmarkId,
                        disconnectedIntervals,
                        unavailabilityThresholdMs / 1000,
                        connectionAvailability);
            }
        }
        catch(Exception e) {
            logger.error("Failed logging violations and intervals. ", e);
        }
    }

    @Override
    public void stopMonitoring() {
        for(Map.Entry<Integer,SpanningMessageModel> entry : this.models.entrySet())
            entry.getValue().stopMonitoring();

        while(!this.models.values().stream().allMatch(x -> x.monitoringStopped()))
            ClientUtils.waitFor(100);

        finalUnavailabilityCheck();
        log();
    }

    @Override
    public void sendComplete() {
        for(Map.Entry<Integer,SpanningMessageModel> entry : this.models.entrySet())
            entry.getValue().sendComplete();
    }

    @Override
    public boolean allMessagesReceived() {
        for(Map.Entry<Integer,SpanningMessageModel> entry : this.models.entrySet()) {
            if(!entry.getValue().allMessagesReceived())
                return false;
        }

        return true;
    }

    @Override
    public long missingMessageCount() {
        long total = 0;

        for(Map.Entry<Integer,SpanningMessageModel> entry : this.models.entrySet())
            total += entry.getValue().missingMessageCount();

        return total;
    }

    @Override
    public List<ConsumeInterval> getConsumeIntervals() {
        return allConsumeIntervals;
    }

    @Override
    public List<DisconnectedInterval> getDisconnectedIntervals() {
        return allDisconnectedIntervals;
    }

    @Override
    public double getConsumeAvailability() {
        return consumeAvailability;
    }

    @Override
    public double getConnectionAvailability() {
        return connectionAvailability;
    }

    @Override
    public long getFinalPublishedCount() {
        long total = 0;

        for(Map.Entry<Integer,SpanningMessageModel> entry : this.models.entrySet())
            total += entry.getValue().getFinalPublishedCount();

        return total;
    }

    @Override
    public long getFinalConsumedCount() {
        long total = 0;

        for(Map.Entry<Integer,SpanningMessageModel> entry : this.models.entrySet()) {
            total += entry.getValue().getFinalConsumedCount();
        }

        return total;
    }

    @Override
    public long getFinalRedeliveredCount() {
        long total = 0;

        for(Map.Entry<Integer,SpanningMessageModel> entry : this.models.entrySet()) {
            total += entry.getValue().getRedeliveredCount();
        }

        return total;
    }

    @Override
    public long getUnconsumedRemainderCount() {
        long total = 0;

        for(Map.Entry<Integer,SpanningMessageModel> entry : this.models.entrySet()) {
            total += entry.getValue().getUnconsumedRemainderCount();
        }

        return total;
    }

    @Override
    public Duration durationSinceLastReceipt() {
        Duration min = Duration.ofSeconds(10000000);

        for(Map.Entry<Integer,SpanningMessageModel> entry : this.models.entrySet()) {
            if(entry.getValue().durationSinceLastReceipt().compareTo(min) < 0)
                min = entry.getValue().durationSinceLastReceipt();
        }

        return min;
    }

    @Override
    public List<Violation> getViolations() {
        List<Violation> violations = new ArrayList<>();

        for(Map.Entry<Integer,SpanningMessageModel> entry : this.models.entrySet())
            violations.addAll(entry.getValue().getAllViolations());

        return violations;
    }

    @Override
    public Map<Integer, FinalSeqNos> getFinalSeqNos() {
        Map<Integer,FinalSeqNos> seqNos = new HashMap<>();

        for(Map.Entry<Integer,SpanningMessageModel> entry : this.models.entrySet()) {
            seqNos.put(entry.getKey(), entry.getValue().getFinalSeqNos());
        }

        return seqNos;
    }
}
