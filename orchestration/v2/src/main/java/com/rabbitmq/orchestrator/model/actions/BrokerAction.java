package com.rabbitmq.orchestrator.model.actions;

import com.rabbitmq.orchestrator.model.ActionType;

public class BrokerAction {
    ActionType actionType;
    ActionTrigger trigger;
    int triggerValue;
    TrafficControl trafficControl;

    public BrokerAction(ActionType actionType, ActionTrigger trigger, int triggerValue, TrafficControl trafficControl) {
        this.actionType = actionType;
        this.trigger = trigger;
        this.triggerValue = triggerValue;
        this.trafficControl = trafficControl;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public ActionTrigger getTrigger() {
        return trigger;
    }

    public int getTriggerValue() {
        return triggerValue;
    }

    public TrafficControl getTrafficControl() {
        return trafficControl;
    }
}
