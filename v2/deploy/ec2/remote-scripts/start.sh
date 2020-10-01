#!/bin/bash

if pgrep -f beam &> /dev/null 2>&1; then
    echo BROKER_ACTIONS: $(hostname) is running already
else
    echo BROKER_ACTIONS: $(hostname) starting
    /rabbitmq/sbin/rabbitmq-server start -detached
    sleep 5
    echo BROKER_ACTIONS: $(hostname) awaiting start
    /rabbitmq/sbin/rabbitmqctl await_startup
    sleep 5
    echo BROKER_ACTIONS: $(hostname) started
fi

