package com.jackvanlightly.rabbittesttool.clients;

import java.util.concurrent.atomic.AtomicBoolean;

public class ClientUtils {

    public static void waitFor(int ms, AtomicBoolean isCancelled) {
        try {
            int waited = 0;
            while(!isCancelled.get() && waited < ms) {
                Thread.sleep(10);
                waited += 10;
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static void waitFor(int ms) {
        try {
            int waited = 0;
            while(waited < ms) {
                Thread.sleep(10);
                waited += 10;
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
