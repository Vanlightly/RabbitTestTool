package com.jackvanlightly.rabbittesttool.statistics;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.jackvanlightly.rabbittesttool.BrokerConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;

public class PublisherGroupStats implements GroupsStats {

    Stats globalStats;

    public final String groupPrefix;

    private final Consumer<Long> updateConfirmLatency;
    private final Consumer<Long> updateConfirmMultipleFlag;
    private final DoubleAccumulator published, returned, confirmed, nacked;
    private final DoubleAccumulator publishedMsgBytes, publishedMsgSize, publishedMsgHeaders;
    private final DoubleAccumulator deliveryMode, publisherInFlightLimit;
    private final DoubleAccumulator publisherCount, targetPublishRate;
    private final DoubleAccumulator blockedPublisherConnectionRate, unblockedPublisherConnectionRate, routingKeyLength;
//    private final DoubleAccumulator perPublisherRateMin, perPublisherRate5, perPublisherRate25, perPublisherRate50, perPublisherRate75, perPublisherRate95, perPublisherRateMax;

    protected long lastStatsTime;
    protected long sendCountInterval;
    protected long returnCountInterval;
    protected long confirmCountInterval;
    protected long nackCountInterval;
    protected long publishedMsgBytesInterval;
    protected long publishedMsgHeadersInterval;
    protected long consumedMsgHeadersInterval;
    protected long deliveryModeInterval;
    protected long currentPublisherCount;
    protected long targetPublishRateCount;
    protected long publisherInFlightLimitCount;
    protected long blockedPublisherConnectionInterval;
    protected long unblockedPublisherConnectionInterval;
    protected long routingKeyLengthInterval;

    protected long sendCountStepTotal;
    protected long sendBytesCountStepTotal;

    protected long elapsedInterval;
    protected long elapsedTotal;

    protected Histogram confirmLatencies = new MetricRegistry().histogram("confirm-latency");
    protected Histogram sendRates = new MetricRegistry().histogram("send-rates");

    public PublisherGroupStats(Stats globalStats,
                               String groupPrefix) {
        this.groupPrefix = groupPrefix;
        this.globalStats = globalStats;

        List<Tag> tags = getTags(globalStats.getBrokerConfig(), groupPrefix);

        MeterRegistry registry = globalStats.getRegistry();
        Timer confirmLatencyTimer = Timer
                .builder(globalStats.getMetricsPrefix() + "confirm-latency")
                .description("confirm latency")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
                .distributionStatisticExpiry(Duration.ofMillis(globalStats.getSamplingIntervalMs()))
                .tags(tags)
                .register(registry);

        Timer confirmMultipleFlagTimer = Timer
                .builder(globalStats.getMetricsPrefix() + "confirm-multiple-flag")
                .description("confirm multiple flag usage")
                .publishPercentiles(0.5, 0.75, 0.95, 0.99, 0.999)
                .distributionStatisticExpiry(Duration.ofMillis(globalStats.getSamplingIntervalMs()))
                .tags(tags)
                .register(registry);

        DoubleBinaryOperator accumulatorFunction = (x, y) -> y;

        published = registry.gauge(globalStats.getMetricsPrefix() + "published", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        returned = registry.gauge(globalStats.getMetricsPrefix() + "returned", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        confirmed = registry.gauge(globalStats.getMetricsPrefix() + "confirmed", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        nacked = registry.gauge(globalStats.getMetricsPrefix() + "nacked", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        publishedMsgBytes = registry.gauge(globalStats.getMetricsPrefix() + "published-msg-bytes", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        publishedMsgSize = registry.gauge(globalStats.getMetricsPrefix() + "published-msg-size", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        publishedMsgHeaders = registry.gauge(globalStats.getMetricsPrefix() + "published-msg-headers", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        deliveryMode = registry.gauge(globalStats.getMetricsPrefix() + "publish-msg-del-mode", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        publisherInFlightLimit = registry.gauge(globalStats.getMetricsPrefix() + "publisher-in-flight-limit", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        publisherCount = registry.gauge(globalStats.getMetricsPrefix() + "publisher-count", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        targetPublishRate = registry.gauge(globalStats.getMetricsPrefix() + "target-publish-rate", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        blockedPublisherConnectionRate = registry.gauge(globalStats.getMetricsPrefix() + "publisher-blocked", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        unblockedPublisherConnectionRate = registry.gauge(globalStats.getMetricsPrefix() + "publisher-unblocked", tags, new DoubleAccumulator(accumulatorFunction, 0.0));
        routingKeyLength = registry.gauge(globalStats.getMetricsPrefix() + "routingkey-length", tags, new DoubleAccumulator(accumulatorFunction, 0.0));

//        perPublisherRateMin = registry.gauge(metricsPrefix + "per-publisher-rate", getTags(tags, "phi", "min"), new DoubleAccumulator(accumulatorFunction, 0.0));
//        perPublisherRate5 = registry.gauge(metricsPrefix + "per-publisher-rate", getTags(tags, "phi", "5"), new DoubleAccumulator(accumulatorFunction, 0.0));
//        perPublisherRate25 = registry.gauge(metricsPrefix + "per-publisher-rate", getTags(tags, "phi", "25"), new DoubleAccumulator(accumulatorFunction, 0.0));
//        perPublisherRate50 = registry.gauge(metricsPrefix + "per-publisher-rate", getTags(tags, "phi", "50"), new DoubleAccumulator(accumulatorFunction, 0.0));
//        perPublisherRate75 = registry.gauge(metricsPrefix + "per-publisher-rate", getTags(tags, "phi", "75"), new DoubleAccumulator(accumulatorFunction, 0.0));
//        perPublisherRate95 = registry.gauge(metricsPrefix + "per-publisher-rate", getTags(tags, "phi", "95"), new DoubleAccumulator(accumulatorFunction, 0.0));
//        perPublisherRateMax = registry.gauge(metricsPrefix + "per-publisher-rate", getTags(tags, "phi", "max"), new DoubleAccumulator(accumulatorFunction, 0.0));

        updateConfirmLatency = latency -> confirmLatencyTimer.record(latency, TimeUnit.NANOSECONDS);
        updateConfirmMultipleFlag = confirms -> confirmMultipleFlagTimer.record(confirms, TimeUnit.MILLISECONDS); // a hack

        reset(globalStats.getStartTime());
    }

    private double rate(long count, long elapsed) {
        return 1000.0 * count / elapsed;
    }

    private void recordStats(long now) {
        recordPublisherCount(currentPublisherCount);
        recordInFlightLimit(publisherInFlightLimitCount);
        recordTargetPublishRate(targetPublishRateCount);

        double ratePublished = rate(sendCountInterval, elapsedInterval);
        recordPublished(ratePublished);

        double ratePublishedBytes = rate(publishedMsgBytesInterval, elapsedInterval);
        recordPublishedBytes(ratePublishedBytes);

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

//            List<Long> allSendCounts = new ArrayList<>();
//            for(PublisherGroup publisherGroup : publisherGroups) {
//                List<Long> sendCounts = publisherGroup.getRealSendCounts();
//                for(Long count : sendCounts) {
//                    allSendCounts.add((long)rate(count, elapsedInterval));
//                }
//            }
//            recordPerPublisherPublished(allSendCounts);
        }

        double rateReturned = rate(returnCountInterval, elapsedInterval);
        recordReturned(rateReturned);

        double rateConfirmed = rate(confirmCountInterval, elapsedInterval);
        recordConfirmed(rateConfirmed);

        double rateNacked = rate(nackCountInterval, elapsedInterval);
        recordNacked(rateNacked);
    }


//    public void addClientGroups(List<PublisherGroup> publisherGroups, List<ConsumerGroup> consumerGroups) {
//        this.publisherGroups = publisherGroups;
//        this.consumerGroups = consumerGroups;
//    }

    private List<Tag> getTags(BrokerConfiguration brokerConfig, String groupPrefix) {
        String node = brokerConfig.getNodeNames().stream().min(Comparator.comparing(String::valueOf)).get();
        return new ArrayList<>(Arrays.asList(
                Tag.of("technology", brokerConfig.getTechnology()),
                Tag.of("node", node),
                Tag.of("group", groupPrefix)
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
        publishedMsgBytesInterval = 0;
        publishedMsgHeadersInterval = 0;
        consumedMsgHeadersInterval = 0;
        deliveryModeInterval = 0;
        blockedPublisherConnectionInterval = 0;
        unblockedPublisherConnectionInterval = 0;
        routingKeyLengthInterval = 0;
    }

    public void recordStats() {
        long now = System.currentTimeMillis();
        recordStats(now);
        reset(now);
    }

    public void setPublisherCount(int publisherCount) {
        currentPublisherCount = publisherCount;
    }

    public void setTargetPublishRate(int targetRate) {
        targetPublishRateCount = targetRate;
    }

    public void setPublisherInFlightLimit(int inFlightLimit) {
        publisherInFlightLimitCount = inFlightLimit;
    }

    public void handleSend(long msgBytes,
                            int msgHeaders,
                            int deliveryMode,
                            int routingKeyLength) {
        if(Stats.RecordingActive) {
            sendCountStepTotal++;
            sendBytesCountStepTotal+=msgBytes;
        }

        sendCountInterval++;
        publishedMsgBytesInterval += msgBytes;
        publishedMsgHeadersInterval += msgHeaders;
        deliveryModeInterval += deliveryMode;
        routingKeyLengthInterval += routingKeyLength;

        globalStats.handleSend(msgBytes, msgHeaders, deliveryMode, routingKeyLength);
    }

    public void handleReturn() {
        returnCountInterval++;
        globalStats.handleReturn();
    }

    public void handleConfirm(int numConfirms, long[] latencies) {
        updateConfirmMultipleFlag.accept((long)numConfirms);
        confirmCountInterval += numConfirms;
        for (long latency : latencies) {
            this.confirmLatencies.update(latency);
            this.updateConfirmLatency.accept(latency);
        }

        globalStats.handleConfirm(numConfirms, latencies);
    }

    public void handleNack(int numAcks) {
        nackCountInterval += numAcks;

        globalStats.handleNack(numAcks);
    }

    public void handleBlockedConnection() {
        blockedPublisherConnectionInterval++;
        globalStats.handleBlockedConnection();
    }

    public void handleUnblockedConnection() {
        unblockedPublisherConnectionInterval++;
        globalStats.handleUnblockedConnection();
    }

    protected void recordPublisherCount(double publisherCount) {
        this.publisherCount.accumulate(publisherCount);
    }

    protected void recordPublished(double rate) {
        this.published.accumulate(rate);

        if(Stats.RecordingActive)
            this.sendRates.update((int)rate);
    }

//    protected void recordPerPublisherPublished(List<Long> rates) {
//        Histogram perPublisherRates = new MetricRegistry().histogram("perPublisherRates");
//        for(Long rate : rates)
//            perPublisherRates.update(rate);
//
//        Snapshot ss = perPublisherRates.getSnapshot();
//        perPublisherRateMin.accumulate(ss.getMin());
//        perPublisherRate5.accumulate(ss.getValue(0.05));
//        perPublisherRate25.accumulate(ss.getValue(0.25));
//        perPublisherRate50.accumulate(ss.getValue(0.50));
//        perPublisherRate75.accumulate(ss.getValue(0.75));
//        perPublisherRate95.accumulate(ss.getValue(0.95));
//        perPublisherRateMax.accumulate(ss.getMax());
//    }

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

    protected void recordPublisherBlocked(double blockedRate) {
        this.blockedPublisherConnectionRate.accumulate(blockedRate);
    }

    protected void recordPublisherUnblocked(double unblockedRate) {
        this.unblockedPublisherConnectionRate.accumulate(unblockedRate);
    }
}
