package com.jackvanlightly.rabbittesttool.statistics;

public class RecordedCounter {
    private long recordedValue;
    private long realValue;

    public RecordedCounter() {}

    private RecordedCounter(long recordedValue, long realValue) {

    }

    public synchronized void increment() {
        if(Stats.RecordingActive)
            recordedValue++;

        realValue++;
    }

    public synchronized void increment(int count) {
        if(Stats.RecordingActive)
            recordedValue+=count;

        realValue+=count;
    }

//    public synchronized RecordedCounter getSnapshotAndReset() {
//        RecordedCounter snapshot = new RecordedCounter(recordedValue, allValue);
//        recordedValue = 0;
//        allValue = 0;
//
//        return snapshot;
//    }

    public synchronized long getRecordedValueAndReset() {
        long copyVal = recordedValue;
        recordedValue = 0;
        return copyVal;
    }

    public synchronized long getRealValueAndReset() {
        long copyVal = realValue;
        realValue = 0;
        return copyVal;
    }
}
