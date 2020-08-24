#!/bin/bash

NODE_NUMBER=$1
KEY_PAIR=$2
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
NODES=${27} #remove
OVERRIDE_STEP_MSG_LIMIT=${28}
OVERRIDE_BROKER_HOSTS=${29}
PUB_CONNECT_TO_NODE=${30}
CON_CONNECT_TO_NODE=${31}
MODE=${32}
GRACE_PERIOD_SEC=${33}
WARMUPSECONDS=${34}
CHECKS=${35}
RUN_ORDINAL=${36}
TAGS=${37}
ATTEMPTS=${38}
INFLUX_SUBPATH=${39}
TOPOLOGY_VARIABLES=${40}
POLICY_VARIABLES=${41}
FEDERATION_ARGS=${42}

set -e

INFLUX_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_metrics" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
INFLUX_URL="http://$INFLUX_IP/$INFLUX_SUBPATH"
echo "Will connect to InfluxDB at ip $INFLUX_IP"

echo "Discovering pods..."
PODS=$(kubectl get pods | grep rabbit | awk '{print $1}')
BROKER_IPS=""
STREAM_PORTS=""
while IFS= read -r POD; do
    BROKER_IP=$(kubectl get pod ${POD} -o jsonpath="{.status.podIP}")
    BROKER_IPS+="${BROKER_IP}:5672,"
    STREAM_PORTS+="5555,"
    echo "$POD"
done <<< "$PODS"

BROKER_IPS=${BROKER_IPS%?}
STREAM_PORTS=${STREAM_PORTS%?}
echo "Broker IPS: $BROKER_IPS"

if [[ $(kubectl get pods | awk '{ print $1 }' | grep ^rtt$ | wc -l) != "0" ]]
then
    echo "Deleting existing rtt pod"
    kubectl delete pod rtt
    sleep 10
fi

RMQ_USER=$(kubectl get secret rtt-rabbitmq-admin -o jsonpath="{.data.username}" | base64 --decode)
RMQ_PASS=$(kubectl get secret rtt-rabbitmq-admin -o jsonpath="{.data.password}" | base64 --decode)

kubectl run rtt --image=jackvanlightly/rtt:1.1.12 --restart=Never -- \
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
--grace-period-sec "$GRACE_PERIOD_SEC" \
--warm-up-seconds "$WARMUPSECONDS" \
--checks "$CHECKS" \
--zero-exit-code true \
--run-ordinal "$RUN_ORDINAL" \
--benchmark-tags "$TAGS" \
--benchmark-attempts "$ATTEMPTS" "$TOPOLOGY_VARIABLES" "$POLICY_VARIABLES" "$FEDERATION_ARGS"


STATUS=$(kubectl get pods rtt | grep rtt | awk '{ print $3}')

while [[ $STATUS != "Running" ]]
do
  echo "Waiting for benchmark to start"
  sleep 5
  STATUS=$(kubectl get pods rtt | grep rtt | awk '{ print $3}')
done

kubectl logs -f rtt

echo "Benchmark completed"