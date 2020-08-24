#!/bin/bash

echo "Username: $(kubectl get secret rtt-rabbitmq-admin -o jsonpath="{.data.username}" | base64 --decode)"
echo "Password: $(kubectl get secret rtt-rabbitmq-admin -o jsonpath="{.data.password}" | base64 --decode)"