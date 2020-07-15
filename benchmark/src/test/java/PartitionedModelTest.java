import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import com.jackvanlightly.rabbittesttool.model.*;
import com.jackvanlightly.rabbittesttool.register.NullRegister;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

public class PartitionedModelTest {
    @Test
    public void givenSingleStreamAndInOrder_thenNoOrderingViolations() {
        // ARRANGE
        MessageModel model = getMessageModel(true, false, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(1, 2));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.isEmpty());
    }

    @Test
    public void givenSingleStreamAndOneOutOfOrderAndNotRedelivered_thenSingleOrderViolation() {
        // ARRANGE
        MessageModel model = getMessageModel(true, false, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3));
        model.received(TestMessageUtils.generateReceivedMessage(1, 2));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.size() == 1);
        assertThat(violations.get(0).getViolationType() == ViolationType.Ordering);
        assertThat(violations.get(0).getMessagePayload().getSequenceNumber() == 2);
        assertThat(violations.get(0).getPriorMessagePayload().getSequenceNumber() == 3);
    }

    @Test
    public void givenSingleStreamAndOneOutOfOrderAndRedelivered_thenNoViolation() {
        // ARRANGE
        MessageModel model = getMessageModel(true, false, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        model.received(TestMessageUtils.generateReceivedMessage(1, 3, false));
        model.received(TestMessageUtils.generateReceivedMessage(1, 4, false));
        model.received(TestMessageUtils.generateReceivedMessage(1, 1, true));
        model.received(TestMessageUtils.generateReceivedMessage(1, 2, true));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3, true));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.isEmpty());
    }

    @Test
    public void givenNotCHeckingOrderingAndSingleStreamAndOneOutOfOrderAndNotRedelivered_thenNoViolation() {
        // ARRANGE
        MessageModel model = getMessageModel(false, false, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3));
        model.received(TestMessageUtils.generateReceivedMessage(1, 2));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.isEmpty());
    }

    @Test
    public void givenMultipleStreamsAndInOrder_thenNoOrderingViolations() {
        // ARRANGE
        MessageModel model = getMessageModel(true, false, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(1, 2));
        model.received(TestMessageUtils.generateReceivedMessage(2, 1));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3));
        model.received(TestMessageUtils.generateReceivedMessage(3, 1));
        model.received(TestMessageUtils.generateReceivedMessage(2, 2));
        model.received(TestMessageUtils.generateReceivedMessage(3, 2));
        model.received(TestMessageUtils.generateReceivedMessage(3, 3));
        model.received(TestMessageUtils.generateReceivedMessage(2, 3));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.isEmpty());
    }

    @Test
    public void givenMultipleStreamsAndInOutOfOrderAndNotRedelivered_thenOrderingViolations() {
        // ARRANGE
        MessageModel model = getMessageModel(true, false, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(1, 2));
        model.received(TestMessageUtils.generateReceivedMessage(2, 1));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3));
        model.received(TestMessageUtils.generateReceivedMessage(3, 1));
        model.received(TestMessageUtils.generateReceivedMessage(2, 3));
        model.received(TestMessageUtils.generateReceivedMessage(3, 2));
        model.received(TestMessageUtils.generateReceivedMessage(3, 3));
        model.received(TestMessageUtils.generateReceivedMessage(2, 2));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.size() == 1);
        assertThat(violations.get(0).getViolationType() == ViolationType.Ordering);
        assertThat(violations.get(0).getMessagePayload().getSequence() == 2);
        assertThat(violations.get(0).getMessagePayload().getSequenceNumber() == 2);
        assertThat(violations.get(0).getPriorMessagePayload().getSequenceNumber() == 3);
    }

    @Test
    public void givenSingleStreamAndNoDuplicates_thenNoDuplicateViolations() {
        // ARRANGE
        MessageModel model = getMessageModel(false, false, true);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(1, 2));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.isEmpty());
    }

    @Test
    public void givenSingleStreamAndSingleDuplicateNotRedelivered_thenSingleDuplicateViolation() {
        // ARRANGE
        MessageModel model = getMessageModel(false, false, true);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        MessagePayload duplicate = TestMessageUtils.generateMessagePayload(1, 2);
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(duplicate));
        model.received(TestMessageUtils.generateReceivedMessage(duplicate));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.size() == 1);
        assertThat(violations.get(0).getViolationType() == ViolationType.NonRedeliveredDuplicate);
        assertThat(violations.get(0).getMessagePayload().getSequenceNumber() == 2);
    }

    @Test
    public void givenMultipleStreamAndSingleDuplicateNotRedelivered_thenSingleDuplicateViolation() {
        // ARRANGE
        MessageModel model = getMessageModel(false, false, true);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        MessagePayload duplicate = TestMessageUtils.generateMessagePayload(1, 2);
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(2, 1));
        model.received(TestMessageUtils.generateReceivedMessage(duplicate));
        model.received(TestMessageUtils.generateReceivedMessage(2, 2));
        model.received(TestMessageUtils.generateReceivedMessage(duplicate));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3));
        model.received(TestMessageUtils.generateReceivedMessage(2, 3));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.size() == 1);
        assertThat(violations.get(0).getViolationType() == ViolationType.NonRedeliveredDuplicate);
        assertThat(violations.get(0).getMessagePayload().getSequence() == 1);
        assertThat(violations.get(0).getMessagePayload().getSequenceNumber() == 2);
    }

    @Test
    public void givenSingleStreamAndSingleOutOfOrderDuplicateNotRedelivered_thenSingleDuplicateViolation() {
        // ARRANGE
        MessageModel model = getMessageModel(false, false, true);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        MessagePayload duplicate = TestMessageUtils.generateMessagePayload(1, 2);
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(duplicate));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3));
        model.received(TestMessageUtils.generateReceivedMessage(duplicate));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.size() == 1);
        assertThat(violations.get(0).getViolationType() == ViolationType.NonRedeliveredDuplicate);
        assertThat(violations.get(0).getMessagePayload().getSequenceNumber() == 2);
    }

    @Test
    public void givenCheckOrderingTooAndSingleStreamAndSingleOutOfOrderDuplicateNotRedelivered_thenDuplicateAndOrderingViolation() {
        // ARRANGE
        MessageModel model = getMessageModel(true, false, true);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        MessagePayload duplicate = TestMessageUtils.generateMessagePayload(1, 2);
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(duplicate));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3));
        model.received(TestMessageUtils.generateReceivedMessage(duplicate));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.size() == 2);

        Optional<Violation> orderingViolation = violations.stream().filter(x -> x.getViolationType() == ViolationType.Ordering).findFirst();
        Optional<Violation> duplicateViolation = violations.stream().filter(x -> x.getViolationType() == ViolationType.NonRedeliveredDuplicate).findFirst();

        assertThat(orderingViolation.isPresent());
        assertThat(duplicateViolation.isPresent());

        if(orderingViolation.isPresent())
            assertThat(orderingViolation.get().getMessagePayload().getSequenceNumber() == 2);
        if(duplicateViolation.isPresent())
            assertThat(duplicateViolation.get().getMessagePayload().getSequenceNumber() == 2);
    }

    @Test
    public void givenSingleStreamAndSingleDuplicateRedelivered_thenNoViolation() {
        // ARRANGE
        MessageModel model = getMessageModel(false, false, true);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        MessagePayload duplicate = TestMessageUtils.generateMessagePayload(1, 2);
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(duplicate));
        model.received(TestMessageUtils.generateReceivedMessage(duplicate, true));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.isEmpty());
    }

    @Test
    public void givenNotCheckingDuplicatesAndSingleStreamAndSingleDuplicateNotRedelivered_thenNoViolation() {
        // ARRANGE
        MessageModel model = getMessageModel(false, false, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        MessagePayload duplicate = TestMessageUtils.generateMessagePayload(1, 2);
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(duplicate));
        model.received(TestMessageUtils.generateReceivedMessage(duplicate));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.isEmpty());
    }

    @Test
    public void givenSingleStreamAndSingleAllMessagesReceived_thenNoMessageLossViolation() {
        // ARRANGE
        MessageModel model = getMessageModel(false, true, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        model.sent(TestMessageUtils.generateMessagePayload(1, 1));
        model.sent(TestMessageUtils.generateMessagePayload(1, 2));
        model.sent(TestMessageUtils.generateMessagePayload(1, 3));
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(1, 2));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.isEmpty());
    }

    @Test
    public void givenSingleStreamAndAllMessagesReceivedOutOfOrder_thenNoMessageLossViolation() {
        // ARRANGE
        MessageModel model = getMessageModel(false, true, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        model.sent(TestMessageUtils.generateMessagePayload(1, 1));
        model.sent(TestMessageUtils.generateMessagePayload(1, 2));
        model.sent(TestMessageUtils.generateMessagePayload(1, 3));
        model.sent(TestMessageUtils.generateMessagePayload(1, 4));
        model.sent(TestMessageUtils.generateMessagePayload(1, 5));
        model.sent(TestMessageUtils.generateMessagePayload(1, 6));
        model.sent(TestMessageUtils.generateMessagePayload(1, 7));
        model.sent(TestMessageUtils.generateMessagePayload(1, 8));
        model.sent(TestMessageUtils.generateMessagePayload(1, 9));
        model.sent(TestMessageUtils.generateMessagePayload(1, 10));

        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3));
        model.received(TestMessageUtils.generateReceivedMessage(1, 7));
        model.received(TestMessageUtils.generateReceivedMessage(1, 2));
        model.received(TestMessageUtils.generateReceivedMessage(1, 9));
        model.received(TestMessageUtils.generateReceivedMessage(1, 8));
        model.received(TestMessageUtils.generateReceivedMessage(1, 10));
        model.received(TestMessageUtils.generateReceivedMessage(1, 2));
        model.received(TestMessageUtils.generateReceivedMessage(1, 4));
        model.received(TestMessageUtils.generateReceivedMessage(1, 6));
        model.received(TestMessageUtils.generateReceivedMessage(1, 5));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.isEmpty());
    }

    @Test
    public void givenSingleStreamAndSingleMessageNotReceived_thenSingleMessageLossViolation() {
        // ARRANGE
        MessageModel model = getMessageModel(false, true, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        model.sent(TestMessageUtils.generateMessagePayload(1, 1));
        model.sent(TestMessageUtils.generateMessagePayload(1, 2));
        model.sent(TestMessageUtils.generateMessagePayload(1, 3));
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        WaitUtils.waitFor(500);

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.size() == 1);
        assertThat(violations.get(0).getViolationType() == ViolationType.Missing);
        assertThat(violations.get(0).getSpan().getLow() == 2);
        assertThat(violations.get(0).getSpan().getHigh() == 2);
    }

    @Test
    public void givenSingleStreamAndSingleMessageNotReceivedPassesThreshold_thenSingleMessageLossViolationDetectedBeforeEnd() {
        // ARRANGE
        int thresholdSec = 1;
        MessageModel model = getMessageModel(Duration.ofSeconds(thresholdSec), 1, Duration.ofMillis(500), false, true, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        model.sent(TestMessageUtils.generateMessagePayload(1, 1));
        model.sent(TestMessageUtils.generateMessagePayload(1, 2));
        model.sent(TestMessageUtils.generateMessagePayload(1, 3));
        model.sent(TestMessageUtils.generateMessagePayload(1, 4));

        // first three will get moved to closed spans and be message loss detectable
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(1, 2));
        model.received(TestMessageUtils.generateReceivedMessage(1, 4));
        WaitUtils.waitFor(thresholdSec*2*1000);
        // span 1-2 closed here
        model.received(TestMessageUtils.generateReceivedMessage(1, 5));
        WaitUtils.waitFor(thresholdSec*2*1000);
        // span 4-5 cl// span 1-2 closed hereosed here and message loss of 3 detected
        model.received(TestMessageUtils.generateReceivedMessage(1, 6));
        WaitUtils.waitFor(5000);

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.size() == 1);
        assertThat(violations.get(0).getViolationType() == ViolationType.Missing);
        assertThat(violations.get(0).getSpan().getLow() == 2);
        assertThat(violations.get(0).getSpan().getHigh() == 2);

        model.stopMonitoring();
    }

    @Test
    public void givenSingleStreamAndMultipleContiguousMessagesNotReceived_thenSingleMessageLossViolation() {
        // ARRANGE
        MessageModel model = getMessageModel(false, true, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        model.sent(TestMessageUtils.generateMessagePayload(1, 1));
        model.sent(TestMessageUtils.generateMessagePayload(1, 2));
        model.sent(TestMessageUtils.generateMessagePayload(1, 3));
        model.sent(TestMessageUtils.generateMessagePayload(1, 4));
        model.sent(TestMessageUtils.generateMessagePayload(1, 5));
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(1, 5));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        WaitUtils.waitFor(500);

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.size() == 1);
        assertThat(violations.get(0).getViolationType() == ViolationType.Missing);
        assertThat(violations.get(0).getSpan().getLow() == 2);
        assertThat(violations.get(0).getSpan().getHigh() == 4);
    }

    @Test
    public void givenSingleStreamAndMultipleNonContiguousMessagesNotReceived_thenMultipleMessageLossViolations() {
        // ARRANGE
        MessageModel model = getMessageModel(false, true, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        model.sent(TestMessageUtils.generateMessagePayload(1, 1));
        model.sent(TestMessageUtils.generateMessagePayload(1, 2));
        model.sent(TestMessageUtils.generateMessagePayload(1, 3));
        model.sent(TestMessageUtils.generateMessagePayload(1, 4));
        model.sent(TestMessageUtils.generateMessagePayload(1, 5));
        model.sent(TestMessageUtils.generateMessagePayload(1, 6));
        model.sent(TestMessageUtils.generateMessagePayload(1, 7));
        model.sent(TestMessageUtils.generateMessagePayload(1, 8));
        model.sent(TestMessageUtils.generateMessagePayload(1, 9));
        model.sent(TestMessageUtils.generateMessagePayload(1, 10));
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(1, 4));
        model.received(TestMessageUtils.generateReceivedMessage(1, 7));
        model.received(TestMessageUtils.generateReceivedMessage(1, 9));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        WaitUtils.waitFor(500);

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.size() == 3);
        assertThat(violations.get(0).getViolationType() == ViolationType.Missing);
        assertThat(violations.get(0).getSpan().getLow() == 2);
        assertThat(violations.get(0).getSpan().getHigh() == 3);
        assertThat(violations.get(1).getViolationType() == ViolationType.Missing);
        assertThat(violations.get(1).getSpan().getLow() == 5);
        assertThat(violations.get(1).getSpan().getHigh() == 6);
        assertThat(violations.get(2).getViolationType() == ViolationType.Missing);
        assertThat(violations.get(2).getSpan().getLow() == 10);
        assertThat(violations.get(2).getSpan().getHigh() == 10);
    }

    @Test
    public void givenNotCheckingMessageLossAndSingleStreamAndMessagesNotReceived_thenNoMessageLossViolation() {
        // ARRANGE
        MessageModel model = getMessageModel(false, true, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        model.sent(TestMessageUtils.generateMessagePayload(1, 1));
        model.sent(TestMessageUtils.generateMessagePayload(1, 2));
        model.sent(TestMessageUtils.generateMessagePayload(1, 3));
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        model.received(TestMessageUtils.generateReceivedMessage(1, 3));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        WaitUtils.waitFor(500);

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.isEmpty());
    }

    @Test
    public void givenSingleStreamAndGapInReceiveWithinThreshold_thenNoConsumeInterval() {
        // ARRANGE
        MessageModel model = getMessageModel(false, false, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        model.sent(TestMessageUtils.generateMessagePayload(1, 1));
        model.sent(TestMessageUtils.generateMessagePayload(1, 2));
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        WaitUtils.waitFor(3000);
        model.received(TestMessageUtils.generateReceivedMessage(1, 2));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        WaitUtils.waitFor(500);

        // ASSERT
        List<ConsumeInterval> intervals = model.getConsumeIntervals();
        assertThat(intervals.isEmpty());
    }

    @Test
    public void givenSingleStreamAndGapInReceiveExceedsThreshold_thenSingleConsumeInterval() {
        // ARRANGE
        MessageModel model = getMessageModel(false, false, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        model.sent(TestMessageUtils.generateMessagePayload(1, 1));
        model.sent(TestMessageUtils.generateMessagePayload(1, 2));
        model.received(TestMessageUtils.generateReceivedMessage(1, 1));
        WaitUtils.waitFor(6000);
        model.received(TestMessageUtils.generateReceivedMessage(1, 2));

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        WaitUtils.waitFor(500);

        // ASSERT
        List<ConsumeInterval> intervals = model.getConsumeIntervals();
        assertThat(intervals.size() == 1);
    }

    @Test
    public void givenMultipleConsumersAndGapInReceiveExceedsThreshold_thenMultipleConsumeIntervals() {
        // ARRANGE
        MessageModel model = getMessageModel(false, false, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);
        int consumerCount = 5;

        // ACT
        for(int i=0; i<50; i++) {
            for (int s = 1; s <= 10; s++)
                model.received(TestMessageUtils.generateReceivedMessage(s, i, false, "c" + (i % consumerCount)));
        }

        WaitUtils.waitFor(6000);

        // consumer intervals occur here
        for(int i=50; i<100; i++) {
            for (int s = 1; s <= 10; s++)
                model.received(TestMessageUtils.generateReceivedMessage(s, i + 50, false, "c" + (i % consumerCount)));
        }

        WaitUtils.waitFor(6000);

        // consumer intervals occur here
        for(int i=100; i<150; i++) {
            for (int s = 1; s <= 10; s++)
                model.received(TestMessageUtils.generateReceivedMessage(s, i + 50, false, "c" + (i % consumerCount)));
        }

        WaitUtils.waitFor(500);

        model.stopMonitoring();

        WaitUtils.waitFor(500);

        // ASSERT
        List<ConsumeInterval> intervals = model.getConsumeIntervals();
        assertThat(intervals.size() == (consumerCount*2));
    }

    @Test
    public void givenHighLoadWithNoIssues_thenNoViolations() {
        // ARRANGE
        MessageModel model = getMessageModel(true, true, true);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        for(int seqNo=0; seqNo<1000000; seqNo++) {
            for(int stream=1; stream<10; stream++) {
                model.sent(TestMessageUtils.generateMessagePayload(stream, seqNo));
                model.received(TestMessageUtils.generateReceivedMessage(stream, seqNo));
            }
        }

        WaitUtils.waitFor(5000);

        model.stopMonitoring();

        WaitUtils.waitFor(500);

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.isEmpty());
    }

    @Test
    public void givenHighLoadWithIssues_thenViolationsDetected() {
        // ARRANGE
        MessageModel model = getMessageModel(true, true, true);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        for(int seqNo=0; seqNo<1000000; seqNo++) {
            for(int stream=1; stream<10; stream++) {
                model.sent(TestMessageUtils.generateMessagePayload(stream, seqNo));

                if(stream == 2 && seqNo == 473425) {
                    // lost message
                }
                else if(stream == 7 && seqNo == 473425) {
                    // duplicate message
                    MessagePayload duplicate = TestMessageUtils.generateMessagePayload(stream, seqNo);
                    model.received(TestMessageUtils.generateReceivedMessage(duplicate));
                    model.received(TestMessageUtils.generateReceivedMessage(duplicate));
                }
                else if(stream == 9 && seqNo == 844646) {
                    // out of order message
                    model.received(TestMessageUtils.generateReceivedMessage(stream, seqNo));
                    model.received(TestMessageUtils.generateReceivedMessage(stream, seqNo-10));
                }
            }
        }

        WaitUtils.waitFor(5000);

        model.stopMonitoring();

        WaitUtils.waitFor(500);

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.size() == 3);
    }

    @Test
    public void givenLongRunningWithIssues_thenViolationsDetected() {
        // ARRANGE
        MessageModel model = getMessageModel(true, true, true);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        // ACT
        for(int seqNo=0; seqNo<30; seqNo++) {
            for(int stream=1; stream<2; stream++) {
                model.sent(TestMessageUtils.generateMessagePayload(stream, seqNo));

                if(seqNo % 10 == 0) {
                    // lost message
                }
                else if(seqNo % 11 == 0) {
                    // duplicate message
                    MessagePayload duplicate = TestMessageUtils.generateMessagePayload(stream, seqNo);
                    model.received(TestMessageUtils.generateReceivedMessage(duplicate));
                    model.received(TestMessageUtils.generateReceivedMessage(duplicate));
                }
                else if(seqNo % 12 == 0) {
                    // out of order message
                    model.received(TestMessageUtils.generateReceivedMessage(stream, seqNo));
                    model.received(TestMessageUtils.generateReceivedMessage(stream, seqNo-10));
                }
            }

            WaitUtils.waitFor(1000);
        }

        WaitUtils.waitFor(5000);

        model.stopMonitoring();

        WaitUtils.waitFor(500);

        // ASSERT
        List<Violation> violations = model.getViolations();
        assertThat(violations.size() == 3);
    }

    @Test
    public void givenSingleStreamAndLastMessagesUnconsumed_thenNoDataLossViolationsButUnconsumedRemainderInstead() {
        // ARRANGE
        MessageModel model = getMessageModel(true, false, false);
        ExecutorService executorService = Executors.newCachedThreadPool();
        model.monitorProperties(executorService);

        for(int i=0; i<10; i++)
            model.sent(TestMessageUtils.generateMessagePayload(1, i));

        // ACT
        for(int i=0; i<4; i++)
            model.received(TestMessageUtils.generateReceivedMessage(1, i));


        WaitUtils.waitFor(500);

        model.stopMonitoring();

        // ASSERT
        long count = model.getUnconsumedRemainderCount();
        assertThat(count == 6);
    }

    private PartitionedModel getMessageModel(boolean checkOrdering, boolean checkDataLoss, boolean checkDuplicates) {
        return getMessageModel(Duration.ofSeconds(10),
                10,
                 Duration.ofSeconds(5),
                 checkOrdering,
                 checkDataLoss,
                 checkDuplicates);
    }

    private PartitionedModel getMessageModel(Duration messageLossThresholdDuration,
                                             int messageLossThresholdMsgs,
                                             Duration houseKeepingInterval,
                                             boolean checkOrdering,
                                             boolean checkDataLoss,
                                             boolean checkDuplicates) {
        return new PartitionedModel(new NullRegister(),
                30,
                messageLossThresholdDuration,
                messageLossThresholdMsgs,
                houseKeepingInterval,
                checkOrdering,
                checkDataLoss,
                checkDuplicates,
                false,
                false,
                false,
                false,
                false,
                false);
    }
}
