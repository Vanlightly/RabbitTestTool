package com.jackvanlightly.rabbittesttool.actions;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.TopologyGenerator;
import com.jackvanlightly.rabbittesttool.topology.model.actions.*;
import io.micrometer.core.instrument.util.NamedThreadFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ActionListRunner {
    AtomicBoolean isCancelled;
    BenchmarkLogger logger;
    QueueHosts queueHosts;
    TopologyGenerator topologyGenerator;
    Random rand;

    public ActionListRunner(QueueHosts queueHosts,
                            TopologyGenerator topologyGenerator,
                            AtomicBoolean isCancelled) {
        this.logger = new BenchmarkLogger("ACTIONLISTRUNNER");
        this.queueHosts = queueHosts;
        this.topologyGenerator = topologyGenerator;
        this.isCancelled = isCancelled;
        this.rand = new Random();
    }

    public void runSynchronized(ActionList actionList) {
        mayBeDelayStart(actionList.getConfig().getActionListDelay());

        switch(actionList.getConfig().getActionCategory()) {
            case Queue:
                runQueueActionsSynchronized(actionList);
                break;
        }
    }

    public void runIndependently(ActionList actionList) {
        if(actionList.getConfig().getExecuteMode() == ExecuteMode.IndependentAllAtOnce)
            mayBeDelayStart(actionList.getConfig().getActionListDelay());

        switch(actionList.getConfig().getActionCategory()) {
            case Queue:
                runQueueActionsIndependently(actionList);
                break;
        }
    }

    private void mayBeDelayStart(ActionDelay delay) {
        int delayMs = 0;
        if(delay.getActionStartDelayType() == ActionStartDelayType.Fixed) {
            delayMs = delay.getActionUpperStartValue()*1000;
        }
        else if(delay.getActionStartDelayType() == ActionStartDelayType.Random) {
            int tUpper = delay.getActionUpperStartValue()-delay.getActionLowerStartValue();
            int waitSec = rand.nextInt(tUpper) + delay.getActionLowerStartValue();
            delayMs = waitSec*1000;
        }

        if(delayMs > 0) {
            logger.info("Action delay of ms: " + delayMs);
            ClientUtils.waitFor(delayMs, isCancelled);
        }
    }

    private void runQueueActionsSynchronized(ActionList actionList) {
        QueueActionListConfig config = (QueueActionListConfig)actionList.getConfig();

        while(!isCancelled.get()) {
            for (ActionConfig actionConfig : actionList.getConfig().getActions()) {
                ExecutorService executorService = Executors.newFixedThreadPool(
                        config.getQueueNames().size(),
                        new NamedThreadFactory("ActionsListRunner"));
                ActionDelay delay = actionConfig.getActionDelay();

                switch (actionConfig.getActionType()) {
                    case QueueDrain:
                        for (String queueName : config.getQueueNames()) {
                            runQueueDrain(actionList.getConnectionSettings(),
                                    (QueueDrainActionConfig) actionConfig,
                                    queueName,
                                    delay,
                                    executorService);
                        }
                        break;
                    case QueueFill:
                        for (String queueName : config.getQueueNames()) {
                            runQueueFill(actionList.getConnectionSettings(),
                                    (QueueFillActionConfig) actionConfig,
                                    queueName,
                                    delay,
                                    executorService);
                        }
                        break;
                    case QueuePurge:
                        for (String queueName : config.getQueueNames()) {
                            runQueuePurge(actionList.getConnectionSettings(),
                                    (QueuePurgeActionConfig) actionConfig,
                                    queueName,
                                    delay,
                                    executorService);
                        }
                        break;
                }

                awaitTermination(executorService);
            }

            if(actionList.getConfig().getActionListExecution() == ActionListExecution.ExecuteOnce)
                break;
        }
    }

    private void runQueueActionsIndependently(ActionList actionList) {
        QueueActionListConfig config = (QueueActionListConfig)actionList.getConfig();
        ExecutorService executorService = Executors.newFixedThreadPool(
                config.getQueueNames().size(),
                new NamedThreadFactory("ActionsListRunner"));

        for(String queueName : config.getQueueNames()) {
            if(actionList.getConfig().getExecuteMode() == ExecuteMode.IndependentStaggered)
                mayBeDelayStart(actionList.getConfig().getActionListDelay());

            executorService.submit(() -> {
                while(!isCancelled.get()) {
                    for (ActionConfig actionConfig : actionList.getConfig().getActions()) {
                        ActionDelay delay = actionConfig.getActionDelay();
                        switch (actionConfig.getActionType()) {
                            case QueueDrain:
                                runQueueDrain(actionList.getConnectionSettings(),
                                        (QueueDrainActionConfig) actionConfig,
                                        queueName,
                                        delay,
                                        null);
                                break;
                            case QueueFill:
                                runQueueFill(actionList.getConnectionSettings(),
                                        (QueueFillActionConfig) actionConfig,
                                        queueName,
                                        delay,
                                        null);
                                break;
                            case QueuePurge:
                                runQueuePurge(actionList.getConnectionSettings(),
                                        (QueuePurgeActionConfig) actionConfig,
                                        queueName,
                                        delay,
                                        null);
                                break;
                        }
                    }

                    if (actionList.getConfig().getActionListExecution() == ActionListExecution.ExecuteOnce)
                        break;
                }
            });
        }

        awaitTermination(executorService);
    }

    private void runQueueFill(ConnectionSettings connectionSettings,
                              QueueFillActionConfig actionConfig,
                              String queueName,
                              ActionDelay actionDelay,
                              ExecutorService executorService) {
        QueueFillAction action = new QueueFillAction(
                actionConfig,
                connectionSettings,
                queueHosts,
                isCancelled);

        if(executorService != null) {
            executorService.submit(() -> {
                mayBeDelayStart(actionDelay);
                action.run(queueName);
            });
        }
        else {
            mayBeDelayStart(actionDelay);
            action.run(queueName);
        }
    }

    private void runQueueDrain(ConnectionSettings connectionSettings,
                               QueueDrainActionConfig actionConfig,
                               String queueName,
                               ActionDelay actionDelay,
                               ExecutorService executorService) {
        QueueDrainAction action = new QueueDrainAction(
                connectionSettings,
                actionConfig,
                queueHosts,
                isCancelled);

        if(executorService != null) {
            executorService.submit(() -> {
                mayBeDelayStart(actionDelay);
                action.run(queueName);
            });
        }
        else {
            mayBeDelayStart(actionDelay);
            action.run(queueName);
        }
    }

    private void runQueuePurge(ConnectionSettings connectionSettings,
                               QueuePurgeActionConfig actionConfig,
                               String queueName,
                               ActionDelay actionDelay,
                               ExecutorService executorService) {
        QueuePurgeAction action = new QueuePurgeAction(
                connectionSettings,
                actionConfig,
                topologyGenerator);

        if(executorService != null) {
            executorService.submit(() -> {
                mayBeDelayStart(actionDelay);
                action.run(queueName);
            });
        }
        else {
            mayBeDelayStart(actionDelay);
            action.run(queueName);
        }
    }

    private void awaitTermination(ExecutorService executorService) {
        executorService.shutdown();

        while (!isCancelled.get()) {
            if(executorService.isTerminated()) {
                break;
            }
            else  {
                ClientUtils.waitFor(1000, isCancelled);
            }
        }

        if(isCancelled.get()) {
            try {
                executorService.awaitTermination(5, TimeUnit.SECONDS);
            }
            catch(InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
