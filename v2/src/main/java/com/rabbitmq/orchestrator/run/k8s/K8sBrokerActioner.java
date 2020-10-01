package com.rabbitmq.orchestrator.run.k8s;

import com.rabbitmq.orchestrator.deploy.BaseSystem;
import com.rabbitmq.orchestrator.model.actions.BrokerAction;
import com.rabbitmq.orchestrator.run.BrokerActioner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class K8sBrokerActioner implements BrokerActioner {
    private static final Logger LOGGER = LoggerFactory.getLogger("K8S_BROKER_ACTIONS");

    @Override
    public void applyActionsIfAny(BaseSystem systems, BrokerAction brokerAction, AtomicBoolean isCancelled, Set<String> failedSystems) {
        LOGGER.warn("Apply broker actions: not implemented for K8s");
    }

    @Override
    public void removeAnyAffects(BaseSystem systems, BrokerAction brokerAction, AtomicBoolean isCancelled, Set<String> failedSystems) {
        LOGGER.warn("Remove any effects: not implemented for K8s");
    }
}
