package com.jackvanlightly.rabbittesttool.actions;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.clients.publishers.Publisher;
import com.jackvanlightly.rabbittesttool.clients.publishers.QueuePublisher;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.model.actions.QueueFillActionConfig;
import io.micrometer.core.instrument.util.NamedThreadFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueueFillAction {
    BenchmarkLogger logger;
    QueueFillActionConfig config;
    ConnectionSettings connectionSettings;
    QueueHosts queueHosts;
    AtomicBoolean isCancelled;

    public QueueFillAction(QueueFillActionConfig config,
                           ConnectionSettings connectionSettings,
                           QueueHosts queueHosts,
                           AtomicBoolean isCancelled) {
        this.logger = new BenchmarkLogger("FILLACTION");
        this.config = config;
        this.connectionSettings = connectionSettings;
        this.queueHosts = queueHosts;
        this.isCancelled = isCancelled;
    }

    public void run(String queueName) {
        QueuePublisher publisher = new QueuePublisher(queueName + "-fill",
                connectionSettings,
                queueHosts,
                isCancelled);
        publisher.fill(queueName, config.getMessageSize(), config.getMessageCount());
    }

//    public void runIndependently() {
//        ExecutorService publishExecutor = Executors.newFixedThreadPool(config.getQueueNames().size());
//
//        List<QueuePublisher> publishers = new ArrayList<>();
//        for(String queue : config.getQueueNames()) {
//            QueuePublisher publisher = new QueuePublisher(queue + "-fill",
//                    connectionSettings,
//                    queueHosts,
//                    isCancelled);
//            publishers.add(publisher);
//            publishExecutor.submit(() -> publisher.fill(queue, config.getMessageSize(), config.getMessageCount()));
//        }
//
//        logger.info("Waiting for fill to complete");
//        publishExecutor.shutdown();
//
//        Instant start = Instant.now();
//        while (!isCancelled.get()) {
//            if(publishExecutor.isTerminated()) {
//                break;
//            }
//            else  {
//                if(Duration.between(start, Instant.now()).compareTo(Duration.ofMinutes(30)) > 0) {
//                    logger.info("Timed out waiting for fill to complete. Forcing shutdown of fill");
//                    publishExecutor.shutdownNow();
//                    break;
//                }
//                else {
//                    ClientUtils.waitFor(1000, isCancelled);
//                }
//            }
//        }
//
//        if(isCancelled.get()) {
//            try {
//                publishExecutor.awaitTermination(5, TimeUnit.SECONDS);
//            }
//            catch(InterruptedException e) {
//                publishExecutor.shutdownNow();
//                Thread.currentThread().interrupt();
//            }
//        }
//
//
//        logger.info("Fill complete.");
//    }
}
