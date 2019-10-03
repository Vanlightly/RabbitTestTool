package com.jackvanlightly.rabbittesttool.topology.model;

public class CorrelatedPair {
    private Integer value1;
    private Integer value2;

    public CorrelatedPair(Integer value1, Integer value2) {
        this.value1 = value1;
        this.value2 = value2;
    }

    public Integer getValue1() {
        return value1;
    }

    public void setValue1(Integer value1) {
        this.value1 = value1;
    }

    public Integer getValue2() {
        return value2;
    }

    public void setValue2(Integer value2) {
        this.value2 = value2;
    }
}
