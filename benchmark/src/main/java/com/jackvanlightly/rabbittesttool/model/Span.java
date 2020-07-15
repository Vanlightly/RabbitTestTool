package com.jackvanlightly.rabbittesttool.model;

import java.time.Instant;
import java.util.Objects;

public class Span implements Comparable<Span> {
    int sequence;
    long low;
    long high;
    Instant created;
    Instant updated;

    public Span(int sequence, long low, long high) {
        this.sequence = sequence;
        this.low = low;
        this.high = high;
        this.created = Instant.now();
        this.updated = Instant.now();
    }

    public Span(Span span) {
        this.sequence = span.getSequence();
        this.low = span.getLow();
        this.high = span.getHigh();
        this.created = span.getCreated();
        this.updated = span.getUpdated();
    }

    public boolean isInsideSpan(int sequence, long number) {
        return this.sequence == sequence && number >= low && number <= high;
    }

    public boolean isHigherThan(int sequence, long number) {
        return this.sequence == sequence && low > number;
    }

    public boolean isLowerThan(int sequence, long number) {
        return this.sequence == sequence && high < number;
    }

    public boolean isAdjacent(int sequence, long number) {
        return this.sequence == sequence && (high == number - 1 || low == number + 1);
    }

    public boolean isAdjacentRight(Span span) {
        return isAdjacent(span.getSequence(), span.getLow());
    }

    public boolean isAdjacentLeft(Span span) {
        return isAdjacent(span.getSequence(), span.getHigh());
    }

    public void include(int sequence, long number) {
        if(this.sequence != sequence)
            throw new RuntimeException("Sequence mismatch. Span sequence is: " + this.sequence + " but received sequence: " + sequence);

        if(number > high) {
            high = number;
        }
        else if(number < low) {
            low = number;
        }
    }

    public void include(int sequence, long number, Instant now) {
        if(this.sequence != sequence)
            throw new RuntimeException("Sequence mismatch. Span sequence is: " + this.sequence + " but received sequence: " + sequence);

        if(number > high) {
            high = number;
            update(now);
        }
        else if(number < low) {
            low = number;
            update(now);
        }
    }

    private void update(Instant now) {
        if(this.updated.isBefore(now)) {
            this.updated = now;
        }
    }

    public int getSequence() {
        return sequence;
    }

    public long getLow() {
        return low;
    }

    public long getHigh() {
        return high;
    }

    public long size() {
        return high-low+1;
    }

    public Instant getCreated() {
        return created;
    }

    public Instant getUpdated() {
        return updated;
    }

    public void setUpdated(Instant updated) {
        this.updated = updated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Span span = (Span) o;
        return getSequence() == span.getSequence() &&
                getLow() == span.getLow() &&
                getHigh() == span.getHigh();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getSequence(), getLow(), getHigh());
    }

    @Override
    public int compareTo(Span o) {
        return Long.compare(this.low, o.getLow());
    }
}
