package com.jackvanlightly.rabbittesttool.model;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;

import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class SpanningMessageModel {
    int sequence;
    String benchmarkId;


    BenchmarkLogger logger;
    List<Span> expectsToReceive;
    Span currentExpectsToReceiveSpan;
    List<Span> unconsumedRemainder;

    // Spans that store the messages received that have passed the message loss threshold and are immutable.
    // Messages stored here will be included in message loss detection that occurs during
    // the operation of the test. The final check takes all messages into account.
    // The watermarks ensure that each data loss check over closed spans operates only once on a given range.
    List<Span> actualReceivedClosed;
    long missingLowWatermark;
    long missingHighWatermark;

    // the messages that are stored in the space efficient span format but still open for change
    List<Span> actualReceivedOpen;

    // the entry point for received messages to this model
    Set<MessagePayload> actualReceivedSet;

    AtomicLong publishedCounter;
    AtomicLong redeliveredCounter;
    AtomicLong consumedCounter;

    ReceivedMessage firstMsg;
    ReceivedMessage lastMsg;
    Instant lastHouseKeepingTime;
    Duration houseKeepingInterval;
    Duration messageLossThresholdDuration;
    int messageLossThresholdMsgs;
    boolean finalHouseKeepingDone;

    Queue<ReceivedMessage> receiveQueue;

    List<Violation> allViolations;
    List<Violation> unloggedViolations;
    AtomicBoolean sendComplete;
    AtomicBoolean isCancelled;
    AtomicBoolean monitoringStopped;
    Instant lastReceivedTime;

    final ReadWriteLock actualLock;
    final ReadWriteLock expectsLock;
    final ReadWriteLock violationsLock;

    Instant monitorStart;
    Instant monitorStop;

    boolean checkOrdering;
    boolean checkDataLoss;
    boolean checkDuplicates;
    boolean includeRedelivered;

    boolean logLastMsg;
    boolean logCompaction;
    boolean logJumps;
    boolean logViolationsLive;

    public SpanningMessageModel(String benchmarkId,
                                int sequence,
                                boolean checkOrdering,
                                boolean checkDataLoss,
                                boolean checkDuplicates,
                                boolean includeRedelivered,
                                Duration messageLossThreshold,
                                int messageLossThresholdMsgs,
                                Duration housekeepingInterval,
                                boolean logLastMsg,
                                boolean logCompaction,
                                boolean logJumps,
                                boolean logViolationsLive) {
        this.logger = new BenchmarkLogger("MESSAGE_MODEL_"+sequence);
        this.benchmarkId = benchmarkId;
        this.sequence = sequence;

        this.sendComplete = new AtomicBoolean();
        this.isCancelled = new AtomicBoolean();
        this.monitoringStopped  = new AtomicBoolean();

        this.receiveQueue = new LinkedBlockingQueue<>();
        this.expectsToReceive = new ArrayList<>();
        this.actualReceivedClosed = new ArrayList<>();
        this.actualReceivedOpen = new ArrayList<>();
        this.actualReceivedSet = new HashSet<>();
        this.houseKeepingInterval = housekeepingInterval;
        this.messageLossThresholdDuration = messageLossThreshold;
        this.messageLossThresholdMsgs = messageLossThresholdMsgs;

        this.allViolations = new ArrayList<>();
        this.unloggedViolations = new ArrayList<>();

        this.actualLock = new ReentrantReadWriteLock();
        this.expectsLock = new ReentrantReadWriteLock();
        this.violationsLock = new ReentrantReadWriteLock();
        this.lastReceivedTime = Instant.now();
        this.checkOrdering = checkOrdering;
        this.checkDataLoss = checkDataLoss;
        this.checkDuplicates = checkDuplicates;
        this.includeRedelivered = includeRedelivered;
        this.missingLowWatermark = 0;

        this.publishedCounter = new AtomicLong();
        this.redeliveredCounter = new AtomicLong();
        this.consumedCounter = new AtomicLong();

        this.logLastMsg = logLastMsg;
        this.logCompaction = logCompaction;
        this.logJumps = logJumps;
        this.logViolationsLive = logViolationsLive;
    }

    public void setBenchmarkId(String benchmarkId) {
        this.benchmarkId = benchmarkId;
    }

    public void stopMonitoring() {
        isCancelled.set(true);
    }

    public boolean monitoringStopped() {
        return monitoringStopped.get();
    }

    public void received(ReceivedMessage messagePayload) {
        receiveQueue.add(messagePayload);
        lastReceivedTime = Instant.now();
        consumedCounter.incrementAndGet();
    }

    public void sent(MessagePayload messagePayload) {
        long seqNo = messagePayload.getSequenceNumber();

        if(currentExpectsToReceiveSpan == null || !currentExpectsToReceiveSpan.isAdjacent(this.sequence, seqNo)) {
            Span newSpan = new Span(this.sequence, seqNo, seqNo);
            addExpectedSpan(newSpan);
            currentExpectsToReceiveSpan = newSpan;
        }
        else {
            currentExpectsToReceiveSpan.include(this.sequence, seqNo);
        }

        publishedCounter.incrementAndGet();
    }

    public void sendComplete() {
        sendComplete.set(true);
    }

    public boolean allMessagesReceived() {
        if(sendComplete.get())
            return getAllMissing().getMissing().isEmpty();
        else
            return false;
    }

    public long missingMessageCount() {
        long total = 0;

        for(Span span : getAllMissing().getMissing()) {
            total += span.size();
        }

        return total;
    }

    public void monitorProperties(ExecutorService executorService) {
        executorService.submit(() -> {
            while(!isCancelled.get()) {
                logger.info("Starting message model monitor for sequence: " + this.sequence);
                try {
                    monitorStart = Instant.now();
                    lastMsg = null;
                    firstMsg = null;
                    lastHouseKeepingTime = Instant.now();
                    long setCount = 0;

                    // detect ordering and duplication in real-time
                    while (!isCancelled.get()) {
                        ReceivedMessage msg = receiveQueue.poll();
                        if (msg == null) {
                            ClientUtils.waitFor(100, this.isCancelled);
                        } else {
                            actualLock.writeLock().lock();
                            try {
                                if(firstMsg == null)
                                    firstMsg = msg;

                                Instant now = Instant.now();
                                MessagePayload payload = msg.getMessagePayload();
                                long seqNo = payload.getSequenceNumber();

                                // check ordering property
                                if (checkOrdering
                                        && lastMsg != null
                                        && lastMsg.getMessagePayload().getSequenceNumber() > seqNo) {
                                    if(msg.isRedelivered() && includeRedelivered)
                                        addViolation(new Violation(ViolationType.RedeliveredOrdering, payload, lastMsg.getMessagePayload()));
                                    else if (!msg.isRedelivered())
                                        addViolation(new Violation(ViolationType.Ordering, payload, lastMsg.getMessagePayload()));
                                }

                                if(logJumps && lastMsg != null) {
                                    long gap = lastMsg.getMessagePayload().getSequenceNumber() - seqNo;
                                    if(gap > 1) {
                                        logger.info("GAP DETECTED: Sequence=" + sequence + ", Gap Size=" + gap
                                                + ", Gap Start=" + (lastMsg.getMessagePayload().getSequenceNumber() + 1)
                                                + ", Gap End="+ (seqNo - 1)
                                                + ", Time="+now);
                                    }
                                }

                                lastMsg = msg;

                                // check duplicate property
                                if (checkDuplicates) {
                                    Span existingSpan = seekSpan(seqNo, actualReceivedOpen);
                                    if (actualReceivedSet.contains(payload) || existingSpan != null) {
                                        if(includeRedelivered && msg.isRedelivered())
                                            addViolation(new Violation(ViolationType.RedeliveredDuplicate, payload));
                                        else if(!msg.isRedelivered())
                                            addViolation(new Violation(ViolationType.NonRedeliveredDuplicate, payload));
                                    }
                                }

                                if(msg.isRedelivered())
                                    redeliveredCounter.incrementAndGet();

                                actualReceivedSet.add(payload);
                                setCount++;

                                // when there are competing consumers, ordering can be lost causing span fragmentation
                                // periodically compaction spans by merging adjacent ones
                                if (setCount > 1000000 || Duration.between(lastHouseKeepingTime, now).getSeconds() > houseKeepingInterval.getSeconds()) {
                                    setCount = 0;
                                    houseKeep(now);
                                    detectMessageLossViolations();
                                }
                            }
                            finally {
                                actualLock.writeLock().unlock();
                            }
                        }
                    }

                    finalHousekeep();
                    detectMessageLossViolations();

                    monitoringStopped.set(true);
                    monitorStop = Instant.now();

                    logger.info("Stopping message model monitor for sequence: " + this.sequence);
                } catch (Exception e) {
                    logger.error("Message model monitor failed for sequence: " + this.sequence, e);
                    logger.info("Restarting message model monitor for sequence: " + this.sequence);
                    ClientUtils.waitFor(5000, isCancelled);
                }
            }
        });
    }

    // binary search of spans
    private Span seekSpan(long seqNo, List<Span> searchSpans) {
        int lo = 0;
        int hi = searchSpans.size() - 1;

        while (lo <= hi) {
            // Key is in a[lo..hi] or not present.
            int mid = lo + (hi - lo) / 2;
            Span midSpan = searchSpans.get(mid);
            if(midSpan.isInsideSpan(this.sequence, seqNo)) {
                return midSpan;
            }

            if (seqNo < midSpan.getLow())
                hi = mid - 1;
            else if (seqNo > midSpan.getHigh())
                lo = mid + 1;
            else
                return null;
        }

        return null;
    }

    private void printLastMsg(Instant now,
                              int messagesEmptied,
                              int spansClosed,
                              int compactedSize) {
        if(logLastMsg) {
            long lastSeqNo = lastMsg.getMessagePayload().getSequenceNumber();
            logger.info("Last consumed in model: Sequence=" + this.sequence + ", SeqNo=" + lastSeqNo + ", Time=" + now);
        }

        if(logCompaction) {
            logger.info(MessageFormat.format("Compaction: Sequence={0,number,#}. {1,number,#} new messages added to spans. {2,number,#} open spans closed. Compacted open spans from {3,number,#} to {4,number,#}",
                    this.sequence,
                    messagesEmptied,
                    spansClosed,
                    actualReceivedOpen.size(),
                    compactedSize));
        }
    }

    private void houseKeep(Instant now) {
        int messagesEmptied = emptyReceiveSet(now);
        int spansClosed = closeOpenSpans(now);
        List<Span> compacted = compactSpans(actualReceivedOpen, now);
        printLastMsg(now, messagesEmptied, spansClosed, compacted.size());
        actualReceivedOpen = compacted;
        lastHouseKeepingTime = now;
    }

    private int emptyReceiveSet(Instant now) {
        if(actualReceivedSet.isEmpty())
            return 0;

        List<MessagePayload> orderedPayloads = actualReceivedSet.stream().sorted().collect(Collectors.toList());
        int count = orderedPayloads.size();
        Span s = new Span(orderedPayloads.get(0).getSequence(),
                orderedPayloads.get(0).getSequenceNumber(),
                orderedPayloads.get(0).getSequenceNumber());

        for(int i=1; i<count; i++) {
            MessagePayload mp = orderedPayloads.get(i);
            if(s.isAdjacent(mp.getSequence(), mp.getSequenceNumber())) {
                s.include(mp.getSequence(), mp.getSequenceNumber(), now);
            } else {
                actualReceivedOpen.add(s);
                s = new Span(orderedPayloads.get(i).getSequence(),
                        orderedPayloads.get(i).getSequenceNumber(),
                        orderedPayloads.get(i).getSequenceNumber());
            }
        }
        actualReceivedOpen.add(s);
        actualReceivedSet.clear();

        return count;
    }

    private int closeOpenSpans(Instant now) {
        if(lastMsg == null)
            return 0;

        List<Span> newActualReceivedOpen = new ArrayList<>();
        long lastSeqNo = lastMsg.getMessagePayload().getSequenceNumber();

        int spansClosed = 0;
        boolean reachedCloseablePoint = false;
        for(int i=actualReceivedOpen.size()-1; i>=0; i--) {
            Span span = actualReceivedOpen.get(i);

            // reaches the closeable point once a span has passed both the time and message gap
            // both are required as a period of unavailability could cause the time gap to trigger
            // so if we don't also check the seq no gap we could close the span too early, causing
            // a false positive message loss detection
            if(!reachedCloseablePoint) {
                long seqNodiff = lastSeqNo - span.getHigh();
                long secondsDiff = Duration.between(span.getUpdated(), now).getSeconds();

                if(secondsDiff > messageLossThresholdDuration.getSeconds()
                    && seqNodiff > messageLossThresholdMsgs) {
                    reachedCloseablePoint = true;
                }
            }

            if(reachedCloseablePoint) {
                actualReceivedClosed.add(span);
                spansClosed++;
            }
            else
                newActualReceivedOpen.add(span);
        }


        actualReceivedOpen = newActualReceivedOpen;
        Collections.sort(actualReceivedOpen);

        if(spansClosed > 0) {
            Collections.sort(actualReceivedClosed);
        }

        // the highwater mark is used for message loss detection.
        // We set it to the border between open and closed spans
        if(actualReceivedOpen.isEmpty() && !actualReceivedClosed.isEmpty())
            missingHighWatermark = actualReceivedClosed.get(actualReceivedClosed.size()-1).getHigh();
        else
            missingHighWatermark = actualReceivedOpen.get(0).getLow()-1;

        return spansClosed;
    }

    private void finalHousekeep() {
        logger.info("Performing final house keeping");
        houseKeep(Instant.now());

        logger.info("Closing remaining " + actualReceivedOpen.size() + " open spans");
        for(Span span : actualReceivedOpen) {
            actualReceivedClosed.add(span);
        }
        List<Span> compacted = compactSpans(actualReceivedClosed, Instant.now());
        actualReceivedClosed = compacted;
        Collections.sort(actualReceivedClosed);

        if(!expectsToReceive.isEmpty()) {
            unconsumedRemainder = identifyMissingRemainder();
        }

        finalHouseKeepingDone = true;
    }

    private List<Span> compactSpans(List<Span> originalSpans, Instant now) {
        // make a copy
        List<Span> spansToCompact = new ArrayList<>();
        for(Span span : originalSpans)
            spansToCompact.add(new Span(span));

        List<Span> compactedSpans = new ArrayList<>();

        Collections.sort(spansToCompact, Span::compareTo);
        int count = spansToCompact.size();
        int pos1 = 0;
        int pos2 = 1;
        while(pos1 < count) {
            Span span = spansToCompact.get(pos1);
            Span nextSpan = pos2 < count ? spansToCompact.get(pos2) : null;

            if (nextSpan != null && span.isAdjacentRight(nextSpan)) {
                span.include(nextSpan.getSequence(), nextSpan.getHigh(), nextSpan.getUpdated());
                pos2++;
            }
            else if (nextSpan != null && span.isAdjacentLeft(nextSpan)) {
                span.include(nextSpan.getSequence(), nextSpan.getLow(), nextSpan.getUpdated());
                pos2++;
            }
            else {
                pos1 = pos2;
                pos2++;
                compactedSpans.add(span);
            }
        }

        return compactedSpans;
    }

    private void addViolation(Violation violation) {
        violationsLock.writeLock().lock();
        try {
            allViolations.add(violation);
            unloggedViolations.add(violation);

            if(logViolationsLive && !violation.getViolationType().equals(ViolationType.Missing)) {
                logger.info("VIOLATION! " + violation.toLogString());
            }
        }
        finally {
            violationsLock.writeLock().unlock();
        }
    }

    public List<Violation> getUnloggedViolations() {
        violationsLock.readLock().lock();
        try {
            List<Violation> copy = new ArrayList<>(unloggedViolations);
            unloggedViolations.clear();

            return copy;
        }
        finally {
            violationsLock.readLock().unlock();
        }
    }

    private void detectMessageLossViolations() {
        if(checkDataLoss) {
            MissingResult missingResult = getReceivedMissingInClosed(missingLowWatermark);
            if(missingResult.hasMissing()) {
                missingLowWatermark = missingResult.getHigh() + 1;

                for (Span missingSpan : missingResult.getMissing())
                    addViolation(new Violation(ViolationType.Missing, missingSpan));
            }
        }
    }

    private void addExpectedSpan(Span span) {
        expectsLock.writeLock().lock();
        try {
            expectsToReceive.add(span);
        }
        finally {
            expectsLock.writeLock().unlock();
        }
    }

    public List<Violation> getAllViolations() {
        violationsLock.readLock().lock();
        try {
            return allViolations
                    .stream()
                    .sorted()
                    .collect(Collectors.toList());
        }
        finally {
            violationsLock.readLock().unlock();
        }
    }

    public Duration durationSinceLastReceipt() {
        return Duration.between(lastReceivedTime, Instant.now());
    }

    public MissingResult getReceivedMissingInClosed(long lowWatermark) {
        List<Span> expectedSpans = null;
        List<Span> closedActualSpans = null;

        expectsLock.readLock().lock();
        try {
            expectedSpans = new ArrayList<>(this.expectsToReceive);
        } finally {
            expectsLock.readLock().unlock();
        }

        closedActualSpans = new ArrayList<>(actualReceivedClosed);
        Collections.sort(closedActualSpans);

        // no closed spans yet
        if(closedActualSpans.isEmpty())
            return new MissingResult();

        // no new closed spans since last check
        if(lowWatermark >= missingHighWatermark)
            return new MissingResult();

        return getMissing(closedActualSpans, expectedSpans, lowWatermark, missingHighWatermark);
    }

    public MissingResult getAllMissing() {
        List<Span> expectedSpans = null;
        List<Span> allActualSpans = null;

        expectsLock.readLock().lock();
        try {
            expectedSpans = new ArrayList<>(this.expectsToReceive);
        } finally {
            expectsLock.readLock().unlock();
        }

        actualLock.writeLock().lock();
        try {
            houseKeep(Instant.now());
            allActualSpans = new ArrayList<>(actualReceivedClosed);
            List<Span> openSpans = new ArrayList<>(actualReceivedOpen);
            allActualSpans.addAll(openSpans);
            Collections.sort(allActualSpans);
        } finally {
            actualLock.writeLock().unlock();
        }

        long lowWatermark = 0;
        long highWatermark = expectedSpans.get(expectedSpans.size()-1).getHigh();
        return getMissing(allActualSpans, expectedSpans, lowWatermark, highWatermark);
    }

    private MissingResult getMissing(List<Span> actualSpans, List<Span> expectedSpans, long lowWatermark, long highWatermark) {
        if (expectedSpans.size() == 0) {
            return new MissingResult(0, 0, expectedSpans);
        } else {
            long start = Math.max(expectedSpans.get(0).getLow(), lowWatermark);
            long end = Math.min(expectedSpans.get(expectedSpans.size() - 1).getHigh(), highWatermark);

            if (actualSpans.size() == 0)
                return new MissingResult(0, 0, expectedSpans);

            List<Span> missingSpans = new ArrayList<>();
            Span currentMissingSpan = null;

            int expectedCursor = 0;
            int actualCursor = 0;
            Span currentExpectedSpan = expectedSpans.get(0);
            Span currentActualSpan = actualSpans.get(0);
            for (long i = start; i <= end; i++) {
                if (!currentExpectedSpan.isInsideSpan(this.sequence, i)) {
                    Span nextSpan = expectedCursor < expectedSpans.size() - 1 ? expectedSpans.get(expectedCursor + 1) : null;
                    if (nextSpan == null || !nextSpan.isInsideSpan(this.sequence, i)) {
                        // not an expected sequence number
                        continue;
                    } else {
                        currentExpectedSpan = nextSpan;
                        expectedCursor++;
                    }
                }

                if (!currentActualSpan.isInsideSpan(this.sequence, i)) {
                    Span nextSpan = actualCursor < actualSpans.size() - 1 ? actualSpans.get(actualCursor + 1) : null;
                    if (nextSpan == null || !nextSpan.isInsideSpan(this.sequence, i)) {
                        if (currentMissingSpan == null || !currentMissingSpan.isAdjacent(this.sequence, i)) {
                            currentMissingSpan = new Span(this.sequence, i, i);
                            missingSpans.add(currentMissingSpan);
                        } else {
                            currentMissingSpan.include(this.sequence, i);
                        }
                    } else {
                        currentActualSpan = nextSpan;
                        actualCursor++;
                    }
                }
            }

            return new MissingResult(start, end, missingSpans);
        }
    }

    // this is the unconsumed tail of expected messages. Given that unavailability can cause this
    // we cannot classify these as lost
    private List<Span> identifyMissingRemainder() {
        long remainderLow = actualReceivedClosed.size() == 0
                ? 0
                : actualReceivedClosed.get(actualReceivedClosed.size()-1).getHigh()+1;

        List<Span> remainder = new ArrayList<>();
        for(Span span : expectsToReceive) {
            if(span.isInsideSpan(sequence, remainderLow))
                remainder.add(new Span(sequence, remainderLow, span.getHigh()));
            else if(span.isHigherThan(sequence, remainderLow))
                remainder.add(span);
        }

        return remainder;
    }

    public long getUnconsumedRemainderCount() {
        if(unconsumedRemainder.isEmpty())
            return 0;
        else
            return unconsumedRemainder.stream().map(x -> x.size()).reduce(Long::sum).get();
    }

    public long getFinalPublishedCount() {
        if(expectsToReceive.isEmpty())
            return 0;
        else
            return expectsToReceive.stream().map(x -> x.size()).reduce(Long::sum).get();
    }

    public long getRedeliveredCount() {
        return redeliveredCounter.get();
    }

    public long getFinalConsumedCount() {
        if(finalHouseKeepingDone)
            if(actualReceivedClosed.isEmpty())
                return 0;
            else
                return actualReceivedClosed.stream().map(x -> x.size()).reduce(Long::sum).get();
        else {
            logger.warn("Requested final consume count but final house keeping not performed yet");
            return 0;
        }
    }

    public FinalSeqNos getFinalSeqNos() {
        Optional<Long> firstMissing = getAllViolations()
                .stream()
                .filter(x -> x.getViolationType() == ViolationType.Missing)
                .map(x -> x.getSpan().getLow())
                .min(Long::compareTo);

        Optional<Long> lastMissing = getAllViolations()
                .stream()
                .filter(x -> x.getViolationType() == ViolationType.Missing)
                .map(x -> x.getSpan().getLow())
                .max(Long::compareTo);

        return new FinalSeqNos(
                sequence,
                expectsToReceive.isEmpty() ? -1 : expectsToReceive.get(0).getLow(),
                firstMsg != null ? firstMsg.getMessagePayload().getSequenceNumber() : -1L,
                firstMissing.isPresent() ? firstMissing.get() : -1L,
                expectsToReceive.isEmpty() ? -1 : expectsToReceive.get(expectsToReceive.size()-1).getHigh(),
                lastMsg != null ? lastMsg.getMessagePayload().getSequenceNumber() : -1L,
                lastMissing.isPresent() ? lastMissing.get() : -1L);
    }
}
