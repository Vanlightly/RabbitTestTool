#!/bin/bash

# NOT TESTED - WILL NOT WORK, MORE OF A GUIDE REALLY

while getopts ":a:A:p:g:G:" opt; do
  case $opt in
    a) AWS_CONFIG_FILE="$OPTARG"
    ;;
    A) AWS_CREDENTIALS_FILE="$OPTARG"
    ;;
    p) AWS_PEM_FILE="$OPTARG"
    ;;
    g) GCLOUD_KEY_FILE="$OPTARG"
    ;;
    G) GCLOUD_PROJECT="$OPTARG"
    ;;
    \?) echo "Invalid option -$OPTARG" >&2
    ;;
  esac
done

docker build \
--build-arg AWS_CONFIG=$AWS_CONFIG_FILE \
--build-arg AWS_CREDENTIALS=$AWS_CREDENTIALS_FILE \
--build-arg AWS_PEM=$AWS_PEM_FILE \
--build-arg GCLOUD_KEY_FILE=$GCLOUD_KEY_FILE \
--build-arg GCLOUD_PROJECT=$GCLOUD_PROJECT .