package com.jackvanlightly.rabbittesttool.model;

import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class MessageModel {
    private Set<MessagePayload> expectsToReceive;
    private Set<MessagePayload> actualReceived;
    private Queue<ReceivedMessage> receiveQueue;
    private Map<String, ReceivedMessage> consumerMessages;
    private List<Violation> violations;
    private List<ConsumeInterval> consumeIntervals;
    private boolean sendComplete;
    private boolean isCancelled;
    private boolean monitoringStopped;
    private boolean enabled;
    private Instant lastReceivedTime;
    private Instant lastReceivedPrinted = Instant.MIN;
    private Instant lastSentPrinted = Instant.MIN;
    private final ReadWriteLock actLock;
    private final ReadWriteLock expLock;

    public MessageModel(boolean enabled) {
        this.enabled = enabled;
        receiveQueue = new LinkedBlockingQueue<>();
        expectsToReceive = new HashSet<>();
        actualReceived = new HashSet<>();
        violations = new ArrayList<>();
        consumerMessages = new HashMap<>();
        consumeIntervals = new ArrayList<>();
        actLock = new ReentrantReadWriteLock();
        expLock = new ReentrantReadWriteLock();
        lastReceivedTime = Instant.now();
    }

    public void stopMonitoring() {
        isCancelled = true;

        while(!monitoringStopped)
            ClientUtils.waitFor(1000, false);
    }

    public void received(ReceivedMessage messagePayload) {
        if(enabled) {
            receiveQueue.add(messagePayload);
            lastReceivedTime = Instant.now();
            if(lastReceivedTime.getEpochSecond()-lastReceivedPrinted.getEpochSecond() > 10) {
                System.out.println("Received " + messagePayload.getMessagePayload().getSequenceNumber());
                lastReceivedPrinted = lastReceivedTime;
            }
        }
    }

    public void sent(MessagePayload messagePayload) {
        if(enabled) {
            expLock.writeLock().lock();
            try {
                expectsToReceive.add(messagePayload);
            }
            finally {
                expLock.writeLock().unlock();
            }
            if(Instant.now().getEpochSecond()-lastSentPrinted.getEpochSecond() > 10) {
                System.out.println("Sent " + messagePayload.getSequenceNumber());
                lastSentPrinted = Instant.now();
            }
        }
    }

    public void sendComplete() {
        sendComplete = true;
    }

    public boolean allMessagesReceived() {
        if(sendComplete)
            return getReceivedMissing().isEmpty();
        else
            return false;
    }

    public void monitorProperties() {

        Map<Integer, ReceivedMessage> streamMessages = new HashMap<>();

        // detect ordering and duplication in real-time
        while(!isCancelled) {
            ReceivedMessage msg = receiveQueue.poll();
            if(msg == null) {
                ClientUtils.waitFor(100, this.isCancelled);
            }
            else {
                Integer stream = msg.getMessagePayload().getStream();

                // check ordering property
                if (streamMessages.containsKey(stream)) {
                    ReceivedMessage lastMsg = streamMessages.get(stream);

                    if (lastMsg.getMessagePayload().getSequenceNumber() > msg.getMessagePayload().getSequenceNumber()
                            && !msg.isRedelivered()) {
                        violations.add(new Violation(ViolationType.Ordering, msg.getMessagePayload(), lastMsg.getMessagePayload()));
                    }
                }
                streamMessages.put(stream, msg);

                // check duplicate property
                actLock.writeLock().lock();

                try {

                    if (actualReceived.contains(msg.getMessagePayload()) && !msg.isRedelivered())
                        violations.add(new Violation(ViolationType.NonRedeliveredDuplicate, msg.getMessagePayload()));

                    actualReceived.add(msg.getMessagePayload());
                }
                finally {
                    actLock.writeLock().unlock();
                }

                // check consume interval
                if(consumerMessages.containsKey(msg.getConsumerId())) {
                    ReceivedMessage lastConsumerMsg = consumerMessages.get(msg.getConsumerId());
                    if(msg.getReceiveTimestamp()-lastConsumerMsg.getReceiveTimestamp() > 30000) {
                        consumeIntervals.add(new ConsumeInterval(lastConsumerMsg, msg));
                    }
                }
                consumerMessages.put(msg.getConsumerId(), msg);
            }
        }

        // detect missing at the end
        Set<MessagePayload> missing = getReceivedMissing();

        for(MessagePayload mp : missing)
            violations.add(new Violation(ViolationType.Missing, mp));

        monitoringStopped = true;
    }

    public List<Violation> getViolations() {
        return violations
                .stream()
                .sorted(Comparator.<Violation,Integer>comparing(x -> x.getMessagePayload().getStream())
                        .thenComparing(x -> x.getMessagePayload().getSequenceNumber()))
                .collect(Collectors.toList());
    }

    public Duration durationSinceLastReceipt() {
        return Duration.between(Instant.now(), lastReceivedTime);
    }

    public List<ConsumeInterval> getConsumeIntervals() {
        return consumeIntervals;
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

}
