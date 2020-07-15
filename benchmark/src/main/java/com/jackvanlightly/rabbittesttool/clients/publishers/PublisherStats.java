package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.jackvanlightly.rabbittesttool.statistics.RecordedCounter;

public class PublisherStats {

    private RecordedCounter recordedSent;

    public PublisherStats() {
        recordedSent = new RecordedCounter();
    }

    public void incrementSendCount() {
        recordedSent.increment();
    }

    public void incrementSendCount(int count) {
        recordedSent.increment(count);
    }

    public long getAndResetRecordedSent() {
        return recordedSent.getRecordedValueAndReset();
    }

    public long getAndResetRealSent() {
        return recordedSent.getRealValueAndReset();
    }
}
