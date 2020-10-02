#!/bin/bash

TECHNOLOGY=$3
BROKER_VERSION=$4
INSTANCE=$5
VOLUME1_TYPE=$6
FILESYSTEM=$7
HOSTING=$8
TENANCY=$9
PASSWORD=${10}
POSTGRES_URL=${11}
POSTGRES_USER=${12}
POSTGRES_PWD=${13}
TOPOLOGY=${14}
RUN_ID=${15}
USERNAME=${16}
PASSWORD=${17}
RUN_TAG=${18}
CORE_COUNT=${19}
THREADS_PER_CORE=${20}
CONFIG_TAG=${21}
CLUSTER_SIZE=${22}
NO_TCP_DELAY=${23}
POLICIES=${24}
OVERRIDE_STEP_SECONDS=${25}
OVERRIDE_STEP_REPEAT=${26}
OVERRIDE_STEP_MSG_LIMIT=${27}
OVERRIDE_BROKER_HOSTS=${28}
PUB_CONNECT_TO_NODE=${29}
CON_CONNECT_TO_NODE=${30}
PUB_HEARTBEAT_SEC=${31}
CON_HEARTBEAT_SEC=${32}
MODE=${33}
GRACE_PERIOD_SEC=${34}
WARMUPSECONDS=${35}
CHECKS=${36}
RUN_ORDINAL=${37}
TAGS=${38}
ATTEMPTS=${39}
INFLUX_SUBPATH=${40}
TOPOLOGY_VARIABLES=${41}
POLICY_VARIABLES=${42}
K_CONTEXT=${44}
RABBITMQ_CLUSTER_NAME=${45}
MEMORY_GB=${46}

set -e

INFLUX_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_metrics" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
INFLUX_URL="http://$INFLUX_IP/$INFLUX_SUBPATH"
echo "Will connect to InfluxDB at ip $INFLUX_IP"

echo "Discovering pods..."

PODS=$(kubectl --context ${K_CONTEXT} get pods | grep rabbit | awk '{print $1}')
BROKER_IPS=""
STREAM_PORTS=""
while IFS= read -r POD; do
    BROKER_IP=$(kubectl --context ${K_CONTEXT} get pod ${POD} -o jsonpath="{.status.podIP}")
    BROKER_IPS+="${BROKER_IP}:5672,"
    STREAM_PORTS+="5555,"
    echo "$POD"
done <<< "$PODS"

BROKER_IPS=${BROKER_IPS%?}
STREAM_PORTS=${STREAM_PORTS%?}
echo "Broker IPS: $BROKER_IPS"

if [[ $(kubectl --context ${K_CONTEXT} get pods | awk '{ print $1 }' | grep ^rtt$ | wc -l) != "0" ]]
then
    echo "Deleting existing rtt pod"
    kubectl --context ${K_CONTEXT} delete pod rtt
    sleep 10
fi

RMQ_USER=$(kubectl --context ${K_CONTEXT} get secret $RABBITMQ_CLUSTER_NAME-rabbitmq-admin -o jsonpath="{.data.username}" | base64 --decode)
RMQ_PASS=$(kubectl --context ${K_CONTEXT} get secret $RABBITMQ_CLUSTER_NAME-rabbitmq-admin -o jsonpath="{.data.password}" | base64 --decode)

CPU=$(( (($CORE_COUNT * $THREADS_PER_CORE) - 1 ) * 1000 ))
REMAINDER=$(( $MEMORY_GB / 5 ))
MEMORY=$(( ($MEMORY_GB - $REMAINDER) * 1000 ))

LIMITS="--limits=cpu=${CPU}m,memory=${MEMORY}Mi"
echo "kubectl --context ${K_CONTEXT} run rtt ${LIMITS} --image=jackvanlightly/rtt:1.1.26 --restart=Never --"

kubectl --context ${K_CONTEXT} run rtt ${LIMITS} --image=jackvanlightly/rtt:1.1.26 --restart=Never -- \
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
--broker-user "$RMQ_USER" \
--broker-password "$RMQ_PASS" \
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
--benchmark-attempts "$ATTEMPTS" "$TOPOLOGY_VARIABLES" "$POLICY_VARIABLES"


STATUS=$(kubectl --context ${K_CONTEXT} get pods rtt | grep rtt | awk '{ print $3}')

while [[ $STATUS != "Running" ]]
do
  echo "Waiting for benchmark to start"
  sleep 5
  STATUS=$(kubectl --context ${K_CONTEXT} get pods rtt | grep rtt | awk '{ print $3}')
done

kubectl --context ${K_CONTEXT} logs -f rtt

echo "Benchmark completed"