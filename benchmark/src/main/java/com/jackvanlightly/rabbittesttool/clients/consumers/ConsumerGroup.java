package com.jackvanlightly.rabbittesttool.clients.consumers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.clients.publishers.Publisher;
import com.jackvanlightly.rabbittesttool.clients.publishers.StreamPublisher;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.MetricGroup;
import com.jackvanlightly.rabbittesttool.statistics.Stats;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.model.Protocol;
import com.jackvanlightly.rabbittesttool.topology.model.VirtualHost;
import com.jackvanlightly.rabbittesttool.topology.model.consumers.ConsumerConfig;
import com.jackvanlightly.rabbittesttool.topology.model.QueueConfig;
import io.micrometer.core.instrument.util.NamedThreadFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ConsumerGroup {
    BenchmarkLogger logger;
    List<Consumer> consumers;
    List<StreamConsumer> streamConsumers;
    ConnectionSettings connectionSettings;
    ConsumerConfig consumerConfig;
    MessageModel messageModel;
    QueueHosts queueHosts;
    ExecutorService executorService;
    Map<String, Integer> currentQueues;
    int consumerCounter;

    public ConsumerGroup(ConnectionSettings connectionSettings,
                         ConsumerConfig consumerConfig,
                         VirtualHost vhost,
                         MessageModel messageModel,
                         QueueHosts queueHosts) {
        this.logger = new BenchmarkLogger("CONSUMER_GROUP");
        this.connectionSettings = connectionSettings;
        this.consumerConfig = consumerConfig;
        this.messageModel = messageModel;
        this.queueHosts = queueHosts;
        this.consumers = new ArrayList<>();
        this.streamConsumers = new ArrayList<>();

        this.currentQueues = new HashMap<>();
        for(QueueConfig queueConfig : vhost.getQueues()) {
            if(queueConfig.getGroup().equals(this.consumerConfig.getQueueGroup())) {
                for(String queue : queueConfig.getInitialQueues())
                    this.currentQueues.put(queue, 0);
            }
        }

        this.consumerCounter = 0;
        this.executorService = Executors.newCachedThreadPool(new NamedThreadFactory(getExecutorId()));
        this.consumers = new ArrayList<>();
    }

    public String getGroup() {
        return this.consumerConfig.getGroup();
    }

    public int getConsumerCount() {
        if(!consumers.isEmpty())
            return this.consumers.size();
        else
            return this.streamConsumers.size();
    }

    public void createInitialConsumers() {
        for(int i = 0; i < this.consumerConfig.getScale(); i++) {
            addConsumer();
        }
    }

    public void startInitialConsumers() {
        if(!consumers.isEmpty()) {
            for (Consumer consumer : this.consumers) {
                ClientUtils.waitFor(100);
                this.executorService.execute(consumer);
            }
        }
        else {
            for (StreamConsumer consumer : this.streamConsumers) {
                ClientUtils.waitFor(100);
                this.executorService.execute(consumer);
            }
        }
    }

    public void setAckInterval(int ackInterval) {
        if(!consumers.isEmpty()) {
            for (Consumer consumer : this.consumers) {
                consumer.setAckInterval(ackInterval);
            }
        }
        else {
            for (StreamConsumer consumer : this.streamConsumers) {
                consumer.setAckInterval(ackInterval);
            }
        }
    }

    public void setAckIntervalMs(int ackIntervalMs) {
        if(!consumers.isEmpty()) {
            for (Consumer consumer : this.consumers) {
                consumer.setAckIntervalMs(ackIntervalMs);
            }
        }
        else {
            for (StreamConsumer consumer : this.streamConsumers) {
                consumer.setAckIntervalMs(ackIntervalMs);
            }
        }
    }

    public void setProcessingMs(int processingMs) {
        consumerConfig.setProcessingMs(processingMs);

        if(!consumers.isEmpty()) {
            for (Consumer consumer : this.consumers) {
                consumer.setProcessingMs(processingMs);
            }
        }
        else {
            for (StreamConsumer consumer : this.streamConsumers) {
                consumer.setProcessingMs(processingMs);
            }
        }
    }

    public List<Long> getRecordedReceiveCounts() {
        List<Long> receiveCounts = new ArrayList<>();

        if(!consumers.isEmpty()) {
            for (Consumer consumer : this.consumers)
                receiveCounts.add(consumer.getRecordedReceiveCount());
        }
        else {
            for (StreamConsumer consumer : this.streamConsumers)
                receiveCounts.add(consumer.getRecordedReceiveCount());
        }

        return receiveCounts;
    }

    public List<Long> getRealReceiveCounts() {
        List<Long> receiveCounts = new ArrayList<>();

        if(!consumers.isEmpty()) {
            for (Consumer consumer : this.consumers)
                receiveCounts.add(consumer.getRealReceiveCount());
        }
        else {
            for (StreamConsumer consumer : this.streamConsumers)
                receiveCounts.add(consumer.getRealReceiveCount());
        }

        return receiveCounts;
    }

    public void setConsumerPrefetch(short prefetch) {
        if(!consumers.isEmpty()) {
            for (Consumer consumer : this.consumers) {
                consumer.setPrefetch(prefetch);
                consumer.triggerNewChannel();
            }
        }
        else {
            for (StreamConsumer consumer : this.streamConsumers) {
                consumer.setPrefetch(prefetch);
                consumer.triggerNewChannel();
            }
        }
    }

    public void addAndStartConsumer() {
        Runnable consumer = addConsumer();
        ClientUtils.waitFor(100);
        this.executorService.execute(consumer);
    }

    public Runnable addConsumer() {
        this.consumerCounter++;

        String queue = getLowestConsumedQueue();
        ConsumerSettings settings = new ConsumerSettings(
                queue,
                this.consumerConfig.getAckMode(),
                this.consumerConfig.getFrameMax(),
                this.consumerConfig.getProcessingMs(),
                this.consumerConfig.isDownstream());

        this.currentQueues.put(queue, this.currentQueues.get(queue) + 1);

        if(this.consumerConfig.getProtocol() == Protocol.AMQP091) {
            Consumer consumer = new Consumer(
                    getConsumerId(consumerCounter),
                    this.connectionSettings,
                    queueHosts,
                    settings,
                    MetricGroup.createAmqpConsumerMetricGroup(),
                    messageModel,
                    this.executorService);

            this.consumers.add(consumer);
            return consumer;
        }
        else {
            StreamConsumer consumer = new StreamConsumer(
                    getConsumerId(consumerCounter),
                    this.connectionSettings,
                    this.queueHosts,
                    settings,
                    MetricGroup.createStreamConsumerMetricGroup(),
                    messageModel);

            this.streamConsumers.add(consumer);
            return consumer;
        }
    }

    public void addQueue(String queueGroup, String queue) {
        if(this.consumerConfig.getQueueGroup().equals(queueGroup)) {
            this.currentQueues.put(queue, 0);
        }
    }

    public void removeQueue(String queueGroup, String queue) {
        if(this.consumerConfig.getQueueGroup().equals(queueGroup)) {
            this.currentQueues.remove(queue);
        }
    }

    public List<MetricGroup> getConsumerMetrics() {
        List<MetricGroup> metrics = new ArrayList<>();

        if(!consumers.isEmpty()) {
            for (Consumer consumer : this.consumers)
                metrics.add(consumer.getMetricGroup());
        }
        else {
            for (StreamConsumer consumer : this.streamConsumers)
                metrics.add(consumer.getMetricGroup());
        }

        return metrics;
    }

    public void removeConsumer() {
        if(!consumers.isEmpty()) {
            Consumer consumer = this.consumers.get(this.consumers.size() - 1);
            consumer.signalStop();
            this.consumers.remove(this.consumers.size() - 1);
        }
        else if(!streamConsumers.isEmpty()) {
            StreamConsumer consumer = this.streamConsumers.get(this.streamConsumers.size() - 1);
            consumer.signalStop();
            this.streamConsumers.remove(this.streamConsumers.size() - 1);
        }
    }

    public void stopAllConsumers() {
        if(!consumers.isEmpty()) {
            for (Consumer consumer : this.consumers)
                consumer.signalStop();
        }
        else {
            for (StreamConsumer consumer : this.streamConsumers)
                consumer.signalStop();
        }
    }

    public void shutdown() {
        this.executorService.shutdownNow();
    }

    public void awaitTermination() {
        try {
            boolean shutdown = this.executorService.awaitTermination(10, TimeUnit.SECONDS);
            if(!shutdown)
                logger.info("Could not shutdown thread pool of publisher group");
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String getConsumerId(int counter) {
        return consumerConfig.getVhostName() + "_" + consumerConfig.getGroup() + "_" + counter;
    }

    private String getExecutorId() {
        return "ConsumerGroup_" + consumerConfig.getVhostName() + "_" + consumerConfig.getGroup();
    }

    private String getLowestConsumedQueue() {
        int min = Integer.MAX_VALUE;
        String lowestQueue = "";

        List<String> orderedQueues = this.currentQueues.keySet().stream().sorted().collect(Collectors.toList());
        for(String queue : orderedQueues) {
            int consumeCount = currentQueues.get(queue);
            if(consumeCount < min) {
                min = consumeCount;
                lowestQueue = queue;
            }
        }

        return lowestQueue;
    }
}
