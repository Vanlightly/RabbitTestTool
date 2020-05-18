package com.jackvanlightly.rabbittesttool.clients;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

// TODO
public class FlowController extends Semaphore {
    public FlowController(int inflightLimit) {
        super(inflightLimit);
    }

    public void getSendPermit() throws InterruptedException {
        this.acquire();
    }

    public boolean tryGetSendPermit(long l, TimeUnit timeUnit) throws InterruptedException {
        return this.tryAcquire(l, timeUnit);
    }

    public void returnSendPermits(int count) {
        this.release(count);
    }

    public void increaseInflightLimit(int amount) {
        this.release(amount);
    }

    public void decreaseInflightLimit(int amount) {
        this.reducePermits(-amount);
    }
}
