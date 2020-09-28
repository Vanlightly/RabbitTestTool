#!/bin/bash

VARIABLES_FILE=$1
source $VARIABLES_FILE

set -e

INFLUX_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_metrics" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
INFLUX_URL="http://$INFLUX_IP/$INFLUX_SUBPATH"
echo "Will connect to InfluxDB at ip $INFLUX_IP"

echo "Discovering pods..."

if [[ $KUBERNETES_ENGINE == "eks" ]]; then
  export AWS_PROFILE=benchmarking
fi

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

if [[ $(kubectl --context ${K_CONTEXT} get pods | awk '{ print $1 }' | grep ^main-load$ | wc -l) != "0" ]]
then
    echo "Deleting existing main-load pod"
    kubectl --context ${K_CONTEXT} delete pod main-load
    sleep 10
fi

RMQ_USER=$(kubectl --context ${K_CONTEXT} get secret $RABBITMQ_CLUSTER_NAME-rabbitmq-admin -o jsonpath="{.data.username}" | base64 --decode)
RMQ_PASS=$(kubectl --context ${K_CONTEXT} get secret $RABBITMQ_CLUSTER_NAME-rabbitmq-admin -o jsonpath="{.data.password}" | base64 --decode)

CPU=$(( ($VCPU_COUNT - 1 ) * 1000 ))
REMAINDER=$(( $MEMORY_GB / 5 ))
MEMORY=$(( ($MEMORY_GB - $REMAINDER) * 1000 ))
CORES=$(( $VCPU_COUNT/2 ))

LIMITS="--limits=cpu=${CPU}m,memory=${MEMORY}Mi"
echo "kubectl --context ${K_CONTEXT} run main-load ${LIMITS} --image=jackvanlightly/rtt:1.1.26 --restart=Never --"

kubectl --context ${K_CONTEXT} run main-load ${LIMITS} --image=jackvanlightly/rtt:1.1.26 --restart=Never -- \
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
--core-count "$CORES" \
--threads-per-core 2 \
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



STATUS=$(kubectl --context ${K_CONTEXT} get pods main-load | grep main-load | awk '{ print $3}')

while [[ $STATUS != "Running" ]]
do
  echo "Waiting for benchmark to start"
  sleep 5
  STATUS=$(kubectl --context ${K_CONTEXT} get pods main-load | grep main-load | awk '{ print $3}')
done

kubectl --context ${K_CONTEXT} logs -f main-load

echo "Benchmark completed"