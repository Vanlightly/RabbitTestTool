package com.jackvanlightly.rabbittesttool.clients.publishers;

public class RateLimiter {
    long periodStartNs;
    long secondStartNs;
    int limitInPeriod;
    int limitInSecond;
    int sentInPeriod;
    int sentInSecond;
    int periodNs;
    static int SecondsNs = 1000000000;

    double warmUpModifier;

    public RateLimiter() {
        warmUpModifier = 1.0;
    }

    public void configureRateLimit(int publishRate) {
        this.limitInSecond = Math.max(1, (int)(publishRate * this.warmUpModifier));
        int measurementPeriodMs = 1000 / this.limitInSecond;

        if (measurementPeriodMs >= 10) {
            this.limitInPeriod = 1;
        } else {
            measurementPeriodMs = 10;
            int periodsPerSecond = 1000 / measurementPeriodMs;
            double dRate = (double)this.limitInSecond / (double)periodsPerSecond;
            if(dRate == (double)this.limitInSecond)
                this.limitInPeriod = (int) dRate;
            else
                this.limitInPeriod = ((int)dRate)+1;
        }

        this.periodNs = measurementPeriodMs * 1000000;
    }

    public void start() {
        periodStartNs = System.nanoTime();
        secondStartNs = System.nanoTime();
    }

    public void rateLimit() {
        this.sentInPeriod++;
        this.sentInSecond++;
        long now = System.nanoTime();
        long elapsedNs = now - periodStartNs;
        long elapsedSecNs = now - secondStartNs;
        boolean reachedPeriodLimit = this.sentInPeriod >= this.limitInPeriod;
        boolean reachedSecondLimit = this.sentInSecond >= this.limitInSecond;

        if (reachedPeriodLimit || reachedSecondLimit) {
            long waitNs = reachedSecondLimit ? SecondsNs - elapsedSecNs : this.periodNs - elapsedNs;
            if (waitNs > 0)
                waitFor((int) (waitNs / 990000));

            // may need to adjust for drift over time
            periodStartNs = System.nanoTime();
            this.sentInPeriod = 0;

            if(reachedSecondLimit) {
                this.sentInSecond = 0;
                secondStartNs = periodStartNs;
            }
        } else {
            if (now - periodStartNs > this.periodNs) {
                periodStartNs = now;
                this.sentInPeriod = 0;
            }

            if(now - secondStartNs > SecondsNs) {
                this.sentInSecond = 0;
                secondStartNs = now;
            }
        }
    }

    public int getLimitInSecond() {
        return limitInSecond;
    }

    public void setWarmUpModifier(double warmUpModifier) {
        this.warmUpModifier = warmUpModifier;
    }

    private void waitFor(int milliseconds) {
        try
        {
            Thread.sleep(milliseconds);
        }
        catch(InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }
}
