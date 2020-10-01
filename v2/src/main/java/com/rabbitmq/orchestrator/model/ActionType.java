package com.rabbitmq.orchestrator.model;

public enum ActionType {
    NONE,
    TRAFFIC_CONTROL,
    RESTART_CLUSTER,
    RESTART_BROKER,
    STOP_BROKER
}
