#!/bin/bash

if pgrep -f beam &> /dev/null 2>&1; then
    /rabbitmq/sbin/rabbitmqctl stop
    sleep 5
    echo BROKER_ACTIONS: RabbitMQ stopped
fi

/rabbitmq/sbin/rabbitmq-server start -detached
sleep 5
echo BROKER_ACTIONS: RabbitMQ started