package com.jackvanlightly.rabbittesttool.clients.consumers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.Stats;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
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

public class ConsumerGroup {
    private BenchmarkLogger logger;
    private List<Consumer> consumers;
    private ConnectionSettings connectionSettings;
    private ConsumerConfig consumerConfig;
    private MessageModel messageModel;
    private QueueHosts queueHosts;
    private ExecutorService executorService;
    private Map<String, Integer> currentQueues;
    private int consumerCounter;
    private Stats stats;

    public ConsumerGroup(ConnectionSettings connectionSettings,
                         ConsumerConfig consumerConfig,
                         VirtualHost vhost,
                         Stats stats,
                         MessageModel messageModel,
                         QueueHosts queueHosts,
                         int maxScale) {
        this.logger = new BenchmarkLogger("CONSUMER_GROUP");
        this.connectionSettings = connectionSettings;
        this.consumerConfig = consumerConfig;
        this.stats = stats;
        this.messageModel = messageModel;
        this.queueHosts = queueHosts;
        this.consumers = new ArrayList<>();

        this.currentQueues = new HashMap<>();
        for(QueueConfig queueConfig : vhost.getQueues()) {
            if(queueConfig.getGroup().equals(this.consumerConfig.getQueueGroup())) {
                for(String queue : queueConfig.getInitialQueues())
                    this.currentQueues.put(queue, 0);
            }
        }

        this.consumerCounter = 0;
        //this.executorService = Executors.newFixedThreadPool(maxScale, new NamedThreadFactory(getExecutorId()));
        this.executorService = Executors.newCachedThreadPool(new NamedThreadFactory(getExecutorId()));
        this.consumers = new ArrayList<>();
    }

    public String getGroup() {
        return this.consumerConfig.getGroup();
    }

    public int getConsumerCount() {
        return this.consumers.size();
    }

    public void createInitialConsumers() {
        for(int i = 0; i < this.consumerConfig.getScale(); i++) {
            addConsumer();
        }
    }

    public void startInitialConsumers() {
        for(Consumer consumer : this.consumers) {
            ClientUtils.waitFor(100);
            this.executorService.execute(consumer);
        }
    }

    public void setAckInterval(int ackInterval) {
        for(Consumer consumer : this.consumers) {
            consumer.setAckInterval(ackInterval);
        }
    }

    public void setAckIntervalMs(int ackIntervalMs) {
        for(Consumer consumer : this.consumers) {
            consumer.setAckIntervalMs(ackIntervalMs);
        }
    }

    public void setProcessingMs(int processingMs) {
        consumerConfig.setProcessingMs(processingMs);
        for(Consumer consumer : this.consumers) {
            consumer.setProcessingMs(processingMs);
        }
    }

    public List<Long> getRecordedReceiveCounts() {
        List<Long> receiveCounts = new ArrayList<>();
        for(Consumer consumer : this.consumers)
            receiveCounts.add(consumer.getRecordedReceiveCount());

        return receiveCounts;
    }

    public List<Long> getRealReceiveCounts() {
        List<Long> receiveCounts = new ArrayList<>();
        for(Consumer consumer : this.consumers)
            receiveCounts.add(consumer.getRealReceiveCount());

        return receiveCounts;
    }

    public void setConsumerPrefetch(short prefetch) {
        for(Consumer consumer : this.consumers) {
            consumer.setPrefetch(prefetch);
            consumer.triggerNewChannel();
        }
    }

    public void addAndStartConsumer() {
        Consumer consumer = addConsumer();
        ClientUtils.waitFor(100);
        this.executorService.execute(consumer);
    }

    public Consumer addConsumer() {
        this.consumerCounter++;

        String queue = getLowestConsumedQueue();
        ConsumerSettings settings = new ConsumerSettings(
                queue,
                this.consumerConfig.getAckMode(),
                this.consumerConfig.getFrameMax(),
                this.consumerConfig.getProcessingMs(),
                this.consumerConfig.isDownstream());

        Consumer consumer = new Consumer(
                getConsumerId(consumerCounter),
                this.connectionSettings,
                queueHosts,
                settings,
                this.stats,
                messageModel,
                this.executorService);

        this.consumers.add(consumer);
        this.currentQueues.put(queue, this.currentQueues.get(queue) + 1);
        return consumer;
    }

    public void addQueue(String queueGroup, String queue) {
        if(this.consumerConfig.getQueueGroup().equals(queueGroup)) {
            this.currentQueues.put(queue, 0);
        }
    }

    public void removeConsumer() {

    }

    public void stopAllConsumers() {
        for(Consumer consumer : this.consumers)
            consumer.signalStop();
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
        for(Map.Entry<String, Integer> entry : this.currentQueues.entrySet()) {
            if(entry.getValue() < min) {
                min = entry.getValue();
                lowestQueue = entry.getKey();
            }
        }

        return lowestQueue;
    }
}
