provider "aws" {
  region = "${var.region}"
}

resource "aws_vpc" "benchmark_vpc" {
  cidr_block = "10.0.0.0/16"

  tags {
    Name = "Benchmark-VPC"
  }
}

# Create an internet gateway to give our subnet access to the outside world
resource "aws_internet_gateway" "default" {
  vpc_id = "${aws_vpc.benchmark_vpc.id}"
}

# Grant the VPC internet access on its main route table
resource "aws_route" "internet_access" {
  route_table_id         = "${aws_vpc.benchmark_vpc.main_route_table_id}"
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = "${aws_internet_gateway.default.id}"
}

# Create a subnet to launch our instances into
resource "aws_subnet" "benchmark_subnet" {
  vpc_id                  = "${aws_vpc.benchmark_vpc.id}"
  cidr_block              = "10.0.0.0/24"
  map_public_ip_on_launch = true
}

resource "aws_security_group" "benchmark_security_group" {
  name   = "benchmark-sg"
  vpc_id = "${aws_vpc.benchmark_vpc.id}"

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

  ingress {
      from_port = 3000
      to_port = 3000
      protocol    = "TCP"
      cidr_blocks = ["0.0.0.0/0"]
      description = "Grafana access"
  }

  # All ports open within the VPC
  ingress {
    from_port   = 0
    to_port     = 65535
    protocol    = "tcp"
    cidr_blocks = ["10.0.0.0/16"]
    description = "Internal VPC traffic"
  }

  # outbound internet access
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags {
    Name = "Benchmark-Security-Group"
  }
}

resource "aws_instance" "metrics" {
  ami           = "${var.metrics_ami_id}"
  instance_type = "${var.metrics_instance_type}"
  vpc_security_group_ids = ["${aws_security_group.benchmark_security_group.id}"]
  # availability_zone = "${var.availability_zone}"
  subnet_id = "${aws_subnet.benchmark_subnet.id}"
  associate_public_ip_address = true
  key_name = "${var.key_pair}"
  volume_tags {
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
  tags { 
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