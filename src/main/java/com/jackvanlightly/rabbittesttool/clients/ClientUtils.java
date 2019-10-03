package com.jackvanlightly.rabbittesttool.clients;

public class ClientUtils {

    public static void waitFor(int ms, Boolean isCancelled) {
        try {
            int waited = 0;
            while(!isCancelled && waited < ms) {
                Thread.sleep(10);
                waited += 10;
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
