package com.jackvanlightly.rabbittesttool.topology.model.actions;

import java.util.List;

public class QueueFillActionConfig extends ActionConfig {
    int messageSize;
    int messageCount;

    public QueueFillActionConfig(ActionDelay actionDelay, int messageSize, int messageCount) {
        super(ActionType.QueueFill, actionDelay);
        this.messageSize = messageSize;
        this.messageCount = messageCount;
    }

    public int getMessageSize() {
        return messageSize;
    }

    public int getMessageCount() {
        return messageCount;
    }
}
