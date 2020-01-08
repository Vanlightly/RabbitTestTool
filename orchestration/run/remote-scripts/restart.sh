#!/bin/bash

/rabbitmq/sbin/rabbitmqctl stop
sleep 5
echo BROKER_ACTIONS: RabbitMQ stopped
/rabbitmq/sbin/rabbitmq-server start -detached
sleep 5
echo BROKER_ACTIONS: RabbitMQ started