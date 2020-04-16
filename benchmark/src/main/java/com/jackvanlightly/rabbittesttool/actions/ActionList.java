package com.jackvanlightly.rabbittesttool.actions;

import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.topology.model.actions.ActionListConfig;

public class ActionList {
    ConnectionSettings connectionSettings;
    ActionListConfig actionListConfig;

    public ActionList(ConnectionSettings connectionSettings, ActionListConfig actionListConfig) {
        this.connectionSettings = connectionSettings;
        this.actionListConfig = actionListConfig;
    }

    public boolean hasActions() {
        return getConfig().getExecuteMode() != null;
    }

    public ConnectionSettings getConnectionSettings() {
        return connectionSettings;
    }

    public void setConnectionSettings(ConnectionSettings connectionSettings) {
        this.connectionSettings = connectionSettings;
    }

    public ActionListConfig getConfig() {
        return actionListConfig;
    }

    public void setActionListConfig(ActionListConfig actionListConfig) {
        this.actionListConfig = actionListConfig;
    }
}
