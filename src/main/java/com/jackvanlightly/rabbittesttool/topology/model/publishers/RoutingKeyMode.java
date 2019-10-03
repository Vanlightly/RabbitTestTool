package com.jackvanlightly.rabbittesttool.topology.model.publishers;

public enum RoutingKeyMode {
    None,
    FixedValue,
    MultiValue,
    Random,
    StreamKey,
    RoutingKeyIndex
}
