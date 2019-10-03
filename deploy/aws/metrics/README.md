# Deploying Postgres, InfluxDB and Grafana on EC2

Some automated deployment scripts are provided, but may require some customization to use for yourself. Currently everything scripted to work in the default VPC.

In order to minimize variability and make benchmarks results reproducible, provisioned IOPs EBS volumes (IO1) are used. When using GP2, high variability is seen.

## InfluxDB server and benchmarking security groups

Run terraform from a directory where you store a variables file.

An example tfvars file:

```tfvars
region = "eu-west-1"
vpc_id = "vpc-123456789"
subnet_id = "subnet-123456789"
metrics_ami_id = "ami-must-be-ubuntu-1804-id"
key_pair = "Name of keypair file (without file extension)"
allowed_ingress_cidrs = ["your.ip.address.here/32"]
```

```bash
terraform apply -var-file=vars.tfvars ~/path/to/RabbitTestTool/deploy/aws/metrics/non-vpc-based/
```

Next we use Ansible to install InfluxDB and Grafana. It requires two Ansible variables files:

- .variables/influx-vars.yml
- .variables/grafana-vars.yml

Example influx-vars.yml:

```yml
influx_database:
  name: name_of_influx_database_here
influx_user:
  name: name_of_influx_user_here
  password: influx_user_password_here
```

Example of grafana-vars.yml:

```yml
grafana_logs_dir: /data/grafana/log
grafana_data_dir: /data/grafana/lib

grafana_security:
  admin_user: admin
  admin_password: admin_password_here

grafana_datasources:
  - name: datasource-influxdb
    type: influxdb
    url: http://localhost:8086
    access: proxy
    database: name_of_influx_database_here
    basicAuthUser: name_of_influx_user_here
    basicAuthPassword: influx_user_password_here
```

To run the Ansible playbook:

```bash
ansible-playbook install.yml --private-key=~/.ssh/MyKeyHere.pem
```

Now you should have an EC2 instance with Grafana and InfluxDB, plus three security groups:

```asciiart
/---(benchmark-loadgen-sg)---\
|                             ----> (benchmark-metrics-sg)  
\-->(benchmark-broker-sg)----/
```

## Grafana

Dashboards TODO!

## Postgres

TODO or alternatively use ElephantSQL :)