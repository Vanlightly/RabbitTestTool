package com.rabbitmq.orchestrator.model.clients;

public enum ClientConnectMode {
    RoundRobin,
    Random,
    Leader,
    NonLeader
}
