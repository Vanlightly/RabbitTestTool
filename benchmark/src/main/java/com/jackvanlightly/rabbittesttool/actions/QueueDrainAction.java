package com.jackvanlightly.rabbittesttool.actions;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.clients.consumers.DrainConsumer;
import com.jackvanlightly.rabbittesttool.clients.consumers.EventingConsumer;
import com.jackvanlightly.rabbittesttool.clients.publishers.QueuePublisher;
import com.jackvanlightly.rabbittesttool.topology.Broker;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.TopologyException;
import com.jackvanlightly.rabbittesttool.topology.model.actions.QueueDrainActionConfig;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueueDrainAction {
    BenchmarkLogger logger;
    ConnectionSettings connectionSettings;
    QueueDrainActionConfig config;
    QueueHosts queueHosts;
    AtomicBoolean isCancelled;

    public QueueDrainAction(ConnectionSettings connectionSettings,
                            QueueDrainActionConfig config,
                            QueueHosts queueHosts,
                            AtomicBoolean isCancelled) {
        this.logger = new BenchmarkLogger("DRAINACTION");
        this.connectionSettings = connectionSettings;
        this.config = config;
        this.queueHosts = queueHosts;
        this.isCancelled = isCancelled;
    }

    public void run(String queueName) {
        DrainConsumer consumer = new DrainConsumer(queueName + "-drain",
                connectionSettings,
                queueHosts,
                isCancelled);
        consumer.drain(queueName, config.getThresholdSeconds());
        logger.info("Drain complete.");
    }
}
