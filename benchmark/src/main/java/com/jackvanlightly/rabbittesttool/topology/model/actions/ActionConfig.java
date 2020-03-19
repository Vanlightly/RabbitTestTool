package com.jackvanlightly.rabbittesttool.topology.model.actions;

public class ActionConfig {
    ActionType actionType;
    ActionDelay actionDelay;

    public ActionConfig(ActionType actionType, ActionDelay actionDelay) {
        this.actionType = actionType;
        this.actionDelay = actionDelay;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public ActionDelay getActionDelay() {
        return actionDelay;
    }
}
