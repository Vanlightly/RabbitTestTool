#!/bin/bash

python3.6 run-logged-aws-playlist.py \
--mode benchmark \
--playlist-file playlists/exchanges/exchanges-types-no-confirms.json \
--aws-config-file /path/to/my/aws-config.json \
--loadgen-instance c5.4xlarge \
--gap-seconds 120 \
--repeat 1 \
--parallel 3 \
--config-count 1 \
--config-tag c1 \
--technology rabbitmq \
--instance c5.4xlarge \
--volume1-type ebs-gp2 \
--volume1-size 1000 \
--filesystem xfs \
--tenancy default \
--core-count 8 \
--threads-per-core 2 \
--cluster-size 1 \
--version 3.8.4 \
--generic-unix-url https://github.com/rabbitmq/rabbitmq-server/releases/download/v3.8.4/rabbitmq-server-generic-unix-latest-toolchain-3.8.4.tar.xz \
--vars-file .variables/rabbitmq-generic-vars-erl-23.yml \
--warm-up-seconds 60 \
--tags exchange-types