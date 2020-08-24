#!/bin/bash

PASSWORD=$1
POSTGRES_USER=$2
POSTGRES_PASSWORD=$3
INFLUX_SUBPATH=$4

instance=rtt
RMQ_USER=$(kubectl get secret ${instance}-rabbitmq-admin -o jsonpath="{.data.username}" | base64 --decode)
RMQ_PASSWORD=$(kubectl get secret ${instance}-rabbitmq-admin -o jsonpath="{.data.password}" | base64 --decode)
service=${instance}-rabbitmq-ingress

INFLUX_IP=$(aws ec2 describe-instances --filters "Name=tag:inventorygroup,Values=benchmarking_metrics" --query "Reservations[*].Instances[*].PublicIpAddress" --output=text)
INFLUX_URL="http://$INFLUX_IP/$INFLUX_SUBPATH"
echo $INFLUX_URL

EXTERNAL_IP=""
while [ -z $EXTERNAL_IP ]; do
  EXTERNAL_IP=$(kubectl get svc rtt-rabbitmq-client --template="{{range .status.loadBalancer.ingress}}{{.hostname}}{{end}}")
  [ -z "$EXTERNAL_IP" ] && sleep 2
done
echo "End point ready: $EXTERNAL_IP"

echo "Discovering pods..."
PODS=$(kubectl get pods | grep rabbit | awk '{print $1}')
BROKER_IPS=""
while IFS= read -r POD; do
    BROKER_IP=$(kubectl get pod ${POD} -o jsonpath="{.status.podIP}")
    BROKER_IPS+="${BROKER_IP}:5672,"
    echo "$POD"
done <<< "$PODS"

BROKER_IPS=${BROKER_IPS%?}
echo "Broker IPS: $BROKER_IPS"

#docker run jackvanlightly/rtt:1.1.1 \
kubectl run rtt --image=jackvanlightly/rtt:1.1.2 --restart=Never -- --mode benchmark --topology topologies/point-to-point/point-to-point-safe.json --version 3.8.5 --run-tag 12345 --instance standard --volume gp2 --filesystem ? --hosting eks --tenancy ? --core-count 1 --threads-per-core 2 --no-tcp-delay false --config-tag c1 --metrics-influx-uri "$INFLUX_URL" --metrics-influx-user metricsagent --metrics-influx-password $PASSWORD --metrics-influx-database metrics --metrics-influx-interval 10 --broker-hosts "$BROKER_IPS" --broker-mgmt-port 15672 --broker-user "$RMQ_USER" --broker-password "$RMQ_PASSWORD" --postgres-jdbc-url jdbc:postgresql://manny.db.elephantsql.com:5432/ --postgres-user "$POSTGRES_USER" --postgres-pwd "$POSTGRES_PASSWORD" --run-ordinal 1 --override-step-seconds 300