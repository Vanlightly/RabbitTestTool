package com.jackvanlightly.rabbittesttool.topology.model;

public enum ExchangeType {
    Direct,
    Fanout,
    Topic,
    Headers,
    ConsistentHash,
    ModulusHash
}
