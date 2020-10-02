#!/bin/bash

VARIABLES_FILE=$1
source $VARIABLES_FILE

set -e

INFLUX_IP=$(aws ec2 describe-instances --profile benchmarking --filters "Name=tag:inventorygroup,Values=benchmarking_metrics" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
INFLUX_URL="http://$INFLUX_IP/$INFLUX_SUBPATH"
echo "Will connect to InfluxDB at ip $INFLUX_IP"

if [[ $KUBERNETES_ENGINE == "eks" ]]; then
  echo "Configuring credentials for EKS workload"
  export AWS_PROFILE=benchmarking
fi

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

if [[ $(kubectl --context ${K_CONTEXT} get pods | awk '{ print $1 }' | grep ^bg-load$ | wc -l) != "0" ]]
then
    echo "Deleting existing bg-load pod"
    kubectl --context ${K_CONTEXT} delete pod bg-load
    sleep 10
fi

RMQ_USER=$(kubectl --context ${K_CONTEXT} get secret $RABBITMQ_CLUSTER_NAME-rabbitmq-admin -o jsonpath="{.data.username}" | base64 --decode)
RMQ_PASS=$(kubectl --context ${K_CONTEXT} get secret $RABBITMQ_CLUSTER_NAME-rabbitmq-admin -o jsonpath="{.data.password}" | base64 --decode)

CPU=$(( $VCPU_COUNT - 1 ) * 1000 ))
REMAINDER=$(( $MEMORY_GB / 5 ))
MEMORY=$(( ($MEMORY_GB - $REMAINDER) * 1000 ))

LIMITS="--limits=cpu=${CPU}m,memory=${MEMORY}Mi"
echo "kubectl --context ${K_CONTEXT} run bg-load ${LIMITS} --image=jackvanlightly/rtt:1.1.26 --restart=Never --"

kubectl --context ${K_CONTEXT} run bg-load ${LIMITS} --image=jackvanlightly/rtt:1.1.26 --restart=Never -- \
--mode benchmark \
--topology "topologies/${TOPOLOGY}" \
--policies "policies/${POLICY}" \
--technology "$TECHNOLOGY" \
--version "$VERSION" \
--broker-hosts "$BROKER_IPS" \
--stream-ports "$STREAM_PORTS" \
--broker-mgmt-port 15672 \
--broker-user "$RMQ_USER" \
--broker-password "$RMQ_PASS" \
--override-step-seconds "$OVERRIDE_STEP_SECONDS" \
--override-step-repeat "$OVERRIDE_STEP_REPEAT" \
--print-live-stats false

#&> /dev/null

echo "Background load completed on instance at ip $LOADGEN_IP against cluster with ips $BROKER_IPs"