package com.rabbitmq.orchestrator.deploy;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class Waiter {
    public static void waitMs(long milliseconds, AtomicBoolean isCancelled) {
        long start = System.currentTimeMillis();

        while(!isCancelled.get() && (System.currentTimeMillis()-start) < milliseconds) {
            try {
                Thread.sleep(100);
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public static void waitMs(long milliseconds) {
        long start = System.currentTimeMillis();

        while((System.currentTimeMillis()-start) < milliseconds) {
            try {
                Thread.sleep(100);
            } catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public static void awaitTermination(ExecutorService executorService, long timeout, TimeUnit timeUnit) {
        try {
            executorService.awaitTermination(timeout, timeUnit);
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
