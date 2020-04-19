package com.jackvanlightly.rabbittesttool.topology.model.publishers;

import com.jackvanlightly.rabbittesttool.topology.QueueGroup;
import com.jackvanlightly.rabbittesttool.topology.model.QueueConfig;
import com.jackvanlightly.rabbittesttool.topology.model.VirtualHost;

import java.util.List;

public class SendToQueueGroup {
    private String queueGroup;
    private QueueGroupMode queueGroupMode;
    private List<String> initialQueuesInGroup;

    public SendToQueueGroup clone(int scaleNumber) {
        SendToQueueGroup s2q = new SendToQueueGroup();
        s2q.setInitialQueuesInGroup(initialQueuesInGroup);
        s2q.setQueueGroup(queueGroup + VirtualHost.getScaleSuffix(scaleNumber));
        s2q.setQueueGroupMode(queueGroupMode);
        return s2q;
    }

    public static SendToQueueGroup withGroup(String queueGroup, QueueGroupMode queueGroupMode, List<QueueConfig> queueConfigs) {
        List<String> initialQueuesInGroup = null;
        for(QueueConfig queueConfig : queueConfigs) {
            if(queueConfig.getGroup().equals(queueGroup))
                initialQueuesInGroup = queueConfig.getInitialQueues();
        }

        if(initialQueuesInGroup == null) {
            throw new RuntimeException("No queue group matches group name: " + queueGroup);
        }

        SendToQueueGroup qg = new SendToQueueGroup();
        qg.setQueueGroup(queueGroup);
        qg.setInitialQueuesInGroup(initialQueuesInGroup);
        qg.setQueueGroupMode(queueGroupMode);

        return qg;
    }

    public String getQueueGroup() {
        return queueGroup;
    }

    public void setQueueGroup(String queueGroup) {
        this.queueGroup = queueGroup;
    }

    public QueueGroupMode getQueueGroupMode() {
        return queueGroupMode;
    }

    public void setQueueGroupMode(QueueGroupMode queueGroupMode) {
        this.queueGroupMode = queueGroupMode;
    }

    public List<String> getInitialQueuesInGroup() {
        return initialQueuesInGroup;
    }

    public void setInitialQueuesInGroup(List<String> initialQueuesInGroup) {
        this.initialQueuesInGroup = initialQueuesInGroup;
    }
}
