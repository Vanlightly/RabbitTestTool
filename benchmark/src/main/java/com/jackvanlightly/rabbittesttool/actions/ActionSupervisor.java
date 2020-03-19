package com.jackvanlightly.rabbittesttool.actions;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.TopologyGenerator;
import com.jackvanlightly.rabbittesttool.topology.model.QueueConfig;
import com.jackvanlightly.rabbittesttool.topology.model.Topology;
import com.jackvanlightly.rabbittesttool.topology.model.VirtualHost;
import com.jackvanlightly.rabbittesttool.topology.model.actions.ActionListConfig;
import com.jackvanlightly.rabbittesttool.topology.model.actions.ExecuteMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ActionSupervisor {
    BenchmarkLogger logger;
    QueueHosts queueHosts;
    QueueHosts downstreamQueueHosts;
    ConnectionSettings connectionSettings;
    ConnectionSettings downstreamConnectionSettings;
    TopologyGenerator topologyGenerator;
    AtomicBoolean isCancelled;

    public ActionSupervisor(ConnectionSettings connectionSettings,
                            ConnectionSettings downstreamConnectionSettings,
                            QueueHosts queueHosts,
                            QueueHosts downstreamQueueHosts,
                            TopologyGenerator topologyGenerator) {
        this.logger = new BenchmarkLogger("ACTIONSUPERVISOR");
        this.connectionSettings = connectionSettings;
        this.downstreamConnectionSettings = downstreamConnectionSettings;
        this.queueHosts = queueHosts;
        this.downstreamQueueHosts = downstreamQueueHosts;
        this.topologyGenerator = topologyGenerator;
        this.isCancelled = new AtomicBoolean();
    }

    public void runActions(Topology topology) {
        List<ActionList> actionLists = buildActionLists(topology);
        run(actionLists);
    }

    public void signalStop() {
        isCancelled.set(true);
    }

    private List<ActionList> buildActionLists(Topology topology) {
        List<ActionList> actionLists = new ArrayList<>();

        for(VirtualHost vhost : topology.getVirtualHosts()) {
            for(QueueConfig queueConfig : vhost.getQueues()) {
                if(queueConfig.getActionListConfig() != null) {
                    ActionListConfig config = queueConfig.getActionListConfig();
                    actionLists.add(getActionList(config, vhost));
                }
            }
        }

        return actionLists;
    }

    private ActionList getActionList(ActionListConfig config, VirtualHost vhost) {
        ConnectionSettings cs = vhost.isDownstream() ? downstreamConnectionSettings : connectionSettings;
        return new ActionList(cs.getClone(vhost.getName()), config);
    }

    private void run(List<ActionList> actionLists) {
        logger.info("Starting action list runners");
        ExecutorService actionsExecutor = Executors.newFixedThreadPool(actionLists.size());
        for(ActionList actionList : actionLists) {
            actionsExecutor.submit(() -> {
                ActionListRunner runner = new ActionListRunner(queueHosts, topologyGenerator, isCancelled);
                if(actionList.getConfig().getExecuteMode() == ExecuteMode.Synchronized)
                    runner.runSynchronized(actionList);
                else
                    runner.runIndependently(actionList);
            });
        }

        logger.info("Action list runners started");
        actionsExecutor.shutdown();

        while (!isCancelled.get() && !actionsExecutor.isTerminated()) {
            ClientUtils.waitFor(1000, isCancelled);
        }

        if(isCancelled.get()) {
            logger.info("Action list runners cancelled, waiting for termination");
            try {
                actionsExecutor.awaitTermination(5, TimeUnit.SECONDS);
            }
            catch(InterruptedException e) {
                actionsExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        logger.info("Action list runners terminated");
    }
}
