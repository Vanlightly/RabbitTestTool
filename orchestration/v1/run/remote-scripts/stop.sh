#!/bin/bash

if pgrep -f beam &> /dev/null 2>&1; then
    kill -9 $(pgrep beam)
    sleep 5
    echo BROKER_ACTIONS: RabbitMQ killed
else
    echo BROKER_ACTIONS: RabbitMQNo action taken, broker already down
fi