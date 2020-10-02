package com.rabbitmq.orchestrator.run.ec2;

public class BrokerActionsException extends RuntimeException {
    public BrokerActionsException(String message) {
        super(message);
    }

    public BrokerActionsException(String message, Throwable cause) {
        super(message, cause);
    }
}

