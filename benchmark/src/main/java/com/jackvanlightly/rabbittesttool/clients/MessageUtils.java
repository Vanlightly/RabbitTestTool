package com.jackvanlightly.rabbittesttool.clients;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MessageUtils {
    private static Lock lock = new ReentrantLock();
    private static Map<Integer, AtomicLong> StreamCounter = new HashMap<>();
    private static int currentStream = 1;

    public static long getTimestamp() {
        return System.nanoTime();
    }

    public static long getLag(long timestamp) {
        return System.nanoTime() - timestamp;
    }

    public static long getLag(long now, long timestamp) {
        return now - timestamp;
    }

    public static long getDifference(long timestamp1, long timestamp2) {

        return timestamp2 - timestamp1;
    }

    public static int addStream() {
        lock.lock();
        try {
            currentStream++;
            StreamCounter.put(currentStream, new AtomicLong());
            return currentStream;
        }
        finally {
            lock.unlock();
        }
    }

    public static long nextSequenceNo(int stream) {
        return StreamCounter.get(stream).getAndIncrement();
    }

}
