package com.jackvanlightly.rabbittesttool.model;

public enum ViolationType {
    Ordering,
    RedeliveredOrdering,
    Missing,
    NonRedeliveredDuplicate,
    RedeliveredDuplicate
}
