package com.jackvanlightly.rabbittesttool.topology.model.actions;

public class ActionDelay {
    ActionStartDelayType actionStartDelayType;
    int actionLowerStartValue;
    int actionUpperStartValue;

    public ActionStartDelayType getActionStartDelayType() {
        return actionStartDelayType;
    }

    public void setActionStartDelayType(ActionStartDelayType actionStartDelayType) {
        this.actionStartDelayType = actionStartDelayType;
    }

    public int getActionLowerStartValue() {
        return actionLowerStartValue;
    }

    public void setActionLowerStartValue(int actionLowerStartValue) {
        this.actionLowerStartValue = actionLowerStartValue;
    }

    public int getActionUpperStartValue() {
        return actionUpperStartValue;
    }

    public void setActionUpperStartValue(int actionUpperStartValue) {
        this.actionUpperStartValue = actionUpperStartValue;
    }
}
