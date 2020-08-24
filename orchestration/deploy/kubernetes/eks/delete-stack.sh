#!/bin/bash

# EKS does not remove stack when deleting

# WIP

VPC=$(aws elb describe-load-balancers --query 'LoadBalancerDescriptions[*].[VPCId]' --output text)
