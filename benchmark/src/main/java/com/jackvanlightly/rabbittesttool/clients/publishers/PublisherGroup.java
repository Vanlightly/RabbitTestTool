package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.clients.consumers.Consumer;
import com.jackvanlightly.rabbittesttool.clients.consumers.StreamConsumer;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.MetricGroup;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.model.Protocol;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.PublisherConfig;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.SendToMode;
import com.jackvanlightly.rabbittesttool.topology.model.QueueConfig;
import com.jackvanlightly.rabbittesttool.topology.model.VirtualHost;
import io.micrometer.core.instrument.util.NamedThreadFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class PublisherGroup {
    private BenchmarkLogger logger;
    private List<Publisher> publishers;
    private List<StreamPublisher> streamPublishers;
    private List<String> currentQueuesInGroup;
    private ConnectionSettings connectionSettings;
    private PublisherConfig publisherConfig;
    private MessageModel messageModel;
    private QueueHosts queueHosts;
    private ExecutorService executorService;
    private int publisherCounter;

    public PublisherGroup(ConnectionSettings connectionSettings,
                          PublisherConfig publisherConfig,
                          VirtualHost vhost,
                          MessageModel messageModel,
                          QueueHosts queueHosts) {
        this.logger = new BenchmarkLogger("PUBLISHER_GROUP");
        this.connectionSettings = connectionSettings;
        this.publisherConfig = publisherConfig;
        this.messageModel = messageModel;
        this.queueHosts = queueHosts;
        this.publishers = new ArrayList<>();
        this.streamPublishers = new ArrayList<>();

        this.publisherCounter = 0;
        this.executorService = Executors.newCachedThreadPool(new NamedThreadFactory(getExecutorId()));

        if(publisherConfig.getSendToMode() == SendToMode.QueueGroup) {
            for (QueueConfig queueConfig : vhost.getQueues()) {
                if (queueConfig.getGroup().equals(publisherConfig.getSendToQueueGroup().getQueueGroup()))
                    this.currentQueuesInGroup = queueConfig.getInitialQueues();
            }
        }
    }

    public String getGroup() {
        return publisherConfig.getGroup();
    }

    public void createInitialPublishers() {
        for(int i = 0; i < this.publisherConfig.getScale(); i++)
            addPublisher();
    }

    public boolean performInitialPublish() {
        ExecutorService publishExecutor = null;
        if(!publishers.isEmpty()) {
            publishExecutor = Executors.newFixedThreadPool(this.publishers.size(),
                    new NamedThreadFactory(getInitialPublishExecutorId()));

            for (Publisher publisher : this.publishers) {
                publishExecutor.submit(() -> publisher.performInitialSend());
            }
        }
        else {
            publishExecutor = Executors.newFixedThreadPool(this.streamPublishers.size(),
                    new NamedThreadFactory(getInitialPublishExecutorId()));

            for (StreamPublisher publisher : this.streamPublishers) {
                publishExecutor.submit(() -> publisher.performInitialSend());
            }
        }

        logger.info("Waiting for initial publish to complete");
        publishExecutor.shutdown();

        try {
            if (!publishExecutor.awaitTermination(3600, TimeUnit.SECONDS)) {
                logger.info("Timed out waiting for initial publish to complete. Forcing shutdown of initial publish");
                publishExecutor.shutdownNow();
                return false;
            }
        } catch (InterruptedException ex) {
            publishExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
        logger.info("Initial publish complete.");
        return true;
    }

    public void startInitialPublishers() {
        if(!publishers.isEmpty()) {
            for (Publisher publisher : this.publishers) {
                ClientUtils.waitFor(100);
                this.executorService.execute(publisher);
            }
        }
        else {
            for (StreamPublisher publisher : this.streamPublishers) {
                ClientUtils.waitFor(100);
                this.executorService.execute(publisher);
            }
        }
    }

    public void setWarmUpModifier(double modifier) {
        if(!publishers.isEmpty()) {
            for (Publisher publisher : this.publishers) {
                publisher.setWarmUpModifier(modifier);
            }
        }
        else {
            for (StreamPublisher publisher : this.streamPublishers) {
                publisher.setWarmUpModifier(modifier);
            }
        }
    }

    public void endWarmUp() {
        if(!publishers.isEmpty()) {
            for (Publisher publisher : this.publishers) {
                publisher.endWarmUp();
            }
        }
        else {
            for (StreamPublisher publisher : this.streamPublishers) {
                publisher.endWarmUp();
            }
        }
    }

    public int getPublisherCount() {
        if(!publishers.isEmpty())
            return this.publishers.size();
        else
            return this.streamPublishers.size();
    }

    public void addAndStartPublisher() {
        Runnable publisher = addPublisher();
        ClientUtils.waitFor(100);
        this.executorService.execute(publisher);
    }

    public Runnable addPublisher() {
        this.publisherCounter++;

        PublisherSettings settings = null;
        if(publisherConfig.getSendToMode() == SendToMode.Exchange) {
            settings = new PublisherSettings(publisherConfig.getSendToExchange(),
                    publisherConfig.getPublisherMode(),
                    StreamUtils.getStreams(publisherConfig.getStreams()),
                    publisherConfig.getMessageSize(),
                    publisherConfig.getDeliveryMode(),
                    publisherConfig.getFrameMax(),
                    publisherConfig.getMessageLimit(),
                    publisherConfig.getInitialPublish());
        }
        else {
            settings = new PublisherSettings(publisherConfig.getSendToQueueGroup(),
                    publisherConfig.getPublisherMode(),
                    StreamUtils.getStreams(publisherConfig.getStreams()),
                    publisherConfig.getMessageSize(),
                    publisherConfig.getDeliveryMode(),
                    publisherConfig.getFrameMax(),
                    publisherConfig.getMessageLimit(),
                    publisherConfig.getInitialPublish());
        }

        settings.setPublishRatePerSecond(publisherConfig.getPublishRatePerSecond());
        settings.setMessageHeadersPerMessage(publisherConfig.getHeadersPerMessage());
        settings.setAvailableHeaders(publisherConfig.getAvailableHeaders());

        if(settings.getPublisherMode().getProtocol() == Protocol.AMQP091) {
            Publisher publisher = new Publisher(
                    getPublisherId(publisherCounter),
                    messageModel,
                    MetricGroup.createAmqpPublisherMetricGroup(),
                    connectionSettings,
                    queueHosts,
                    settings,
                    currentQueuesInGroup,
                    executorService);

            this.publishers.add(publisher);

            return publisher;
        }
        else {
            StreamPublisher publisher = new StreamPublisher(
                    getPublisherId(publisherCounter),
                    messageModel,
                    MetricGroup.createStreamPublisherMetricGroup(),
                    connectionSettings,
                    this.queueHosts,
                    settings,
                    currentQueuesInGroup);

            this.streamPublishers.add(publisher);

            return publisher;
        }
    }

    public void removePublisher() {
        if(!publishers.isEmpty()) {
            Publisher publisher = this.publishers.get(this.publishers.size() - 1);
            publisher.signalStop();
            this.publishers.remove(this.publishers.size() - 1);
        }
        else if(!streamPublishers.isEmpty()) {
            StreamPublisher publisher = this.streamPublishers.get(this.streamPublishers.size() - 1);
            publisher.signalStop();
            this.streamPublishers.remove(this.streamPublishers.size() - 1);
        }
    }

    public void addQueue(String queueGroup, String queue) {
        if(publisherConfig.getSendToMode() == SendToMode.QueueGroup
            && publisherConfig.getSendToQueueGroup().getQueueGroup().equals(queueGroup)) {
            currentQueuesInGroup.add(queue);

            if(!publishers.isEmpty()) {
                for (Publisher publisher : this.publishers)
                    publisher.addQueue(queue);
            }
            else {
                for (StreamPublisher publisher : this.streamPublishers)
                    publisher.addQueue(queue);
            }
        }
    }

    public void removeQueue(String queueGroup, String queue) {
        if(publisherConfig.getSendToMode() == SendToMode.QueueGroup
                && publisherConfig.getSendToQueueGroup().getQueueGroup().equals(queueGroup)) {
            currentQueuesInGroup.remove(queue);

            if(!publishers.isEmpty()) {
                for (Publisher publisher : this.publishers)
                    publisher.removeQueue(queue);
            }
            else {
                for (StreamPublisher publisher : this.streamPublishers)
                    publisher.removeQueue(queue);
            }
        }
    }

    public void setMessageSize(int bytes) {
        if(!publishers.isEmpty()) {
            for (Publisher publisher : this.publishers)
                publisher.setMessageSize(bytes);
        }
        else {
            for (StreamPublisher publisher : this.streamPublishers)
                publisher.setMessageSize(bytes);
        }
    }

    public void setMessageHeaders(int headers) {
        if(!publishers.isEmpty()) {
            for (Publisher publisher : this.publishers)
                publisher.setMessageHeaders(headers);
        }
        else
            throw new UnsupportedOperationException();
    }

    public int getPublishRatePerSecond() {
        if(!publishers.isEmpty())
            return this.publishers.get(0).getPublishRatePerSecond();
        else
            return this.streamPublishers.get(0).getPublishRatePerSecond();
    }

    public void setPublishRatePerSecond(int msgsPerSecond) {
        if(!publishers.isEmpty()) {
            for (Publisher publisher : this.publishers)
                publisher.setPublishRatePerSecond(msgsPerSecond);
        }
        else {
            for (StreamPublisher publisher : this.streamPublishers)
                publisher.setPublishRatePerSecond(msgsPerSecond);
        }
    }

    public void modifyPublishRatePerSecond(double percentModification) {
        if(!publishers.isEmpty()) {
            for (Publisher publisher : this.publishers)
                publisher.modifyPublishRatePerSecond(percentModification);
        }
        else {
            for (StreamPublisher publisher : this.streamPublishers)
                publisher.modifyPublishRatePerSecond(percentModification);
        }
    }

    public int getInFlightLimit() {
        if(!publishers.isEmpty())
            return this.publishers.get(0).getInFlightLimit();
        else
            return this.streamPublishers.get(0).getInFlightLimit();
    }

    public int getMaxBatchSize() {
        if(streamPublishers.isEmpty())
            return 0;
        else
            return this.streamPublishers.get(0).getMaxBatchSize();
    }

    public int getMaxBatchSizeBytes() {
        if(streamPublishers.isEmpty())
            return 0;
        else
            return this.streamPublishers.get(0).getMaxBatchSizeBytes();
    }

    public int getMaxBatchWaitMs() {
        if(streamPublishers.isEmpty())
            return 0;
        else
            return this.streamPublishers.get(0).getMaxBatchWaitMs();
    }

    public void setInFlightLimit(int inFlightLimit) {
        if(!publishers.isEmpty()) {
            for (Publisher publisher : this.publishers)
                publisher.setInFlightLimit(inFlightLimit);
        }
        else {
            for (StreamPublisher publisher : this.streamPublishers)
                publisher.setInFlightLimit(inFlightLimit);
        }
    }

    public void setRoutingKeyIndex(int rkIndex) {
        if(!publishers.isEmpty()) {
            for (Publisher publisher : this.publishers)
                publisher.setRoutingKeyIndex(rkIndex);
        }
        else {
            throw new UnsupportedOperationException();
        }
    }

    public void resetSendCount() {
        if(!publishers.isEmpty()) {
            for (Publisher publisher : this.publishers)
                publisher.resetSendCount();
        }
        else {
            for (StreamPublisher publisher : this.streamPublishers)
                publisher.resetSendCount();
        }
    }

    public List<Long> getRecordedSendCounts() {
        List<Long> sendCounts = new ArrayList<>();

        if(!publishers.isEmpty()) {
            for (Publisher publisher : this.publishers)
                sendCounts.add(publisher.getRecordedSendCount());
        }
        else {
            for (StreamPublisher publisher : this.streamPublishers)
                sendCounts.add(publisher.getRecordedSendCount());
        }

        return sendCounts;
    }

    public List<Long> getRealSendCounts() {
        List<Long> sendCounts = new ArrayList<>();

        if(!publishers.isEmpty()) {
            for (Publisher publisher : this.publishers)
                sendCounts.add(publisher.getRealSendCount());
        }
        else {
            for (StreamPublisher publisher : this.streamPublishers)
                sendCounts.add(publisher.getRealSendCount());
        }

        return sendCounts;
    }

    public List<MetricGroup> getPublisherMetrics() {
        List<MetricGroup> metrics = new ArrayList<>();

        if(!publishers.isEmpty()) {
            for (Publisher publisher : this.publishers)
                metrics.add(publisher.getMetricGroup());
        }
        else {
            for (StreamPublisher publisher : this.streamPublishers)
                metrics.add(publisher.getMetricGroup());
        }

        return metrics;
    }

    public void stopAllPublishers() {
        if(!publishers.isEmpty()) {
            for (Publisher publisher : this.publishers)
                publisher.signalStop();
        }
        else {
            for (StreamPublisher publisher : this.streamPublishers)
                publisher.signalStop();
        }
    }

    public int getPendingConfirmCount() {
        int total = 0;

        if(!publishers.isEmpty()) {
            for (Publisher publisher : this.publishers) {
                total += publisher.getPendingConfirmCount();
            }
        }
        else {
            for (StreamPublisher publisher : this.streamPublishers) {
                total += publisher.getPendingConfirmCount();
            }
        }

        return total;
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

    private String getPublisherId(int counter) {
        return this.connectionSettings.getVhost() + "_" + this.publisherConfig.getGroup() + "_" + counter;
    }

    private String getExecutorId() {
        return "PublisherGroup_" + this.connectionSettings.getVhost() + "_" + this.publisherConfig.getGroup();
    }

    private String getInitialPublishExecutorId() {
        return "InitialPublish_" + this.connectionSettings.getVhost() + "_" + this.publisherConfig.getGroup();
    }
}
