package com.jackvanlightly.rabbittesttool.topology.model.actions;

import java.util.List;

public class QueueActionListConfig extends ActionListConfig {
    List<String> queueNames;

    public QueueActionListConfig(List<String> queueNames) {
        super(ActionCategory.Queue);
        this.queueNames = queueNames;
    }

    public List<String> getQueueNames() {
        return queueNames;
    }
}
