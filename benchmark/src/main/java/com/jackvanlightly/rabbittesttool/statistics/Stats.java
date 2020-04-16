package com.jackvanlightly.rabbittesttool.statistics;

import com.jackvanlightly.rabbittesttool.BrokerConfiguration;
import com.jackvanlightly.rabbittesttool.clients.consumers.ConsumerGroup;
import com.jackvanlightly.rabbittesttool.clients.publishers.PublisherGroup;
import com.jackvanlightly.rabbittesttool.register.StepStatistics;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.core.instrument.util.NamedThreadFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;

public class Stats {

    ScheduledExecutorService reportExecutor;

    public static boolean RecordingActive;
    protected final long samplingIntervalMs;
    protected final long startTime;
    protected Instant recordStart;
    protected Instant recordStop;

    private final Consumer<Long> updateLatency;
    private final Consumer<Long> updateConfirmLatency;
    private final Consumer<Long> updateConfirmMultipleFlag;
    private final DoubleAccumulator published, returned, confirmed, nacked, consumed;
    private final DoubleAccumulator publishedMsgBytes, consumedMsgBytes, publishedMsgSize, consumedMsgSize, publishedMsgHeaders, consumedMsgHeaders;
    private final DoubleAccumulator deliveryMode, consumerPrefetch, consumerAck, publisherInFlightLimit;
    private final DoubleAccumulator consumerCount, publisherCount, queueCount, targetPublishRate, consumerConnectionError;
    private final DoubleAccumulator blockedPublisherConnectionRate, unblockedPublisherConnectionRate, routingKeyLength;
    private final DoubleAccumulator perPublisherRateMin, perPublisherRate5, perPublisherRate25, perPublisherRate50, perPublisherRate75, perPublisherRate95, perPublisherRateMax;
    private final DoubleAccumulator perConsumerRateMin, perConsumerRate5, perConsumerRate25, perConsumerRate50, perConsumerRate75, perConsumerRate95, perConsumerRateMax;
    protected long lastStatsTime;
    protected long sendCountInterval;
    protected long returnCountInterval;
    protected long confirmCountInterval;
    protected long nackCountInterval;
    protected long recvCountInterval;
    protected long publishedMsgBytesInterval;
    protected long consumedMsgBytesInterval;
    protected long publishedMsgHeadersInterval;
    protected long consumedMsgHeadersInterval;
    protected long deliveryModeInterval;
    protected long consumerPrefetchInterval;
    protected long consumerAckInterval;
    protected long currentConsumerCount;
    protected long currentPublisherCount;
    protected long currentQueueCount;
    protected long targetPublishRateCount;
    protected long publisherInFlightLimitCount;
    protected long consumerConnectionErrorInterval;
    protected long blockedPublisherConnectionInterval;
    protected long unblockedPublisherConnectionInterval;
    protected long routingKeyLengthInterval;

    protected long sendCountStepTotal;
    protected long recvCountStepTotal;
    protected long sendBytesCountStepTotal;
    protected long recvBytesCountStepTotal;

    protected long elapsedInterval;
    protected long elapsedTotal;

    protected Histogram latencies = new MetricRegistry().histogram("latency");
    protected Histogram confirmLatencies = new MetricRegistry().histogram("confirm-latency");
    protected Histogram sendRates = new MetricRegistry().histogram("send-rates");
    protected Histogram receiveRates = new MetricRegistry().histogram("receive-rates");

    protected List<PublisherGroup> publisherGroups;
    protected List<ConsumerGroup> consumerGroups;

    public Stats(long samplingIntervalMs,
                 BrokerConfiguration brokerConfig) {
        this(samplingIntervalMs, brokerConfig, "?", new SimpleMeterRegistry(), "_amqp");
    }

    public Stats(long samplingIntervalMs,
                 BrokerConfiguration brokerConfig,
                 String instance,
                 MeterRegistry registry,
                 String metricsPrefix) {
        this.samplingIntervalMs = samplingIntervalMs;
        startTime = System.currentTimeMillis();

        metricsPrefix = metricsPrefix == null ? "" : metricsPrefix;
        List<Tag> tags = getTags(brokerConfig, instance);

        Timer latencyTimer = Timer
                .builder(metricsPrefix + "latency")
                .description("message latency")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
                .distributionStatisticExpiry(Duration.ofMillis(this.samplingIntervalMs))
                .tags(tags)
                .register(registry);

        Timer confirmLatencyTimer = Timer
                .builder(metricsPrefix + "confirm-latency")
                .description("confirm latency")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
                .distributionStatisticExpiry(Duration.ofMillis(this.samplingIntervalMs))
                .tags(tags)
                .register(registry);

        Timer confirmMultipleFlagTimer = Timer
                .builder(metricsPrefix + "confirm-multiple-flag")
                .description("confirm multiple flag usage")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
                .distributionStatisticExpiry(Duration.ofMillis(this.samplingIntervalMs))
                .tags(tags)
                .register(registry);

        DoubleBinaryOperator accumulatorFunction = (x, y) -> y;

        published = registry.gauge(metricsPrefix + "published", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        returned = registry.gauge(metricsPrefix + "returned", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        confirmed = registry.gauge(metricsPrefix + "confirmed", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        nacked = registry.gauge(metricsPrefix + "nacked", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        consumed = registry.gauge(metricsPrefix + "consumed", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        publishedMsgBytes = registry.gauge(metricsPrefix + "published-msg-bytes", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        consumedMsgBytes = registry.gauge(metricsPrefix + "consumed-msg-bytes", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        publishedMsgSize = registry.gauge(metricsPrefix + "published-msg-size", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        consumedMsgSize = registry.gauge(metricsPrefix + "consumed-msg-size", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        publishedMsgHeaders = registry.gauge(metricsPrefix + "published-msg-headers", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        consumedMsgHeaders = registry.gauge(metricsPrefix + "consumed-msg-headers", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        deliveryMode = registry.gauge(metricsPrefix + "publish-msg-del-mode", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        consumerPrefetch = registry.gauge(metricsPrefix + "consumer-prefetch", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        consumerAck = registry.gauge(metricsPrefix + "consumer-ack-interval", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        publisherInFlightLimit = registry.gauge(metricsPrefix + "publisher-in-flight-limit", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        consumerCount = registry.gauge(metricsPrefix + "consumer-count", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        publisherCount = registry.gauge(metricsPrefix + "publisher-count", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        queueCount = registry.gauge(metricsPrefix + "queue-count", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        targetPublishRate = registry.gauge(metricsPrefix + "target-publish-rate", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        consumerConnectionError = registry.gauge(metricsPrefix + "consumer-conn-errors", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        blockedPublisherConnectionRate = registry.gauge(metricsPrefix + "publisher-blocked", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        unblockedPublisherConnectionRate = registry.gauge(metricsPrefix + "publisher-unblocked", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        routingKeyLength = registry.gauge(metricsPrefix + "routingkey-length", tags, new DoubleAccumulator(accumulatorFunction, 0.0));

        perPublisherRateMin = registry.gauge(metricsPrefix + "per-publisher-rate", getTags(tags, "phi", "min"), new DoubleAccumulator(accumulatorFunction, 0.0));
        perPublisherRate5 = registry.gauge(metricsPrefix + "per-publisher-rate", getTags(tags, "phi", "5"), new DoubleAccumulator(accumulatorFunction, 0.0));
        perPublisherRate25 = registry.gauge(metricsPrefix + "per-publisher-rate", getTags(tags, "phi", "25"), new DoubleAccumulator(accumulatorFunction, 0.0));
        perPublisherRate50 = registry.gauge(metricsPrefix + "per-publisher-rate", getTags(tags, "phi", "50"), new DoubleAccumulator(accumulatorFunction, 0.0));
        perPublisherRate75 = registry.gauge(metricsPrefix + "per-publisher-rate", getTags(tags, "phi", "75"), new DoubleAccumulator(accumulatorFunction, 0.0));
        perPublisherRate95 = registry.gauge(metricsPrefix + "per-publisher-rate", getTags(tags, "phi", "95"), new DoubleAccumulator(accumulatorFunction, 0.0));
        perPublisherRateMax = registry.gauge(metricsPrefix + "per-publisher-rate", getTags(tags, "phi", "max"), new DoubleAccumulator(accumulatorFunction, 0.0));

        perConsumerRateMin = registry.gauge(metricsPrefix + "per-consumer-rate", getTags(tags, "phi", "min"), new DoubleAccumulator(accumulatorFunction, 0.0));
        perConsumerRate5 = registry.gauge(metricsPrefix + "per-consumer-rate", getTags(tags, "phi", "5"), new DoubleAccumulator(accumulatorFunction, 0.0));
        perConsumerRate25 = registry.gauge(metricsPrefix + "per-consumer-rate", getTags(tags, "phi", "25"), new DoubleAccumulator(accumulatorFunction, 0.0));
        perConsumerRate50 = registry.gauge(metricsPrefix + "per-consumer-rate", getTags(tags, "phi", "50"), new DoubleAccumulator(accumulatorFunction, 0.0));
        perConsumerRate75 = registry.gauge(metricsPrefix + "per-consumer-rate", getTags(tags, "phi", "75"), new DoubleAccumulator(accumulatorFunction, 0.0));
        perConsumerRate95 = registry.gauge(metricsPrefix + "per-consumer-rate", getTags(tags, "phi", "95"), new DoubleAccumulator(accumulatorFunction, 0.0));
        perConsumerRateMax = registry.gauge(metricsPrefix + "per-consumer-rate", getTags(tags, "phi", "max"), new DoubleAccumulator(accumulatorFunction, 0.0));

        updateLatency = latency -> latencyTimer.record(latency, TimeUnit.NANOSECONDS);
        updateConfirmLatency = latency -> confirmLatencyTimer.record(latency, TimeUnit.NANOSECONDS);
        updateConfirmMultipleFlag = confirms -> confirmMultipleFlagTimer.record(confirms, TimeUnit.MILLISECONDS); // a hack

        reset(startTime);
        startReportTimer();
    }

    private void startReportTimer(){
        TimerTask repeatedTask = new TimerTask() {
            public void run() {
                report();
            }
        };
        reportExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("ReportStats"));
        long delay  = 1000L;
        long period = 1000L;
        reportExecutor.scheduleAtFixedRate(repeatedTask, delay, period, TimeUnit.MILLISECONDS);
    }

    public void close() {
        reportExecutor.shutdown();
    }

    private double rate(long count, long elapsed) {
        return 1000.0 * count / elapsed;
    }

    private void recordStats(long now) {
        recordPublisherCount(currentPublisherCount);
        recordConsumerCount(currentConsumerCount);
        recordQueueCount(currentQueueCount);
        recordInFlightLimit(publisherInFlightLimitCount);
        recordTargetPublishRate(targetPublishRateCount);

        double ratePublished = rate(sendCountInterval, elapsedInterval);
        recordPublished(ratePublished);

        double ratePublishedBytes = rate(publishedMsgBytesInterval, elapsedInterval);
        recordPublishedBytes(ratePublishedBytes);

        if(consumerConnectionErrorInterval > 0)
            recordConsumerConnError(consumerConnectionErrorInterval);

        double blockedPublisherRate = rate(blockedPublisherConnectionInterval, elapsedInterval);
        recordPublisherBlocked(blockedPublisherRate);

        double unblockedPublisherRate = rate(unblockedPublisherConnectionInterval, elapsedInterval);
        recordPublisherUnblocked(unblockedPublisherRate);

        if(sendCountInterval > 0) {
            double avgPublishedMsgSize = publishedMsgBytesInterval / sendCountInterval;
            recordPublishedMsgSize(avgPublishedMsgSize);

            double avgPublishedMsgHeaders = publishedMsgHeadersInterval / sendCountInterval;
            recordPublishedMsgHeaders(avgPublishedMsgHeaders);

            double avgDeliveryMode = deliveryModeInterval / sendCountInterval;
            recordDeliveryMode(avgDeliveryMode);

            double avgRoutingKeyLength = routingKeyLengthInterval / sendCountInterval;
            recordRoutingKeyLength(avgRoutingKeyLength);

            List<Long> allSendCounts = new ArrayList<>();
            for(PublisherGroup publisherGroup : publisherGroups) {
                List<Long> sendCounts = publisherGroup.getRealSendCounts();
                for(Long count : sendCounts) {
                    allSendCounts.add((long)rate(count, elapsedInterval));
                }
            }
            recordPerPublisherPublished(allSendCounts);

            List<Long> allReceiveCounts = new ArrayList<>();
            for(ConsumerGroup consumerGroup : consumerGroups) {
                List<Long> receiveCounts = consumerGroup.getRealReceiveCounts();
                for(Long count : receiveCounts) {
                    allReceiveCounts.add((long)rate(count, elapsedInterval));
                }
            }
            recordPerConsumerReceived(allReceiveCounts);
        }

        double rateReturned = rate(returnCountInterval, elapsedInterval);
        recordReturned(rateReturned);

        double rateConfirmed = rate(confirmCountInterval, elapsedInterval);
        recordConfirmed(rateConfirmed);

        double rateNacked = rate(nackCountInterval, elapsedInterval);
        recordNacked(rateNacked);

        double rateConsumed = rate(recvCountInterval, elapsedInterval);
        recordReceived(rateConsumed);

        double rateConsumedBytes = rate(consumedMsgBytesInterval, elapsedInterval);
        recordReceivedBytes(rateConsumedBytes);

        if(recvCountInterval > 0) {
            double avgConsumedMsgSize = consumedMsgBytesInterval / recvCountInterval;
            recordReceivedMsgSize(avgConsumedMsgSize);

            double avgConsumedMsgHeaders = consumedMsgHeadersInterval / recvCountInterval;
            recordReceivedMsgHeaders(avgConsumedMsgHeaders);

            double consumerAcks = consumerAckInterval / recvCountInterval;
            recordConsumerAck(consumerAcks);

            double consumerPrefetchVal = consumerPrefetchInterval / recvCountInterval;
            recordConsumerPrefetch(consumerPrefetchVal);
        }
    }


    public void addClientGroups(List<PublisherGroup> publisherGroups, List<ConsumerGroup> consumerGroups) {
        this.publisherGroups = publisherGroups;
        this.consumerGroups = consumerGroups;
    }

    public void startRecordingStep() {
        this.recordStart = Instant.now();
        RecordingActive = true;
    }

    public void stopRecordingStep() {
        this.recordStop = Instant.now();
        RecordingActive = false;
    }

    private List<Tag> getTags(BrokerConfiguration brokerConfig, String instance) {
        String node = brokerConfig.getNodeNames().stream().min(Comparator.comparing(String::valueOf)).get();
        return new ArrayList<>(Arrays.asList(
                Tag.of("technology", brokerConfig.getTechnology()),
                Tag.of("version", brokerConfig.getVersion()),
                Tag.of("instance", instance),
                Tag.of("node", node)
        ));
    }

    private List<Tag> getTags(List<Tag> tags, String tagKey, String tagValue) {
        List<Tag> newTags = new ArrayList<>(tags);
        newTags.add(Tag.of(tagKey, tagValue));
        return newTags;
    }

    private void reset(long t) {
        lastStatsTime = t;

        sendCountInterval = 0;
        returnCountInterval = 0;
        confirmCountInterval = 0;
        nackCountInterval = 0;
        recvCountInterval = 0;
        publishedMsgBytesInterval = 0;
        consumedMsgBytesInterval = 0;
        publishedMsgHeadersInterval = 0;
        consumedMsgHeadersInterval = 0;
        deliveryModeInterval = 0;
        consumerAckInterval = 0;
        consumerPrefetchInterval = 0;
        consumerConnectionErrorInterval = 0;
        blockedPublisherConnectionInterval = 0;
        unblockedPublisherConnectionInterval = 0;
        routingKeyLengthInterval = 0;
    }

    private synchronized void report() {
        long now = System.currentTimeMillis();
        elapsedInterval = now - lastStatsTime;

        if (elapsedInterval >= samplingIntervalMs) {
            elapsedTotal += elapsedInterval;
            recordStats(now);
            reset(now);
        }
    }

    public void setConsumerCount(int consumerCount) {
        currentConsumerCount = consumerCount;
    }

    public void setPublisherCount(int publisherCount) {
        currentPublisherCount = publisherCount;
    }

    public void setQueueCount(int queueCount) {
        currentQueueCount = queueCount;
    }

    public void setTargetPublishRate(int targetRate) {
        targetPublishRateCount = targetRate;
    }

    public void setPublisherInFlightLimit(int inFlightLimit) {
        publisherInFlightLimitCount = inFlightLimit;
    }

    public synchronized void handleSend(long msgBytes,
                                        int msgHeaders,
                                        int deliveryMode,
                                        int routingKeyLength) {
        if(RecordingActive) {
            sendCountStepTotal++;
            sendBytesCountStepTotal+=msgBytes;
        }

        sendCountInterval++;
        publishedMsgBytesInterval += msgBytes;
        publishedMsgHeadersInterval += msgHeaders;
        deliveryModeInterval += deliveryMode;
        routingKeyLengthInterval += routingKeyLength;

        report();
    }

    public synchronized void handleReturn() {
        returnCountInterval++;
        report();
    }

    public synchronized void handleConfirm(int numConfirms, long[] latencies) {
        updateConfirmMultipleFlag.accept((long)numConfirms);
        confirmCountInterval += numConfirms;
        for (long latency : latencies) {
            this.confirmLatencies.update(latency);
            this.updateConfirmLatency.accept(latency);
        }
        report();
    }

    public synchronized void handleNack(int numAcks) {
        nackCountInterval += numAcks;
        report();
    }

    public synchronized void handleRecv(long latency, long msgBytes, int msgHeaders, int prefetch, int ackInterval) {
        if(RecordingActive) {
            this.recvCountStepTotal++;
            this.latencies.update(latency);
        }

        recvCountInterval++;

        recvBytesCountStepTotal += msgBytes;
        consumedMsgBytesInterval += msgBytes;
        consumedMsgHeadersInterval += msgHeaders;
        consumerPrefetchInterval += prefetch;
        consumerAckInterval += ackInterval;

        if (latency > 0) {
            this.updateLatency.accept(latency);
        }
        report();
    }

    public synchronized void handleConnectionError() {
        consumerConnectionErrorInterval++;
        report();
    }

    public synchronized void handleBlockedConnection() {
        blockedPublisherConnectionInterval++;
        report();
    }

    public synchronized void handleUnblockedConnection() {
        unblockedPublisherConnectionInterval++;
        report();
    }

    protected void recordPublisherCount(double publisherCount) {
        this.publisherCount.accumulate(publisherCount);
    }

    protected void recordConsumerCount(double consumerCount) {
        this.consumerCount.accumulate(consumerCount);
    }

    protected void recordQueueCount(double queueCount) {
        this.queueCount.accumulate(queueCount);
    }

    protected void recordPublished(double rate) {
        this.published.accumulate(rate);

        if(RecordingActive)
            this.sendRates.update((int)rate);
    }

    protected void recordPerPublisherPublished(List<Long> rates) {
        Histogram perPublisherRates = new MetricRegistry().histogram("perPublisherRates");
        for(Long rate : rates)
            perPublisherRates.update(rate);

        Snapshot ss = perPublisherRates.getSnapshot();
        perPublisherRateMin.accumulate(ss.getMin());
        perPublisherRate5.accumulate(ss.getValue(0.05));
        perPublisherRate25.accumulate(ss.getValue(0.25));
        perPublisherRate50.accumulate(ss.getValue(0.50));
        perPublisherRate75.accumulate(ss.getValue(0.75));
        perPublisherRate95.accumulate(ss.getValue(0.95));
        perPublisherRateMax.accumulate(ss.getMax());
    }

    protected void recordPerConsumerReceived(List<Long> rates) {
        Histogram perConsumerRates = new MetricRegistry().histogram("perConsumerRates");
        for(Long rate : rates)
            perConsumerRates.update(rate);

        Snapshot ss = perConsumerRates.getSnapshot();
        perConsumerRateMin.accumulate(ss.getMin());
        perConsumerRate5.accumulate(ss.getValue(0.05));
        perConsumerRate25.accumulate(ss.getValue(0.25));
        perConsumerRate50.accumulate(ss.getValue(0.50));
        perConsumerRate75.accumulate(ss.getValue(0.75));
        perConsumerRate95.accumulate(ss.getValue(0.95));
        perConsumerRateMax.accumulate(ss.getMax());
    }

    protected void recordPublishedBytes(double rate) {
        this.publishedMsgBytes.accumulate(rate);
    }

    protected void recordPublishedMsgSize(double messageBytes) {
        this.publishedMsgSize.accumulate(messageBytes);
    }

    protected void recordPublishedMsgHeaders(double messageHeaders) {
        this.publishedMsgHeaders.accumulate(messageHeaders);
    }

    protected void recordInFlightLimit(double inFlightLimit) {
        this.publisherInFlightLimit.accumulate(inFlightLimit);
    }

    protected void recordRoutingKeyLength(double routingKeyLength) {
        this.routingKeyLength.accumulate(routingKeyLength);
    }

    protected void recordTargetPublishRate(double publishRate) {
        this.targetPublishRate.accumulate(publishRate);
    }

    protected void recordDeliveryMode(double deliveryMode) {
        this.deliveryMode.accumulate(deliveryMode);
    }

    protected void recordReturned(double rate) {
        this.returned.accumulate(rate);
    }

    protected void recordConfirmed(double rate) {
        this.confirmed.accumulate(rate);
    }

    protected void recordNacked(double rate) {
        this.nacked.accumulate(rate);
    }

    protected void recordReceived(double rate) {
        this.consumed.accumulate(rate);

        if(RecordingActive)
            this.receiveRates.update((int)rate);
    }

    protected void recordReceivedBytes(double rate) {
        this.consumedMsgBytes.accumulate(rate);
    }

    protected void recordReceivedMsgSize(double messageBytes) {
        this.consumedMsgSize.accumulate(messageBytes);
    }

    protected void recordReceivedMsgHeaders(double messageHeaders) {
        this.consumedMsgHeaders.accumulate(messageHeaders);
    }

    protected void recordConsumerPrefetch(double prefetch) {
        this.consumerPrefetch.accumulate(prefetch);
    }

    protected void recordConsumerAck(double ackInterval) {
        this.consumerAck.accumulate(ackInterval);
    }

    protected void recordConsumerConnError(double connectionErrors) {
        this.consumerConnectionError.accumulate(connectionErrors);
    }

    protected void recordPublisherBlocked(double blockedRate) {
        this.blockedPublisherConnectionRate.accumulate(blockedRate);
    }

    protected void recordPublisherUnblocked(double unblockedRate) {
        this.unblockedPublisherConnectionRate.accumulate(unblockedRate);
    }

    public StepStatistics getStepStatistics(int stepDurationSeconds) {
//        StepStatistics stepStats = new StepStatistics();
//
//        int recordingSeconds = (int)Duration.between(recordStart, recordStop).getSeconds();
//        stepStats.setDurationSeconds(stepDurationSeconds);
//        stepStats.setRecordingSeconds(recordingSeconds);
//        stepStats.setLatencyPercentiles(new String[] {"Min", "50th", "75th", "95th", "99th", "99.9th", "Max"});
//        stepStats.setLatencies(getLatencyHistogramValues(latencies));
//        stepStats.setConfirmLatencies(getLatencyHistogramValues(confirmLatencies));
//        stepStats.setThroughPutPercentiles(new String[] {"Min", "Avg", "Median", "StdDev", "Max"});
//        stepStats.setSendRates(getThroughputHistogramValues(sendRates));
//        stepStats.setReceiveRates(getThroughputHistogramValues(receiveRates));
//        stepStats.setSentCount(sendCountStepTotal);
//        stepStats.setSentBytesCount(sendBytesCountStepTotal);
//        stepStats.setReceivedCount(recvCountStepTotal);
//        stepStats.setReceivedBytesCount(recvBytesCountStepTotal);
//        stepStats.setFairnessPercentiles(new String[] {"Min", "5th", "25th", "50th", "75th", "95th", "Max"});
//        stepStats.setPerPublisherSendRates(getPerPublisherSendRates(recordingSeconds));
//        stepStats.setPerConsumerReceiveRates(getPerConsumerReceiveRates(recordingSeconds));
        StepStatistics stepStats = readCurrentStepStatistics(stepDurationSeconds);

        latencies = new MetricRegistry().histogram("latency");
        confirmLatencies = new MetricRegistry().histogram("confirm-latency");
        sendRates = new MetricRegistry().histogram("send-rates");
        receiveRates = new MetricRegistry().histogram("receive-rates");

        sendCountStepTotal = 0;
        recvCountStepTotal = 0;
        sendBytesCountStepTotal = 0;
        recvBytesCountStepTotal = 0;

        return stepStats;
    }

    public StepStatistics readCurrentStepStatistics(int stepDurationSeconds) {
        StepStatistics stepStats = new StepStatistics();

        int recordingSeconds = 0;
        if(recordStop == null || recordStop.isBefore(recordStart))
            recordingSeconds = (int)Duration.between(recordStart, Instant.now()).toMillis()/1000;
        else
            recordingSeconds = (int)Duration.between(recordStart, recordStop).toMillis()/1000;

        stepStats.setDurationSeconds(stepDurationSeconds);
        stepStats.setRecordingSeconds(recordingSeconds);
        stepStats.setLatencyPercentiles(new String[] {"Min", "50th", "75th", "95th", "99th", "99.9th", "Max"});
        stepStats.setLatencies(getLatencyHistogramValues(latencies));
        stepStats.setConfirmLatencies(getLatencyHistogramValues(confirmLatencies));
        stepStats.setThroughPutPercentiles(new String[] {"Min", "Avg", "Median", "StdDev", "Max"});
        stepStats.setSendRates(getThroughputHistogramValues(sendRates));
        stepStats.setReceiveRates(getThroughputHistogramValues(receiveRates));
        stepStats.setSentCount(sendCountStepTotal);
        stepStats.setSentBytesCount(sendBytesCountStepTotal);
        stepStats.setReceivedCount(recvCountStepTotal);
        stepStats.setReceivedBytesCount(recvBytesCountStepTotal);
        stepStats.setFairnessPercentiles(new String[] {"Min", "5th", "25th", "50th", "75th", "95th", "Max"});
        stepStats.setPerPublisherSendRates(getPerPublisherSendRates(recordingSeconds));
        stepStats.setPerConsumerReceiveRates(getPerConsumerReceiveRates(recordingSeconds));

        return stepStats;
    }

    private double[] getPerPublisherSendRates(int recordingSeconds) {
        if(this.publisherGroups == null)
            return new double[] {0,0,0,0,0,0};

        Histogram perPublisherRates = new MetricRegistry().histogram("perPublisherRates");
        for(PublisherGroup publisherGroup : publisherGroups) {
            List<Long> sendCounts = publisherGroup.getRecordedSendCounts();
            for(Long count : sendCounts) {
                if(count == 0 || recordingSeconds == 0)
                    perPublisherRates.update(0);
                else
                    perPublisherRates.update(count/recordingSeconds);
            }
        }

        return getFairnessHistogramValues(perPublisherRates);
    }

    private double[] getPerConsumerReceiveRates(int recordingSeconds) {
        if(this.consumerGroups == null)
            return new double[] {0,0,0,0,0,0,0};

        Histogram perConsumerRates = new MetricRegistry().histogram("perConsumerRates");
        for(ConsumerGroup consumerGroup : consumerGroups) {
            List<Long> receiveCounts = consumerGroup.getRecordedReceiveCounts();
            for(Long count : receiveCounts) {
                if(count == 0 || recordingSeconds == 0)
                    perConsumerRates.update(0);
                else
                    perConsumerRates.update(count/recordingSeconds);
            }
        }

        return getFairnessHistogramValues(perConsumerRates);
    }

    private double[] getLatencyHistogramValues(Histogram histogram) {
        Snapshot ss = histogram.getSnapshot();

        return new double[]{
                ((double)ss.getMin())/1000000,
                ss.getMedian()/1000000,
                ss.get75thPercentile()/1000000,
                ss.get95thPercentile()/1000000,
                ss.get99thPercentile()/1000000,
                ss.get999thPercentile()/1000000,
                ((double)ss.getMax())/1000000
        };
    }

    private double[] getThroughputHistogramValues(Histogram histogram) {
        Snapshot ss = histogram.getSnapshot();

        return new double[]{
                ss.getMin(),
                ss.getMean(),
                ss.getMedian(),
                ss.getStdDev(),
                ss.getMax()
        };
    }

    private double[] getFairnessHistogramValues(Histogram histogram) {
        Snapshot ss = histogram.getSnapshot();

        return new double[]{
                ss.getMin(),
                ss.getValue(0.05),
                ss.getValue(0.25),
                ss.getValue(0.50),
                ss.getValue(0.75),
                ss.getValue(0.95),
                ss.getMax()
        };
    }


}
