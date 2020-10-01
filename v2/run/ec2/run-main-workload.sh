#!/bin/bash

VARIABLES_FILE=$1
source $VARIABLES_FILE

set -e

LAST_NODE=$(($NODE_NUMBER + $CLUSTER_SIZE - 1))

INFLUX_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_metrics" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
INFLUX_URL="http://$INFLUX_IP/$INFLUX_SUBPATH"
echo "Will connect to InfluxDB at ip $INFLUX_IP"


BROKER_IPS=""
STREAM_PORTS=""
if [ "$OVERRIDE_BROKER_HOSTS" != "" ]; then
    echo "Using overridden broker IPs $OVERRIDE_BROKER_HOSTS instead of obtaining IPs via tags"
    BROKER_IPS="$OVERRIDE_BROKER_HOSTS"
else
    for NODE in $(seq $NODE_NUMBER $LAST_NODE)
    do
        BROKER_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_${TECHNOLOGY}${NODE}_${RUN_TAG}" --query "Reservations[*].Instances[*].PrivateIpAddress" --output=text)
        echo "Obtained IP $BROKER_IP for instance with inventorygroup tag: benchmarking_${TECHNOLOGY}${NODE}_${RUN_TAG}"

        if [[ $NODE != $LAST_NODE ]]; then
            BROKER_IPS+="${BROKER_IP}:5672,"
            STREAM_PORTS+="5555,"
        else
            BROKER_IPS+="${BROKER_IP}:5672"
            STREAM_PORTS+="5555"
        fi
    done
fi

LOADGEN_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_loadgen_${TECHNOLOGY}${NODE_NUMBER}_${RUN_TAG}" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)

echo "Will connect to load gen server at ip $LOADGEN_IP"
echo "Will connect to $TECHNOLOGY at ips $BROKER_IPS"

ssh -i "~/.ssh/$KEY_PAIR.pem" -o "StrictHostKeyChecking=no" -o "UserKnownHostsFile=/dev/null" ubuntu@$LOADGEN_IP java -Xms1024m -Xmx8192m -jar rabbittesttool-1.1-SNAPSHOT-jar-with-dependencies.jar \
--mode "$MODE" \
--topology "topologies/$TOPOLOGY" \
--policies "policies/$POLICIES" \
--run-id "$RUN_ID" \
--run-tag "$RUN_TAG" \
--technology "$TECHNOLOGY" \
--version "$BROKER_VERSION" \
--instance "$INSTANCE" \
--volume "$VOLUME1_TYPE" \
--filesystem "$FILESYSTEM" \
--hosting "$HOSTING" \
--tenancy "$TENANCY" \
--core-count "$CORE_COUNT" \
--threads-per-core "$THREADS_PER_CORE" \
--no-tcp-delay "$NO_TCP_DELAY" \
--config-tag "$CONFIG_TAG" \
--metrics-influx-uri "$INFLUX_URL" \
--metrics-influx-user metricsagent \
--metrics-influx-password "$PASSWORD" \
--metrics-influx-database metrics \
--metrics-influx-interval 10 \
--broker-hosts "$BROKER_IPS" \
--stream-ports "$STREAM_PORTS" \
--broker-mgmt-port 15672 \
--broker-user "$USERNAME" \
--broker-password "$PASSWORD" \
--postgres-jdbc-url "$POSTGRES_URL" \
--postgres-user "$POSTGRES_USER" \
--postgres-pwd "$POSTGRES_PWD" \
--override-step-seconds "$OVERRIDE_STEP_SECONDS" \
--override-step-repeat "$OVERRIDE_STEP_REPEAT" \
--override-step-msg-limit "$OVERRIDE_STEP_MSG_LIMIT" \
--pub-connect-to-node "$PUB_CONNECT_TO_NODE" \
--con-connect-to-node "$CON_CONNECT_TO_NODE" \
--pub-heartbeat-sec "$PUB_HEARTBEAT_SEC" \
--con-heartbeat-sec "$CON_HEARTBEAT_SEC" \
--grace-period-sec "$GRACE_PERIOD_SEC" \
--warm-up-seconds "$WARMUPSECONDS" \
--checks "$CHECKS" \
--zero-exit-code true \
--run-ordinal "$RUN_ORDINAL" \
--benchmark-tags "$TAGS" \
--benchmark-attempts "$ATTEMPTS" "$TOPOLOGY_VARIABLES" "$POLICY_VARIABLES" "$FEDERATION_ARGS"