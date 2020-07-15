package com.jackvanlightly.rabbittesttool.statistics;

public class MetricCounter {
    MetricType metricType;
    volatile long recordedValue;
    volatile long realValue;
    volatile long lastFetchedRecordedValue;
    volatile long lastFetchedRealValue;
    volatile long lastStepStatsRecordedValue;
    volatile long lastStepStatsRealValue;

    public MetricCounter(MetricType metricType) {
        this.metricType = metricType;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    public void increment() {
        if(Stats.RecordingActive)
            recordedValue++;

        realValue++;
    }

    public void increment(long count) {
        if(Stats.RecordingActive)
            recordedValue+=count;

        realValue+=count;
    }

    public void set(long value) {
        if(Stats.RecordingActive)
            recordedValue = value;

        realValue = value;
    }

    public long getRecordedValue() {
        return recordedValue;
    }

    public long getRealValue() {
        return realValue;
    }

    public long getRecordedDeltaValue() {
        long copyVal = recordedValue;
        long delta = copyVal - lastFetchedRecordedValue;
        lastFetchedRecordedValue = copyVal;
        return delta;
    }

    public long getRealDeltaValue() {
        long copyVal = realValue;
        long delta = copyVal - lastFetchedRealValue;
        lastFetchedRealValue = copyVal;
        return delta;
    }

    public long getRecordedDeltaValueForStepStats() {
        long copyVal = recordedValue;
        long delta = copyVal - lastStepStatsRecordedValue;
        lastStepStatsRecordedValue = copyVal;
        return delta;
    }

    public long getRealDeltaValueForStepStats() {
        long copyVal = realValue;
        long delta = copyVal - lastStepStatsRealValue;
        lastStepStatsRealValue = copyVal;
        return delta;
    }
}
