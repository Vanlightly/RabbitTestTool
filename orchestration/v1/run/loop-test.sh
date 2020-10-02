#!/bin/bash

BROKER_IPS=""
for NODE in $(seq 1 3)
do
    BROKER_IP="rabbitmq$NODE"

    if [[ $NODE != 3 ]]; then
        BROKER_IPS+="${BROKER_IP},"
    else
        BROKER_IPS+="${BROKER_IP}"
    fi
done

echo $BROKER_IPS