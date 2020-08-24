#!/bin/bash

TARGET_IPS=$1
echo "TARGET_IPS=$TARGET_IPS"
DELAY=$2
echo "DELAY=$DELAY"
DELAY_JITTER=$3
echo "DELAY_JITTER=$DELAY_JITTER"
DELAY_DIST=$4
echo "DELAY_DIST=$DELAY_DIST"
BANDWIDTH=$5
echo "BANDWIDTH=$BANDWIDTH"
PACKET_LOSS_MODE=$6
echo "PACKET_LOSS_MODE=$PACKET_LOSS_MODE"
PACKET_LOSS_ARG1=$7
echo "PACKET_LOSS_ARG1=$PACKET_LOSS_ARG1"
PACKET_LOSS_ARG2=$8
echo "PACKET_LOSS_ARG2=$PACKET_LOSS_ARG2"
PACKET_LOSS_ARG3=$9
echo "PACKET_LOSS_ARG3=$PACKET_LOSS_ARG3"
PACKET_LOSS_ARG4=${10}
echo "PACKET_LOSS_ARG4=$PACKET_LOSS_ARG4"

#gemodel 1% 10% 70% 0.1%

tc qdisc del dev ens5 root > /dev/null 2>&1
tc qdisc add dev ens5 root handle 1: prio

IFS=","
for IP in $TARGET_IPS
do
    echo "CREATING FILTER FOR $IP"
    tc filter add dev ens5 protocol ip parent 1: prio 1 u32 match ip dst $IP flowid 1:1
done

tc filter add dev ens5 protocol all parent 1: prio 2 u32 match ip sport 22 0xffff flowid 1:2

# if we combine multiple network effects here they must be nested correctly
if [[ $DELAY != "0" || $PACKET_LOSS_ARG1 != "0%" ]]; then
    
    if [[ $DELAY != "0" && $PACKET_LOSS_ARG1 == "0%" ]]; then
        echo "Creating delay for outbound traffic to other brokers"
        tc qdisc add dev ens5 parent 1:1 handle 2: netem delay ${DELAY}ms ${DELAY_JITTER}ms distribution $DELAY_DIST
    elif [[ $DELAY == "0" && $PACKET_LOSS_ARG1 != "0%" ]]; then
        echo "Creating packet loss for outbound traffic to other brokers"
        if [[ $PACKET_LOSS_MODE == "gemodel" ]]; then
            tc qdisc add dev ens5 parent 1:1 handle 2: netem loss gemodel $PACKET_LOSS_ARG1 $PACKET_LOSS_ARG2 $PACKET_LOSS_ARG3 $PACKET_LOSS_ARG4
        elif [[ $PACKET_LOSS_MODE == "random" ]]; then
            tc qdisc add dev ens5 parent 1:1 handle 2: netem loss random $PACKET_LOSS_ARG1
        fi
    elif [[ $DELAY != "0" && $PACKET_LOSS_ARG1 != "0%" ]]; then
        echo "Creating delay and packet loss for outbound traffic to other brokers"
        tc qdisc add dev ens5 parent 1:1 handle 2: netem delay ${DELAY}ms ${DELAY_JITTER}ms distribution $DELAY_DIST loss gemodel $PACKET_LOSS_ARG1 $PACKET_LOSS_ARG2 $PACKET_LOSS_ARG3 $PACKET_LOSS_ARG4

        if [[ $PACKET_LOSS_MODE == "gemodel" ]]; then
            tc qdisc add dev ens5 parent 1:1 handle 2: netem delay ${DELAY}ms ${DELAY_JITTER}ms distribution $DELAY_DIST loss gemodel $PACKET_LOSS_ARG1 $PACKET_LOSS_ARG2 $PACKET_LOSS_ARG3 $PACKET_LOSS_ARG4
        elif [[ $PACKET_LOSS_MODE == "random" ]]; then
            tc qdisc add dev ens5 parent 1:1 handle 2: netem delay ${DELAY}ms ${DELAY_JITTER}ms distribution $DELAY_DIST loss random $PACKET_LOSS_ARG1
        fi
    fi

    if [[ $BANDWIDTH != "0" ]]; then
        echo "Creating bandwidth limit for outbound traffic to other brokers"
        tc qdisc add dev ens5 parent 2:1 handle 3: tbf rate ${BANDWIDTH}mbit burst 64kb latency 400ms
    fi
elif [[ $BANDWIDTH != "0" ]]; then
    echo "Creating bandwidth limit for outbound traffic to other brokers"
    tc qdisc add dev ens5 parent 1:1 handle 2: tbf rate ${BANDWIDTH}mbit burst 64kb latency 400ms
fi