#!/bin/bash

VARIABLES_FILE=$1
source $VARIABLES_FILE

echo "Variables from $VARIABLES_FILE"

set -e

INFLUX_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_metrics" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
INFLUX_URL="http://$INFLUX_IP/$INFLUX_SUBPATH"

echo "Discovering pods... for $RABBITMQ_CLUSTER_NAME"

if [[ $KUBERNETES_ENGINE == "eks" ]]; then
  echo "Configuring credentials for EKS workload"
  export AWS_PROFILE=benchmarking
fi

PODS=$(kubectl --context ${K_CONTEXT} get pods | grep rabbit | awk '{print $1}')
BROKER_IPS=""
STREAM_PORTS=""
while IFS= read -r POD; do
    BROKER_IP=$(kubectl --context ${K_CONTEXT} get pod ${POD} -o jsonpath="{.status.podIP}")
    
    if [[ $BROKER_IP == "" ]]; then
      while [[ $BROKER_IP == "" ]]; do
        echo "Broker IP for $POD is blank, retrying in 10 seconds"
        sleep 10
        BROKER_IP=$(kubectl --context ${K_CONTEXT} get pod ${POD} -o jsonpath="{.status.podIP}")
      done
    fi

    BROKER_IPS+="${BROKER_IP}:5672,"
    STREAM_PORTS+="5555,"
done <<< "$PODS"

BROKER_IPS=${BROKER_IPS%?}
STREAM_PORTS=${STREAM_PORTS%?}
echo "Broker IPS: $BROKER_IPS for $RABBITMQ_CLUSTER_NAME"

if [[ $(kubectl --context ${K_CONTEXT} get pods | awk '{ print $1 }' | grep ^main-load$ | wc -l) != "0" ]]
then
    echo "Deleting existing main-load pod for $RABBITMQ_CLUSTER_NAME"
    kubectl --context ${K_CONTEXT} delete pod main-load
    sleep 10
fi

RMQ_USER=$(kubectl --context ${K_CONTEXT} get secret $RABBITMQ_CLUSTER_NAME-rabbitmq-admin -o jsonpath="{.data.username}" | base64 --decode)
RMQ_PASS=$(kubectl --context ${K_CONTEXT} get secret $RABBITMQ_CLUSTER_NAME-rabbitmq-admin -o jsonpath="{.data.password}" | base64 --decode)

LIMITS="--limits=cpu=${CPU_LIMIT}m,memory=${MEMORY_LIMIT}Mi"
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
--core-count "$VCPU_COUNT" \
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

while [[ $STATUS != "Running" && $STATUS != "Error" ]]
do
  echo "Waiting for $RABBITMQ_CLUSTER_NAME benchmark to start"
  sleep 5
  STATUS=$(kubectl --context ${K_CONTEXT} get pods main-load | grep main-load | awk '{ print $3}')
done

if [[ $STATUS == "Error" ]]; then
  kubectl --context ${K_CONTEXT} logs main-load
  echo "Benchmark $RABBITMQ_CLUSTER_NAME failed"
else
  kubectl --context ${K_CONTEXT} logs -f main-load
  echo "Benchmark $RABBITMQ_CLUSTER_NAME completed"
fi