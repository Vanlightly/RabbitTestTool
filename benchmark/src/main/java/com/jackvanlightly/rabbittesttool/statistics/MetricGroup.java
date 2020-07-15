package com.jackvanlightly.rabbittesttool.statistics;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricGroup {
    Map<MetricType, MetricCounter> scalarMetrics;
    Map<MetricType, MetricListCounter> listMetrics;

    public MetricGroup(Map<MetricType, MetricCounter> metricGroup,
                       Map<MetricType, MetricListCounter> listMetrics) {
        this.scalarMetrics = metricGroup;
        this.listMetrics = listMetrics;
    }

    public static MetricGroup createAmqpPublisherMetricGroup() {
        Map<MetricType, MetricCounter> scalarMetrics = new HashMap<>();
        scalarMetrics.put(MetricType.PublisherBlockedConnection, new MetricCounter(MetricType.PublisherBlockedConnection));
        scalarMetrics.put(MetricType.PublisherConfirm, new MetricCounter(MetricType.PublisherConfirm));
        scalarMetrics.put(MetricType.PublisherDeliveryMode, new MetricCounter(MetricType.PublisherDeliveryMode));
        scalarMetrics.put(MetricType.PublisherSentHeaderCount, new MetricCounter(MetricType.PublisherSentHeaderCount));
        scalarMetrics.put(MetricType.PublisherNacked, new MetricCounter(MetricType.PublisherNacked));
        scalarMetrics.put(MetricType.PublisherReturned, new MetricCounter(MetricType.PublisherReturned));
        scalarMetrics.put(MetricType.PublisherRoutingKeyLength, new MetricCounter(MetricType.PublisherRoutingKeyLength));
        scalarMetrics.put(MetricType.PublisherSentMessage, new MetricCounter(MetricType.PublisherSentMessage));
        scalarMetrics.put(MetricType.PublisherSentBytes, new MetricCounter(MetricType.PublisherSentBytes));

        Map<MetricType, MetricListCounter> listMetrics = new HashMap<>();
        listMetrics.put(MetricType.PublisherConfirmLatencies, new MetricListCounter(MetricType.PublisherConfirmLatencies));

        return new MetricGroup(scalarMetrics, listMetrics);
    }

    public static MetricGroup createStreamPublisherMetricGroup() {
        Map<MetricType, MetricCounter> scalarMetrics = new HashMap<>();
        scalarMetrics.put(MetricType.PublisherConfirm, new MetricCounter(MetricType.PublisherConfirm));
        scalarMetrics.put(MetricType.PublisherNacked, new MetricCounter(MetricType.PublisherNacked));
        scalarMetrics.put(MetricType.PublisherSentMessage, new MetricCounter(MetricType.PublisherSentMessage));
        scalarMetrics.put(MetricType.PublisherSentBytes, new MetricCounter(MetricType.PublisherSentBytes));

        Map<MetricType, MetricListCounter> listMetrics = new HashMap<>();
        listMetrics.put(MetricType.PublisherConfirmLatencies, new MetricListCounter(MetricType.PublisherConfirmLatencies));

        return new MetricGroup(scalarMetrics, listMetrics);
    }

    public static MetricGroup createAmqpConsumerMetricGroup() {
        Map<MetricType, MetricCounter> scalarMetrics = new HashMap<>();
        scalarMetrics.put(MetricType.ConsumerAckedMessages, new MetricCounter(MetricType.ConsumerAckedMessages));
        scalarMetrics.put(MetricType.ConsumerAckInterval, new MetricCounter(MetricType.ConsumerAckInterval));
        scalarMetrics.put(MetricType.ConsumerAckIntervalMs, new MetricCounter(MetricType.ConsumerAckIntervalMs));
        scalarMetrics.put(MetricType.ConsumerAcks, new MetricCounter(MetricType.ConsumerAcks));
        scalarMetrics.put(MetricType.ConsumerConnectionErrors, new MetricCounter(MetricType.ConsumerConnectionErrors));
        scalarMetrics.put(MetricType.ConsumerPrefetch, new MetricCounter(MetricType.ConsumerPrefetch));
        scalarMetrics.put(MetricType.ConsumerReceivedBytes, new MetricCounter(MetricType.ConsumerReceivedBytes));
        scalarMetrics.put(MetricType.ConsumerReceivedHeaderCount, new MetricCounter(MetricType.ConsumerReceivedHeaderCount));
        scalarMetrics.put(MetricType.ConsumerReceivedMessage, new MetricCounter(MetricType.ConsumerReceivedMessage));

        Map<MetricType, MetricListCounter> listMetrics = new HashMap<>();
        listMetrics.put(MetricType.ConsumerLatencies, new MetricListCounter(MetricType.ConsumerLatencies));

        return new MetricGroup(scalarMetrics, listMetrics);
    }

    public static MetricGroup createStreamConsumerMetricGroup() {
        Map<MetricType, MetricCounter> scalarMetrics = new HashMap<>();
        scalarMetrics.put(MetricType.ConsumerAckedMessages, new MetricCounter(MetricType.ConsumerAckedMessages));
        scalarMetrics.put(MetricType.ConsumerAckInterval, new MetricCounter(MetricType.ConsumerAckInterval));
        scalarMetrics.put(MetricType.ConsumerAckIntervalMs, new MetricCounter(MetricType.ConsumerAckIntervalMs));
        scalarMetrics.put(MetricType.ConsumerAcks, new MetricCounter(MetricType.ConsumerAcks));
        scalarMetrics.put(MetricType.ConsumerConnectionErrors, new MetricCounter(MetricType.ConsumerConnectionErrors));
        scalarMetrics.put(MetricType.ConsumerPrefetch, new MetricCounter(MetricType.ConsumerPrefetch));
        scalarMetrics.put(MetricType.ConsumerReceivedBytes, new MetricCounter(MetricType.ConsumerReceivedBytes));
        scalarMetrics.put(MetricType.ConsumerReceivedMessage, new MetricCounter(MetricType.ConsumerReceivedMessage));
        scalarMetrics.put(MetricType.ConsumerStreamBatches, new MetricCounter(MetricType.ConsumerStreamBatches));
        scalarMetrics.put(MetricType.ConsumerStreamMessages, new MetricCounter(MetricType.ConsumerStreamMessages));

        Map<MetricType, MetricListCounter> listMetrics = new HashMap<>();
        listMetrics.put(MetricType.ConsumerLatencies, new MetricListCounter(MetricType.ConsumerLatencies));

        return new MetricGroup(scalarMetrics, listMetrics);
    }

    public Collection<MetricCounter> getScalarMetrics() {
        return scalarMetrics.values();
    }

    public Collection<MetricListCounter> getListMetrics() {
        return listMetrics.values();
    }

    public void set(MetricType metric, long value) {
        scalarMetrics.get(metric).set(value);
    }

    public void add(MetricType metric, long value) {
        listMetrics.get(metric).add(value);
    }

    public void add(MetricType metric, long[] value) {
        listMetrics.get(metric).add(value);
    }

    public void increment(MetricType metric) {
        scalarMetrics.get(metric).increment();
    }

    public void increment(MetricType metric, long value) {
        scalarMetrics.get(metric).increment(value);
    }

    public long getRealScalarValue(MetricType metric) {
        return scalarMetrics.get(metric).getRealValue();
    }

    public long getRecordedScalarValue(MetricType metric) {
        return scalarMetrics.get(metric).getRecordedValue();
    }

    public List<Long> getRealListValue(MetricType metric) {
        return listMetrics.get(metric).getRealValue();
    }

    public List<Long> getRecordedListValue(MetricType metric) {
        return listMetrics.get(metric).getRecordedValue();
    }

    public long getRealDeltaScalarValue(MetricType metric) {
        return scalarMetrics.get(metric).getRealDeltaValue();
    }

    public long getRecordedDeltaScalarValue(MetricType metric) {
        return scalarMetrics.get(metric).getRecordedDeltaValue();
    }

    public long getRealDeltaScalarValueForStepStats(MetricType metric) {
        return scalarMetrics.get(metric).getRealDeltaValueForStepStats();
    }

    public long getRecordedDeltaScalarValueForStepStats(MetricType metric) {
        return scalarMetrics.get(metric).getRecordedDeltaValueForStepStats();
    }
}
