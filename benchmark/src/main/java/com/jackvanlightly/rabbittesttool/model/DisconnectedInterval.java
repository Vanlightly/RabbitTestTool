package com.jackvanlightly.rabbittesttool.model;

import java.time.Duration;
import java.time.Instant;

public class DisconnectedInterval {
    String clientId;
    Duration disconnectedPeriod;
    Instant from;
    Instant to;

    public DisconnectedInterval(String clientId,
                                Instant from,
                                Instant to) {
        this.clientId = clientId;
        this.from = from;
        this.to = to;
        this.disconnectedPeriod = Duration.between(from, to);
    }

    public String getClientId() {
        return clientId;
    }

    public Duration getDuration() {
        return disconnectedPeriod;
    }

    public Instant getFrom() {
        return from;
    }

    public Instant getTo() {
        return to;
    }
}
