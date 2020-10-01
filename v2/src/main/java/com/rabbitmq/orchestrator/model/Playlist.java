package com.rabbitmq.orchestrator.model;

import com.rabbitmq.orchestrator.deploy.BaseSystem;

import java.util.List;

public class Playlist {
    List<BaseSystem> systems;
    List<Benchmark> benchmarks;

    public Playlist(List<BaseSystem> systems, List<Benchmark> benchmarks) {
        this.systems = systems;
        this.benchmarks = benchmarks;
    }

    public List<BaseSystem> getSystems() {
        return systems;
    }

    public List<Benchmark> getBenchmarks() {
        return benchmarks;
    }
}
