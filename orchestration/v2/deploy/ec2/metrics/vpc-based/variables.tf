variable "region" {}

variable "metrics_instance_type" {
    default = "t2.small"
}

variable "metrics_ami_id" {
}

variable "allowed_ingress_cidrs" {
    type = "list"
}

variable "key_pair" {}
