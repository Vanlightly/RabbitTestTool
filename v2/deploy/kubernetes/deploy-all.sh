#!bin/bash

set -e

deploy_eks_cluster() {
  echo "Deploying k8s cluster to EKS"

  K_CLUSTER_SIZE=$(( $BROKERS + 1 ))

  echo "eksctl create cluster"
  echo "  --name $KUBE_CLUSTER_NAME"
  echo "  --version $K_VERSION"
  echo "  --region $REGION_OR_ZONES"
  echo "  --nodegroup-name standard-workers"
  echo "  --node-type $INSTANCE_TYPE"
  echo "  --nodes $K_CLUSTER_SIZE"
  echo "  --nodes-min 1"
  echo "  --nodes-max $K_CLUSTER_SIZE"
  echo "  --ssh-access"
  echo "  --ssh-public-key ~/.ssh/benchmarking.pub"
  echo "  --managed"

  eksctl create cluster \
    --name $KUBE_CLUSTER_NAME \
    --version $K_VERSION \
    --region $REGION_OR_ZONES \
    --nodegroup-name standard-workers \
    --node-type $INSTANCE_TYPE \
    --nodes $K_CLUSTER_SIZE \
    --nodes-min 1 \
    --nodes-max $K_CLUSTER_SIZE \
    --ssh-access \
    --ssh-public-key ~/.ssh/benchmarking.pub \
    --managed
}

create_eks_storage_classes() {
  echo "Creating storage classes"
  kubectl --context ${K_CONTEXT} create -f eks/io1.yaml
  kubectl --context ${K_CONTEXT} create -f eks/st1.yaml
}

delete_eks_cluster() {
  echo "Deleting k8s cluster on EKS"
  eksctl delete cluster --name $KUBE_CLUSTER_NAME
}

deploy_gke_cluster() {
  echo "Deploying k8s cluster to GKE"
  local NODES_PER_ZONE=$(( ($BROKERS / 3) + 1 ))
  
  echo "gcloud container clusters create $KUBE_CLUSTER_NAME"
  echo "--cluster-version $K_VERSION"
  echo "--num-nodes $NODES_PER_ZONE"
  echo "--zone $FIRST_ZONE"
  echo "--node-locations $REGION_OR_ZONES"
  echo "--machine-type $INSTANCE_TYPE"

  gcloud container clusters create $KUBE_CLUSTER_NAME \
  --cluster-version $K_VERSION \
  --num-nodes $NODES_PER_ZONE \
  --zone $FIRST_ZONE \
  --node-locations $REGION_OR_ZONES \
  --machine-type $INSTANCE_TYPE

  gcloud container clusters get-credentials $KUBE_CLUSTER_NAME
}

create_gke_storage_classes() {
  echo "Creating storage classes"
  kubectl --context ${K_CONTEXT} create -f gke/pd-ssd.yaml
}

delete_gke_storage_classes() {
  echo "Deleting k8s cluster on GKE"
  gcloud container clusters delete $KUBE_CLUSTER_NAME
}

deploy_operator() {
  echo "Deploying RabbitMQ cluster operator"
  kubectl --context ${K_CONTEXT} apply -f manifests/operator/namespace.yaml
  kubectl --context ${K_CONTEXT} apply -f manifests/operator/rbac.yaml
  kubectl --context ${K_CONTEXT} apply -f manifests/operator/crd.yaml
  kubectl --context ${K_CONTEXT} apply -f manifests/operator/deployment.yaml
}

deploy_rabbitmq() {
  echo ""
  echo "Deploying RabbitMQ cluster"
  kubectl --context ${K_CONTEXT} apply -f $MANIFEST_FILE
}

wait_for_cluster() {
  STATUS=$(kubectl --context ${K_CONTEXT} get statefulsets | grep $RABBITMQ_CLUSTER_NAME | awk '{ print $2 }')

  while [[ $STATUS != "$BROKERS/$BROKERS" ]]
  do
      echo "Cluster $KUBE_CLUSTER_NAME not ready yet. Actual: $STATUS, expected: $BROKERS/$BROKERS"
      sleep 5
      STATUS=$(kubectl --context ${K_CONTEXT} get statefulsets | grep $RABBITMQ_CLUSTER_NAME | awk '{ print $2 }')
  done
}

deploy_telegraf() {
  echo "Deploying Telegraf"
  kubectl --context ${K_CONTEXT} apply -f manifests/telegraf/.variables/influx-secret.yaml

  PODS=$(kubectl --context ${K_CONTEXT} get pods | grep rabbit | awk '{print $1}')
  COUNTER=0
  while IFS= read -r POD; do
      BROKER_IP=$(kubectl --context ${K_CONTEXT} get pod ${POD} -o jsonpath="{.status.podIP}")
      BROKER_NAME="rabbit@${POD}.${RABBITMQ_CLUSTER_NAME}-rabbitmq-headless.default"
      BROKER_URL="\"http://${BROKER_IP}:15692/metrics\""

      CONFIG_MANIFEST_FILE="manifests/telegraf/telegraf-config-${RABBITMQ_CLUSTER_NAME}-generated.yaml"
      cp manifests/telegraf/telegraf-config-template.yaml $CONFIG_MANIFEST_FILE
      sed -i "s#RABBITMQ_SCRAPE_URLS#$BROKER_URL#g" $CONFIG_MANIFEST_FILE
      sed -i "s#BROKER_NAME#$BROKER_NAME#g" $CONFIG_MANIFEST_FILE
      sed -i "s#ORDINAL#$COUNTER#g" $CONFIG_MANIFEST_FILE

      DEPLOYMENT_MANIFEST_FILE="manifests/telegraf/telegraf-deployment-${RABBITMQ_CLUSTER_NAME}-generated.yaml"
      cp manifests/telegraf/telegraf-deployment-template.yaml $DEPLOYMENT_MANIFEST_FILE
      sed -i "s#ORDINAL#$COUNTER#g" $DEPLOYMENT_MANIFEST_FILE

      echo "Applying manifests for $POD" $DEPLOYMENT_MANIFEST_FILE
      kubectl --context ${K_CONTEXT} apply -f ./$CONFIG_MANIFEST_FILE
      kubectl --context ${K_CONTEXT} apply -f ./$DEPLOYMENT_MANIFEST_FILE

      COUNTER=$((COUNTER + 1))
  done <<< "$PODS"
}

while getopts ":n:N:i:b:m:k:l:v:u:c:z:" opt; do
  case $opt in
    n) KUBE_CLUSTER_NAME="$OPTARG"
    ;;
    N) RABBITMQ_CLUSTER_NAME="$OPTARG"
    ;;
    i) INSTANCE_TYPE="$OPTARG"
    ;;
    b) BROKERS="$OPTARG"
    ;;
    m) MANIFEST_FILE="$OPTARG"
    ;;
    k) KUBERNETES_ENGINE="$OPTARG"
    ;;
    v) K_VERSION="$OPTARG"
    ;;
    u) K_USER_OR_PROJECT="$OPTARG"
    ;;
    c) K_CONTEXT="$OPTARG"
    ;;
    z) REGION_OR_ZONES="$OPTARG"
    ;;
    \?) echo "Invalid option -$OPTARG" >&2
    ;;
  esac
done


if [[ $KUBERNETES_ENGINE == "eks" ]]; then
  export AWS_PROFILE=benchmarking
  deploy_eks_cluster
  sleep 5
  create_eks_storage_classes
elif [[ $KUBERNETES_ENGINE == "gke" ]]; then
  FIRST_ZONE=$(echo $REGION_OR_ZONES | awk -F ',' '{print $1}')
  deploy_gke_cluster
  sleep 5
  create_gke_storage_classes
fi

sleep 5
deploy_operator
sleep 5
deploy_rabbitmq

wait_for_cluster
sleep 5
deploy_telegraf

echo "Deployment complete!"