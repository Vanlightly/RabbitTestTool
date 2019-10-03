package com.jackvanlightly.rabbittesttool.model;

import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;

import java.util.*;
import java.util.stream.Collectors;

public class MessageModel {
    private Set<MessagePayload> expectsToReceive;
    private Set<MessagePayload> actualReceived;
    private Queue<ReceivedMessage> receiveQueue;
    private List<Violation> violations;
    private boolean isCancelled;
    private boolean enabled;

    public MessageModel(boolean enabled) {
        this.enabled = enabled;
        receiveQueue = new ArrayDeque<>();
        expectsToReceive = new HashSet<>();
        actualReceived = new HashSet<>();
        violations = new ArrayList<>();
    }

    public void stopMonitoring() {
        isCancelled = true;
    }

    public void received(ReceivedMessage messagePayload) {
        if(enabled)
            receiveQueue.add(messagePayload);
    }

    public void sent(MessagePayload messagePayload) {
        if(enabled)
            expectsToReceive.add(messagePayload);
    }

    public Set<MessagePayload> getReceivedNonConfirmed() {
        Set<MessagePayload> actualReceivedCopy = new HashSet<MessagePayload>(actualReceived);
        actualReceivedCopy.removeAll(expectsToReceive);

        return actualReceivedCopy;
    }

    public boolean allMessagesReceived() {
        return getReceivedMissing().isEmpty();
    }

    public void monitorProperties() {

        ReceivedMessage lastMsg = null;

        // detect ordering and duplication in real-time
        while(!isCancelled) {
            ReceivedMessage msg = receiveQueue.poll();
            if(msg == null) {
                ClientUtils.waitFor(100, this.isCancelled);
            }
            else {
                if(actualReceived.contains(msg.getMessagePayload()) && !msg.isRedelivered())
                    violations.add(new Violation(ViolationType.NonRedeliveredDuplicate, msg.getMessagePayload()));

                if(lastMsg != null) {
                    if(lastMsg.getMessagePayload().getSequenceNumber() > msg.getMessagePayload().getSequenceNumber()
                        && !msg.isRedelivered()) {
                        violations.add(new Violation(ViolationType.Ordering, msg.getMessagePayload()));
                    }
                }

                actualReceived.add(msg.getMessagePayload());
                lastMsg = msg;
            }
        }

        // detect missing at the end
        Set<MessagePayload> missing = getReceivedMissing();

        for(MessagePayload mp : missing)
            violations.add(new Violation(ViolationType.Missing, mp));
    }

    public List<Violation> getViolations() {
        return violations
                .stream()
                .sorted(Comparator.<Violation,Integer>comparing(x -> x.getMessagePayload().getStream())
                        .thenComparing(x -> x.getMessagePayload().getSequenceNumber()))
                .collect(Collectors.toList());
    }

    private Set<MessagePayload> getReceivedMissing() {
        Set<MessagePayload> expectedCopy = new HashSet<>(expectsToReceive);
        expectedCopy.removeAll(actualReceived);

        return expectedCopy;
    }
}
