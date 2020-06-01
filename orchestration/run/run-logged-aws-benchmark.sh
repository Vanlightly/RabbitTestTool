#!/bin/bash

set -e

LAST_NODE=$(($1 + ${22} - 1))

INFLUX_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_metrics" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
echo "Will connect to InfluxDB at ip $INFLUX_IP"

BROKER_IPS=""
STREAM_PORTS=""
if [ "${29}" != "" ]; then
    BROKER_IPS="${29}"
else
    for NODE in $(seq $1 $LAST_NODE)
    do
        BROKER_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_${3}${NODE}_${18}" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)

        if [[ $NODE != $LAST_NODE ]]; then
            BROKER_IPS+="${BROKER_IP}:5672,"
            STREAM_PORTS+="5555,"
        else
            BROKER_IPS+="${BROKER_IP}:5672"
            STREAM_PORTS+="5555"
        fi
    done
fi

LOADGEN_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_loadgen_$3$1_${18}" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)

echo "Will connect to load gen server at ip $LOADGEN_IP"
echo "Will connect to $3 at ipS $BROKER_IPs"

ssh -i "~/.ssh/$2.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" ubuntu@$LOADGEN_IP java -Xms1024m -Xmx8192m -jar rabbittesttool-1.1-SNAPSHOT-jar-with-dependencies.jar \
--mode "${32}" \
--topology "topologies/${14}" \
--policies "policies/${24}" \
--run-id "${15}" \
--run-tag "${18}" \
--technology "$3" \
--nodes "${27}" \
--version "$4" \
--instance "$5" \
--volume "$6" \
--filesystem "$7" \
--hosting "$8" \
--tenancy "$9" \
--core-count "${19}" \
--threads-per-core "${20}" \
--no-tcp-delay "${23}" \
--config-tag "${21}" \
--metrics-influx-uri "http://$INFLUX_IP:8086" \
--metrics-influx-user metricsagent \
--metrics-influx-password "${10}" \
--metrics-influx-database metrics \
--metrics-influx-interval 10 \
--broker-hosts "$BROKER_IPS" \
--stream-ports "$STREAM_PORTS" \
--broker-mgmt-port 15672 \
--broker-user "${16}" \
--broker-password "${17}" \
--postgres-jdbc-url "${11}" \
--postgres-user "${12}" \
--postgres-pwd "${13}" \
--override-step-seconds "${25}" \
--override-step-repeat "${26}" \
--override-step-msg-limit "${28}" \
--pub-connect-to-node "${30}" \
--con-connect-to-node "${31}" \
--grace-period-sec "${33}" \
--warm-up-seconds "${34}" \
--checks "${35}" \
--zero-exit-code true \
--run-ordinal "${36}" \
--benchmark-tags "${37}" \
--benchmark-attempts "${38}" "${39}" "${40}" "${41}"