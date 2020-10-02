variable "region" {}

variable "vpc_id" {}

variable "subnet_id" {}

variable "metrics_instance_type" {
    default = "t2.small"
}

variable "metrics_ami_id" {
}

variable "allowed_ingress_cidrs" {
    type = "list"
}

variable "key_pair" {}
