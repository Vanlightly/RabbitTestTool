package com.rabbitmq.orchestrator.model.clients;

import java.util.List;
import java.util.stream.Collectors;

public class ClientConfiguration {
    boolean tcpNoDelay;
    ClientConnectMode publisherConnectMode;
    ClientConnectMode consumerConnectMode;
    int publisherHeartbeatSeconds;
    int consumerHeartbeatSeconds;

    public ClientConfiguration(boolean tcpNoDelay,
                               ClientConnectMode publisherConnectMode,
                               ClientConnectMode consumerConnectMode,
                               int publisherHeartbeatSeconds,
                               int consumerHeartbeatSeconds) {
        this.tcpNoDelay = tcpNoDelay;
        this.publisherConnectMode = publisherConnectMode;
        this.consumerConnectMode = consumerConnectMode;
        this.publisherHeartbeatSeconds = publisherHeartbeatSeconds;
        this.consumerHeartbeatSeconds = consumerHeartbeatSeconds;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
    }

    public ClientConnectMode getPublisherConnectMode() {
        return publisherConnectMode;
    }

    public void setPublisherConnectMode(ClientConnectMode publisherConnectMode) {
        this.publisherConnectMode = publisherConnectMode;
    }

    public ClientConnectMode getConsumerConnectMode() {
        return consumerConnectMode;
    }

    public void setConsumerConnectMode(ClientConnectMode consumerConnectMode) {
        this.consumerConnectMode = consumerConnectMode;
    }

    public int getPublisherHeartbeatSeconds() {
        return publisherHeartbeatSeconds;
    }

    public void setPublisherHeartbeatSeconds(int publisherHeartbeatSeconds) {
        this.publisherHeartbeatSeconds = publisherHeartbeatSeconds;
    }

    public int getConsumerHeartbeatSeconds() {
        return consumerHeartbeatSeconds;
    }

    public void setConsumerHeartbeatSeconds(int consumerHeartbeatSeconds) {
        this.consumerHeartbeatSeconds = consumerHeartbeatSeconds;
    }
}
