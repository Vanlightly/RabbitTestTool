#!/bin/bash

VARIABLES_FILE=$1
source $VARIABLES_FILE

set -e

ROOT_PATH=$(pwd)
cd $ROOT_PATH/rabbitmq


ansible-playbook update-rabbitmq-config.yml --private-key=~/.ssh/$KEY_PAIR.pem --ssh-common-args='-o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null' \
--tags configuration \
--extra-vars "node=$NODE" \
--extra-vars "run_tag=$RUN_TAG" \
--extra-vars '{"standard_config":['"${STANDARD_VARS}"']}' \
--extra-vars '{"advanced_config_rabbit":['"${ADVANCED_VARS_RABBIT}"']}' \
--extra-vars '{"advanced_config_ra":['"${ADVANCED_VARS_RA}"']}' \
--extra-vars '{"advanced_config_aten":['"${ADVANCED_VARS_ATEN}"']}' \
--extra-vars '{"env_config":['"${ENV_VARS}"']}'