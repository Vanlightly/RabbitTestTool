package com.jackvanlightly.rabbittesttool.model;

import java.time.Instant;
import java.util.Objects;

public class Span implements Comparable<Span> {
    int stream;
    long low;
    long high;
    Instant created;
    Instant updated;

    public Span(int stream, long low, long high) {
        this.stream = stream;
        this.low = low;
        this.high = high;
        this.created = Instant.now();
        this.updated = Instant.now();
    }

    public Span(Span span) {
        this.stream = span.getStream();
        this.low = span.getLow();
        this.high = span.getHigh();
        this.created = span.getCreated();
        this.updated = span.getUpdated();
    }

    public boolean isStream(int stream) {
        return this.stream == stream;
    }

    public boolean isInsideSpan(int stream, long number) {
        return this.stream == stream && number >= low && number <= high;
    }

    public boolean isHigherThan(int stream, long number) {
        return this.stream == stream && low > number;
    }

    public boolean isLowerThan(int stream, long number) {
        return this.stream == stream && high < number;
    }

    public boolean isAdjacent(int stream, long number) {
        return this.stream == stream && (high == number - 1 || low == number + 1);
    }

    public boolean isAdjacentRight(Span span) {
        return isAdjacent(span.getStream(), span.getLow());
    }

    public boolean isAdjacentLeft(Span span) {
        return isAdjacent(span.getStream(), span.getHigh());
    }

    public void include(int stream, long number) {
        if(this.stream != stream)
            throw new RuntimeException("Stream mismatch. Span stream is: " + this.stream + " but received stream: " + stream);

        if(number > high) {
            high = number;
        }
        else if(number < low) {
            low = number;
        }
    }

    public void include(int stream, long number, Instant now) {
        if(this.stream != stream)
            throw new RuntimeException("Stream mismatch. Span stream is: " + this.stream + " but received stream: " + stream);

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

    public int getStream() {
        return stream;
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
        return getStream() == span.getStream() &&
                getLow() == span.getLow() &&
                getHigh() == span.getHigh();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStream(), getLow(), getHigh());
    }

    @Override
    public int compareTo(Span o) {
        return Long.compare(this.low, o.getLow());
    }
}
