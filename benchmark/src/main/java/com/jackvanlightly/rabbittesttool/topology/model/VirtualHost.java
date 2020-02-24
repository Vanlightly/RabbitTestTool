package com.jackvanlightly.rabbittesttool.topology.model;

import com.jackvanlightly.rabbittesttool.topology.model.consumers.ConsumerConfig;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.PublisherConfig;

import java.util.ArrayList;
import java.util.List;

public class VirtualHost {
    private String name;
    private List<PublisherConfig> publishers;
    private List<ConsumerConfig> consumers;
    private List<ExchangeConfig> exchanges;
    private List<QueueConfig> queues;
    private boolean isDownstream;

    public VirtualHost() {
        publishers = new ArrayList<>();
        consumers = new ArrayList<>();
        exchanges = new ArrayList<>();
        queues = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<PublisherConfig> getPublishers() {
        return publishers;
    }

    public void setPublishers(List<PublisherConfig> publishers) {
        this.publishers = publishers;
    }

    public List<ConsumerConfig> getConsumers() {
        return consumers;
    }

    public void setConsumers(List<ConsumerConfig> consumers) {
        this.consumers = consumers;
    }

    public List<ExchangeConfig> getExchanges() {
        return exchanges;
    }

    public void setExchanges(List<ExchangeConfig> exchanges) {
        this.exchanges = exchanges;
    }

    public List<QueueConfig> getQueues() {
        return queues;
    }

    public void setQueues(List<QueueConfig> queues) {
        this.queues = queues;
    }

    public boolean isDownstream() {
        return isDownstream;
    }

    public void setDownstream(boolean downstream) {
        isDownstream = downstream;
    }
}
