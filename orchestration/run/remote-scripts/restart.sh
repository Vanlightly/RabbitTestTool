#!/bin/bash

if pgrep -f beam &> /dev/null 2>&1; then
    echo BROKER_ACTIONS: $(hostname) running, will stop now
    /rabbitmq/sbin/rabbitmqctl stop

    while [ pgrep -f beam &> /dev/null 2>&1 ]
    do
        echo BROKER_ACTIONS: $(hostname) waiting to stop
        sleep 5
    done

    echo BROKER_ACTIONS: $(hostname) stopped
    sleep 5
else
    echo BROKER_ACTIONS: $(hostname) already stopped
fi

echo BROKER_ACTIONS: $(hostname) starting
/rabbitmq/sbin/rabbitmq-server start -detached
sleep 5
echo BROKER_ACTIONS: $(hostname) awaiting start
/rabbitmq/sbin/rabbitmqctl await_startup
sleep 5
echo BROKER_ACTIONS: $(hostname) started