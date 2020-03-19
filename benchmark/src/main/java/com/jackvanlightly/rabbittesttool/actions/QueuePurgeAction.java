package com.jackvanlightly.rabbittesttool.actions;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.clients.consumers.DrainConsumer;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.TopologyGenerator;
import com.jackvanlightly.rabbittesttool.topology.model.actions.QueueDrainActionConfig;
import com.jackvanlightly.rabbittesttool.topology.model.actions.QueuePurgeActionConfig;

import java.util.concurrent.atomic.AtomicBoolean;

public class QueuePurgeAction {
    BenchmarkLogger logger;
    ConnectionSettings connectionSettings;
    QueuePurgeActionConfig config;
    TopologyGenerator topologyGenerator;

    public QueuePurgeAction(ConnectionSettings connectionSettings,
                            QueuePurgeActionConfig config,
                            TopologyGenerator topologyGenerator) {
        this.logger = new BenchmarkLogger("DRAINACTION");
        this.connectionSettings = connectionSettings;
        this.config = config;
        this.topologyGenerator = topologyGenerator;
    }

    public void run(String queueName) {
        topologyGenerator.purgeQueue(connectionSettings.getVhost(),
                queueName,
                connectionSettings.isDownstream());
        logger.info("Purge complete.");
    }
}
