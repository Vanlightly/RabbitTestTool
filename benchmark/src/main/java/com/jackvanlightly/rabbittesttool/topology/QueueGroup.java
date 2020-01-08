package com.jackvanlightly.rabbittesttool.topology;

import com.jackvanlightly.rabbittesttool.topology.model.QueueConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class QueueGroup {
    private String vhostName;
    private QueueConfig queueConfig;
    private List<String> queues;
    private int queueCounter;
    private boolean queuesPredeclared;
    private TopologyGenerator topologyGenerator;
    private List<String> nodes;
    private Random rand;
    private int nodeIndex;


    public QueueGroup(String vhostName,
                      QueueConfig queueConfig,
                      List<String> nodes,
                      TopologyGenerator topologyGenerator) {
        this.vhostName = vhostName;
        this.queueConfig = queueConfig;
        this.nodes = nodes;
        this.topologyGenerator = topologyGenerator;
        this.rand = new Random();
        this.nodeIndex = rand.nextInt(nodes.size());
    }

    public String getGroup() {
        return this.queueConfig.getGroup();
    }

    public int getQueueCount() {
        return this.queues.size();
    }

    public void createInitialQueues(boolean declareQueues) {
        if(queueConfig.getGroup().equals("sharded")) {
            queues = new ArrayList<>();
            queues.add(queueConfig.getGroup());
            queueCounter = queues.size();
        }
        else {
            queues = queueConfig.getInitialQueues();
            if (declareQueues)
                topologyGenerator.declareQueuesAndBindings(queueConfig);
            queueCounter = queues.size();
        }
    }

    public void createAllQueues(boolean declareQueues, int maxQueues) {
        queuesPredeclared = true;
        queues = queueConfig.getInitialQueues();
        if(declareQueues)
            topologyGenerator.declareQueuesAndBindings(queueConfig);
        queueCounter = queues.size();
        int counter = queueCounter;

        while(counter < maxQueues) {
            topologyGenerator.declareQueue(queueConfig, counter, nodeIndex);
            topologyGenerator.declareQueueBindings(queueConfig, counter);
            nextNode();
            counter++;
        }
    }

    public String addQueue() {
        queueCounter++;
        String queueName = queueConfig.getQueueName(queueCounter);
        queues.add(queueName);

        if(!queuesPredeclared) {
            topologyGenerator.declareQueue(queueConfig, queueCounter, nodeIndex);
            topologyGenerator.declareQueueBindings(queueConfig, queueCounter);
            nextNode();
        }

        return queueName;
    }

    private void nextNode() {
        nodeIndex++;
        if(nodeIndex >= nodes.size())
            nodeIndex = 0;
    }
}
