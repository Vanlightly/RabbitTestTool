package com.jackvanlightly.rabbittesttool.statistics;

import java.util.ArrayList;
import java.util.List;

public class MetricListCounter {
    MetricType metricType;
    volatile List<Long> recordedValue;
    volatile List<Long> realValue;

    public MetricListCounter(MetricType metricType) {
        this.metricType = metricType;
        this.recordedValue = new ArrayList<>();
        this.realValue = new ArrayList<>();
    }

    public MetricType getMetricType() {
        return metricType;
    }

    public void add(long[] value) {
        if(Stats.RecordingActive) {
            for(long item : value)
                recordedValue.add(item);
        }

        for(long item : value)
            realValue.add(item);
    }

    public void add(long value) {
        if(Stats.RecordingActive) {
            recordedValue.add(value);
        }

        realValue.add(value);
    }

    public List<Long> getRecordedValue() {
        List<Long> values = new ArrayList<>(recordedValue);
        recordedValue = new ArrayList<>();
        return values;
    }

    public List<Long> getRealValue() {
        List<Long> values = new ArrayList<>(realValue);
        realValue = new ArrayList<>();
        return values;
    }
}
