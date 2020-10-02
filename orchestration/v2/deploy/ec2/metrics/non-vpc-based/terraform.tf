provider "aws" {
  region = "${var.region}"
}

resource "aws_security_group" "loadgen_security_group" {
  name   = "benchmark-loadgen-sg"
  vpc_id = "${var.vpc_id}"

  ingress {
      from_port = 0
      to_port = 65535
      protocol    = "TCP"
      cidr_blocks = "${var.allowed_ingress_cidrs}"
      description = "Jack all access"
  }

  # SSH access from anywhere
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "SSH access"
  }

  # outbound internet access
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "Benchmark-LoadGen"
  }
}

resource "aws_security_group" "broker_security_group" {
  name   = "benchmark-broker-sg"
  vpc_id = "${var.vpc_id}"

  ingress {
      from_port = 0
      to_port = 65535
      protocol    = "TCP"
      cidr_blocks = "${var.allowed_ingress_cidrs}"
      description = "Jack all access"
  }

  ingress {
      from_port = 0
      to_port = 65535
      protocol    = "TCP"
      security_groups = ["${aws_security_group.loadgen_security_group.id}"]
      description = "Broker access"
  }

  # SSH access from anywhere
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "SSH access"
  }

  # outbound internet access
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "Benchmark-Brokers"
  }
}


resource "aws_security_group" "benchmark_security_group" {
  name   = "benchmark-metrics-sg"
  vpc_id = "${var.vpc_id}"

  ingress {
      from_port = 0
      to_port = 65535
      protocol    = "TCP"
      cidr_blocks = "${var.allowed_ingress_cidrs}"
      description = "Jack all access"
  }

  ingress {
      from_port = 0
      to_port = 65535
      protocol    = "TCP"
      security_groups = ["${aws_security_group.loadgen_security_group.id}"]
      description = "Broker access"
  }

  # SSH access from anywhere
  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
    description = "SSH access"
  }

  ingress {
      from_port = 3000
      to_port = 3000
      protocol    = "TCP"
      cidr_blocks = ["0.0.0.0/0"]
      description = "Grafana access"
  }

  # outbound internet access
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "Benchmark-Metrics"
  }
}

resource "aws_instance" "metrics" {
  ami           = "${var.metrics_ami_id}"
  instance_type = "${var.metrics_instance_type}"
  vpc_security_group_ids = ["${aws_security_group.benchmark_security_group.id}"]
  subnet_id = "${var.subnet_id}"
  associate_public_ip_address = true
  key_name = "${var.key_pair}"
  volume_tags = {
    Name = "benchmarking_metrics"
  }
  root_block_device {
    volume_type = "gp2"
    volume_size = "20"
  }
  ebs_block_device {
    volume_type = "gp2"
    volume_size = "100"
    device_name = "/dev/sdb"
    iops = 300
    delete_on_termination = false
  }
  tags = { 
    Name = "benchmarking_metrics"
    inventorygroup = "benchmarking_metrics"
  }
}

output "metrics-public-ip" {
  value = "${aws_instance.metrics.public_ip}"
}

output "metrics-private-ip" {
  value = "${aws_instance.metrics.private_ip}"
}