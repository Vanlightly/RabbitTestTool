package com.rabbitmq.orchestrator.model;

import java.util.List;

public class Playlist {
    List<Benchmark> benchmarks;

    public Playlist(List<Benchmark> benchmarks) {
        this.benchmarks = benchmarks;
    }

    public List<Benchmark> getBenchmarks() {
        return benchmarks;
    }

    public void setBenchmarks(List<Benchmark> benchmarks) {
        this.benchmarks = benchmarks;
    }
}
