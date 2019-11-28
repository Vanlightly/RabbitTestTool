package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.Stats;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.PublisherConfig;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.SendToMode;
import com.jackvanlightly.rabbittesttool.topology.model.QueueConfig;
import com.jackvanlightly.rabbittesttool.topology.model.VirtualHost;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class PublisherGroup {
    private static final Logger LOGGER = LoggerFactory.getLogger("PUBLISHER_GROUP");
    private List<Publisher> publishers;
    private List<String> currentQueuesInGroup;
    private ConnectionSettings connectionSettings;
    private PublisherConfig publisherConfig;
    private MessageModel messageModel;
    private QueueHosts queueHosts;
    private ExecutorService executorService;
    private int publisherCounter;
    private Stats stats;

    public PublisherGroup(ConnectionSettings connectionSettings,
                          PublisherConfig publisherConfig,
                          VirtualHost vhost,
                          Stats stats,
                          MessageModel messageModel,
                          QueueHosts queueHosts,
                          int maxScale) {
        this.connectionSettings = connectionSettings;
        this.publisherConfig = publisherConfig;
        this.stats = stats;
        this.messageModel = messageModel;
        this.queueHosts = queueHosts;
        this.publishers = new ArrayList<>();

        this.publisherCounter = 0;
        this.executorService = Executors.newFixedThreadPool(maxScale, new NamedThreadFactory(getExecutorId()));
        this.publishers = new ArrayList<>();


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
        ExecutorService publishExecutor = Executors.newFixedThreadPool(this.publishers.size(), new NamedThreadFactory(getInitialPublishExecutorId()));
        for(Publisher publisher : this.publishers) {
            publishExecutor.submit(() -> publisher.performInitialSend());
        }

        LOGGER.info("Waiting for initial publish to complete");
        publishExecutor.shutdown();

        try {
            if (!publishExecutor.awaitTermination(3600, TimeUnit.SECONDS)) {
                LOGGER.info("Timed out waiting for initial publish to complete. Forcing shutdown of initial publish");
                publishExecutor.shutdownNow();
                return false;
            }
        } catch (InterruptedException ex) {
            publishExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            return false;
        }
        LOGGER.info("Initial publish complete.");
        return true;
    }

    public void startInitialPublishers() {
        for(Publisher publisher : this.publishers) {
            this.executorService.execute(publisher);
        }
    }

    public int getPublisherCount() {
        return this.publishers.size();
    }

    public void addAndStartPublisher() {
        Publisher publisher = addPublisher();
        this.executorService.execute(publisher);
    }

    private Publisher addPublisher() {
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

        Publisher publisher = new Publisher(
                getPublisherId(publisherCounter),
                messageModel,
                stats,
                connectionSettings,
                queueHosts,
                settings,
                currentQueuesInGroup);

        this.publishers.add(publisher);

        return publisher;
    }

    public void addQueue(String queueGroup, String queue) {
        if(publisherConfig.getSendToMode() == SendToMode.QueueGroup
            && publisherConfig.getSendToQueueGroup().getQueueGroup().equals(queueGroup)) {
            currentQueuesInGroup.add(queue);

            for (Publisher publisher : this.publishers)
                publisher.addQueue(queue);
        }
    }

    public void setMessageSize(int bytes) {
        for(Publisher publisher : this.publishers)
            publisher.setMessageSize(bytes);
    }

    public void setMessageHeaders(int headers) {
        for(Publisher publisher : this.publishers)
            publisher.setMessageHeaders(headers);
    }

    public int getPublishRatePerSecond() {
        return this.publishers.get(0).getPublishRatePerSecond();
    }

    public void setPublishRatePerSecond(int msgsPerSecond) {
        for(Publisher publisher : this.publishers)
            publisher.setPublishRatePerSecond(msgsPerSecond);
    }

    public void modifyPublishRatePerSecond(double percentModification) {
        for(Publisher publisher : this.publishers)
            publisher.modifyPublishRatePerSecond(percentModification);
    }

    public int getInFlightLimit() {
        return this.publishers.get(0).getInFlightLimit();
    }

    public void setInFlightLimit(int inFlightLimit) {
        for(Publisher publisher : this.publishers)
            publisher.setInFlightLimit(inFlightLimit);
    }

    public void setRoutingKeyIndex(int rkIndex) {
        for(Publisher publisher : this.publishers)
            publisher.setRoutingKeyIndex(rkIndex);
    }

    public void resetSendCount() {
        for(Publisher publisher : this.publishers)
            publisher.resetSendCount();
    }

    public List<Long> getRecordedSendCounts() {
        List<Long> sendCounts = new ArrayList<>();
        for(Publisher publisher : this.publishers)
            sendCounts.add(publisher.getRecordedSendCount());

        return sendCounts;
    }

    public List<Long> getRealSendCounts() {
        List<Long> sendCounts = new ArrayList<>();
        for(Publisher publisher : this.publishers)
            sendCounts.add(publisher.getRealSendCount());

        return sendCounts;
    }

    public void stopAllPublishers() {
        for(Publisher publisher : this.publishers)
            publisher.signalStop();
    }

    public void shutdown() {
        this.executorService.shutdownNow();
    }

    public void awaitTermination() {
        try {
            boolean shutdown = this.executorService.awaitTermination(10, TimeUnit.SECONDS);
            if(!shutdown)
                LOGGER.info("Could not shutdown thread pool of publisher group");
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
