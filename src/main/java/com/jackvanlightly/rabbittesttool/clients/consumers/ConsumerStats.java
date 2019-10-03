package com.jackvanlightly.rabbittesttool.clients.consumers;

import com.jackvanlightly.rabbittesttool.statistics.RecordedCounter;

public class ConsumerStats {
    private RecordedCounter recordedReceived;

    public ConsumerStats() {
        recordedReceived = new RecordedCounter();
    }

    public void incrementReceivedCount() {
        recordedReceived.increment();
    }

    public long getAndResetRecordedReceived() {
        return recordedReceived.getRecordedValueAndReset();
    }

    public long getAndResetRealReceived() {
        return recordedReceived.getRealValueAndReset();
    }
}
