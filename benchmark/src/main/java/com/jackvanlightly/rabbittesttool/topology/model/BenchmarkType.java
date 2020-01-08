package com.jackvanlightly.rabbittesttool.topology.model;

public enum BenchmarkType {
    Throughput, // sends messages as fast as it can
    Latency, // sends messages at a specific rate
    Stress // tries to overload the broker
}
