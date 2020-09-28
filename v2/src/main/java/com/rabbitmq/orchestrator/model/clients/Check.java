package com.rabbitmq.orchestrator.model.clients;

public enum Check {
    DataLoss,
    Ordering,
    Duplicates,
    Connectivity,
    ConsumeGaps,
    All
}
