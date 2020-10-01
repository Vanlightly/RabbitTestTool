package com.rabbitmq.orchestrator.run;

import com.rabbitmq.orchestrator.deploy.BaseSystem;
import com.rabbitmq.orchestrator.deploy.ec2.model.EC2System;
import com.rabbitmq.orchestrator.model.actions.BrokerAction;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public interface BrokerActioner {
    void applyActionsIfAny(BaseSystem systems,
                           BrokerAction brokerAction,
                           AtomicBoolean isCancelled,
                           Set<String> failedSystems);
    void removeAnyAffects(BaseSystem systems,
                          BrokerAction brokerAction,
                          AtomicBoolean isCancelled,
                          Set<String> failedSystems);
}
