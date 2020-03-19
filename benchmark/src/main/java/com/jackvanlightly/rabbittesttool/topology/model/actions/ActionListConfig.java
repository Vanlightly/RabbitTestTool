package com.jackvanlightly.rabbittesttool.topology.model.actions;

import java.util.ArrayList;
import java.util.List;

public class ActionListConfig {
    ActionCategory actionCategory;
    ActionListExecution actionListExecution;
    ExecuteMode executeMode;
    ActionDelay actionListDelay;
    List<ActionConfig> actions;


    public ActionListConfig(ActionCategory actionCategory) {
        this.actionCategory = actionCategory;
        actions = new ArrayList<>();
    }

    public ActionCategory getActionCategory() {
        return actionCategory;
    }

    public void setActionCategory(ActionCategory actionCategory) {
        this.actionCategory = actionCategory;
    }

    public ActionListExecution getActionListExecution() {
        return actionListExecution;
    }

    public List<ActionConfig> getActions() {
        return actions;
    }

    public void setActionListExecution(ActionListExecution actionListExecution) {
        this.actionListExecution = actionListExecution;
    }

    public void setActions(List<ActionConfig> actions) {
        this.actions = actions;
    }

    public ActionDelay getActionListDelay() {
        return actionListDelay;
    }

    public void setActionListDelay(ActionDelay actionListDelay) {
        this.actionListDelay = actionListDelay;
    }

    public ExecuteMode getExecuteMode() {
        return executeMode;
    }

    public void setExecuteMode(ExecuteMode executeMode) {
        this.executeMode = executeMode;
    }
}
