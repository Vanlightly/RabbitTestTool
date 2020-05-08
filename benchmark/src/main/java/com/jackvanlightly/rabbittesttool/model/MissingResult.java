package com.jackvanlightly.rabbittesttool.model;

import java.util.ArrayList;
import java.util.List;

public class MissingResult {
    long low;
    long high;
    List<Span> missing;

    public MissingResult() {
        missing = new ArrayList<>();
    }

    public MissingResult(long low, long high, List<Span> missing) {
        this.low = low;
        this.high = high;
        this.missing = missing;
    }

    public long getLow() {
        return low;
    }

    public long getHigh() {
        return high;
    }

    public List<Span> getMissing() {
        return missing;
    }

    public boolean hasMissing() {
        return !missing.isEmpty();
    }
}
