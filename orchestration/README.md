# RabbitTestTool Orchestration

The orchrestration scripts aim to make it easy to run both small and large scale benchmarks in AWS. It's based on a mix of Python, Bash, Ansible and the AWS CLI.

## Table of Contents

- [Concepts](#concepts)
    - [run-id, run-tag, config-tag, node, run-ordinal](#run-id,-run-tag,-config-tag,-node,-run-ordinal)
    - [repeat and parallel](#repeat-and-parallel)
    - [new-instance-per-run](#new-instance-per-run)
    - [cluster-size](#cluster-size)
    - [Deployments Visualized](#deployments-visualized)
- [Running a Playlist](#running-a-playlist)
    - [Config Files](#config-files)
    - [Notes on local storage and EBS volumes](#notes-on-local-storage-and-EBS-volumes)
    - [Notes on AWS CLI and Ansible](#notes-on-aws-cli-and-ansible)
    - [Notes on gcloud CLI](#notes-on-gcloud-cli)
    - [Notes on Influxdb](#notes-on-influxdb)
    - [Grafana Dashboards](#grafana-dashboards)
        - [One Node Dashboard](#one-node-dashboard)
        - [Node A vs Node B Dashboard](#node-a-vs-node-b-dashboard)
        - [Broker Server Metrics Dashboard](#broker-server-metrics-dashboard)

## Concepts

The Python orchestration scripts perform the following actions, in this order:

1. Create broker and load generation EC2 or GCP instances.
2. Deploy RabbitMQ and the Java RabbitTestTool program.
3. Run a playlist which is a list of benchmarks. Running a benchmark means running the deployed RabbitTestTool instances with a topology file specified in the playlist.
4. Terminate all EC2 or GCP instances

Playlists are json files which specify a list of benchmarks to run. Each benchmark has a topology file, optionally a policies file and a list of other arguments to configure the benchmark.

This example shows a playlist of three benchmarks which run a common topology file with some common overriden variables and some different overriden variables. They also share the same policy.

```json
{
    "benchmarks": [
        { "topologyVariables": { "publisherCount": "1", "queueCount": "1", "consumerCount": "1" } },
        { "topologyVariables": { "publisherCount": "1", "queueCount": "10", "consumerCount": "10" } },
        { "topologyVariables": { "publisherCount": "1", "queueCount": "10", "consumerCount": "100" } }
    ],
    "commonAttributes": {
        "topology": "throughput/exchanges/fanout.json",
        "topologyVariables": {
            "useConfirms": "true",
            "inflightLimit": "1000",
            "manualAcks": "true",
            "consumerPrefetch": "1000",
            "ackInterval": "1"
        },
        "policy": "quorum-queue.json",
        "policyVariables": {
            "maxInMemoryLength": "100000"
        }
    }
}
```

You can choose to run a single configuration or multiple configurations at the same time. For example, you could run the same playlist for 5 different versions of RabbitMQ at the same time.

The deployed brokers are numbered, for example, rabbitmq1. In a single deployment each broker gets a unique ordinal suffix, for example:

- rabbitmq1
- rabbitmq2
- rabbitmq3

Note that in a multiple configuration run, each configuration enumerates its broker numbers like this (example with 5 parallel runs per configuration):

| Config 1 | Config 2 |
| --- | --- |
| rabbitmq1 | rabbitmq6 |
| rabbitmq2 | rabbitmq7 |
| rabbitmq3 | rabbitmq8 |
| rabbitmq4 | rabbitmq9 |
| rabbitmq5 | rabbitmq10 |

### run-id, run-tag, config-tag, node, run-ordinal

All EC2 instances are tagged to allow automation scripts and Ansible to use these tags to get IP addresses of the servers. The tags are composed from the technology, node number and run-tag.

The __run-id__ is an auto-generated UUID and is passed an an argument all instances of RabbitTestTool. It is used to identify all benchmarks of a given run.

The __run-tag__ is a small auto-generated number and is used to tag EC2 instances to discriminate between the EC2 instances of other concurrent playlists being run, or run recently.

The __config-tag__ acts as an alias for the various configurations (volume, instance etc) and is used primarily in side-by-side benchmarks later to simplify queries and report generation.

The __node__ number is a number suffix that identifies the the broker.

The __run-ordinal__ is passed as an argument to each RabbitTestTool to identify where that benchmark sits in the playlist, so we can later perform an analysis.

For example, we can test two configurations: 3.7.17 vs 3.8.1 with a playlist of 2 benchmarks, and run each benchmark 3 times.

| benchmark | run-id | run-tag | config-tag | node | run-ordinal |
| -- | -- | -- | -- | -- | -- |
| fanout-topology against rabbitmq1 (3.7.17) | 467efe40-930c-407d-a002-4a64f9f47e2a | 67482 | c1 | 1 | 1 |
| fanout-topology against rabbitmq2 (3.7.17) | 467efe40-930c-407d-a002-4a64f9f47e2a | 67482 | c1 | 2 | 1 |
| fanout-topology against rabbitmq3 (3.7.17) | 467efe40-930c-407d-a002-4a64f9f47e2a | 67482 | c1 | 3 | 1 |
| fanout-topology against rabbitmq4 (3.8.1) | 467efe40-930c-407d-a002-4a64f9f47e2a | 67482 | c2 | 4 | 1 |
| fanout-topology against rabbitmq5 (3.8.1) | 467efe40-930c-407d-a002-4a64f9f47e2a | 67482 | c2 | 5 | 1 |
| fanout-topology against rabbitmq6 (3.8.1) | 467efe40-930c-407d-a002-4a64f9f47e2a | 67482 | c2 | 6 | 1 |
| topic-topology against rabbitmq1 (3.7.17) | 467efe40-930c-407d-a002-4a64f9f47e2a | 67482 | c1 | 1 | 2 |
| topic-topology against rabbitmq2 (3.7.17) | 467efe40-930c-407d-a002-4a64f9f47e2a | 67482 | c1 | 2 | 2 |
| topic-topology against rabbitmq3 (3.7.17) | 467efe40-930c-407d-a002-4a64f9f47e2a | 67482 | c1 | 3 | 2 |
| topic-topology against rabbitmq4 (3.8.1) | 467efe40-930c-407d-a002-4a64f9f47e2a | 67482 | c2 | 4 | 2 |
| topic-topology against rabbitmq5 (3.8.1) | 467efe40-930c-407d-a002-4a64f9f47e2a | 67482 | c2 | 5 | 2 |
| topic-topology against rabbitmq6 (3.8.1) | 467efe40-930c-407d-a002-4a64f9f47e2a | 67482 | c2 | 6 | 2 |

Later when generating statistic analyses, the combination of run-id, config-tag and run-ordinal are used to match together the different benchmarks to be compared. In the above case we would compare the fanout topology run 3 times on 3.7.17 vs the 3 with 3.8.1.

#### repeat and parallel

Due to variability of benchmark results in the cloud, multiple runs are advised. The report generation can take multiple runs and compute averages, std dev, min and max across multiple runs.

We can run a playlist multiple times either concurrently or sequentially. Because a playlist can take a long time to run, parallel execution is probably the most useful.

To set the parallelization to 5, set --parallel 5.

Be careful, this can spin up a lot of machines. With a side-by-side test with --parallel 5, you'll end up with 20 EC2 instances for a few hours. 20 because:

- 5 instances for broker config 1
- 5 instances for the benchmark program for config 1
- 5 instances for broker config 2
- 5 instances for the benchmark program for config 2

### new-instance-per-run

This argument for the python scripts applies to when --repeat > 1. It will deploy new EC2 instances for each repetition rather than the default of reusing them.

This slows down total run time.

### cluster-size

By default, only one broker is deployed per benchmark instance. But using this argument you can deploy clusters instead. The numbering of broker nodes changes when using clusters. For example, with a side-by-side run, with --parallel 2 --cluster-size 3, the numbering is:

| Config 1 | Config 2 |
| --- | --- |
| 1 | 7 |
| 2 | 8 |
| 3 | 9 |

| Config 1 | Config 2 |
| --- | --- |
| 4 | 10 |
| 5 | 11 |
| 6 | 12 |

### Deployments Visualized

![](https://github.com/vanlightly/rabbittesttool/blob/master/images/rabbittesttool-single.png)
Fig 1 shows a single benchmark run with --parallel 4. Each of the four instances gets its own run-tag but they share the same config-tag.

![](https://github.com/vanlightly/rabbittesttool/blob/master/images/rabbittesttool-side-by-side.png)
Fig 2 shows a side-by-side run with --parallel 2. Each of the four instances gets its own run-tag then two have config-tag c1 and the other two have config-tag c2.

![](https://github.com/vanlightly/rabbittesttool/blob/master/images/rabbittesttool-cluster.png)
Fig 3 shows a single run with --parallel 2 and a cluster size of 3. The first benchmark VM and its three VMs share the same run-tag, the second benchmark VM and its three VMs share the same run-tag and they all share the same config-tag.

## Running a Playlist

Arguments reference.

Common argument:

| Argument | Default | Description  |
| --- | --- | --- |
| --playlist-file | None (mandatory) | The path to the playlist file that will be run |
| --aws-config-file | None (optional) | The path to the AWS configuration file (described further below) |
| --gcp-config-file | None (optional) | The path to the GCP configuration file (described further below) |
| --gcp-project-id | None (mandatory) | The GCP project where the instances are created (used to construct rabbitmq node names) |
| --gcp-postgres-connection-name | None (optional) | The connection name of a GCP Cloud SQL Database (decribed further below) |
| --loadgen-instance | None (mandatory) | The EC2 instance type that the benchmark Java program will run on |
| --loadgen-machine-type | None (mandatory) | The GCP machine type that the benchmark Java program will run on |
| --loadgen-container-image | None (mandatory) | The docker image containing the Java program (built from the Dockerfile in this repo) (GCP only) |
| --config-count | None (mandatory) | The number of configurations to run |
| --gap-seconds | None (mandatory) | The number of seconds between each benchmark |
| --repeat | 1 | The number of times the playlist is run sequentially. Default is once. |
| --parallel | 1 | The number of parallel executions of the playlist. Default is one. |
| --no-destroy | false | Whether or not the cluster is terminated on completion or error of the playlist |
| --no-deploy | false | Whether to deploy all the EC2 instances and installation scripts. When true, also needs a run-tag of an existing cluster |
| --run-tag | None (optional) | For when a playlist is to be run on a previously and not terminated cluster |
| --step-override-seconds | 0 | Override all step durations |
| --step-override-repeat | 0 | Make all steps get executed repeatedly |
| --bg-topology-file | None (optional) | A topology file that will be run on a second instance of the Java program, to generate background load that will not be recorded in Grafana or Postgres. It's virtual hosts must not clash with the main topology file. |
| --bg-policies-file | None (optional) | A policies file that will be applied to the background virtual hosts |
| --bg-delay | 0 | A delay in seconds before running the main topology |
| --bg-step-seconds | 0 | Override all step durations in the background topology |
| --bg-step-repeat | 0 | Make all steps get executed repeatedly in the background topology |
| --federation-enabled | false | When true, a second broker (cluster) will be deployed, acting as the downstream. The downstream will have the same node numbering as the upstream + 100. So an upstream may be rabbitmq1 and the downstream rabbitmq101. (Only supported in AWS orchrestration) |

Arguments that can be applied to all or specific configurations. When running multiple configurations, you can add a number suffix to match the configuration, for example --version1 3.7.17 and --version2 3.8.1.

| Argument | Default | Description  |
| --- | --- | --- |
| --config-tag | None (mandatory) | The alias for this configuration |
| --technology | None (mandatory) | The technology being tested, rabbitmq |
| --version | None (mandatory) | The version to be deployed |
| --instance | None (mandatory) | The EC2 instance type of the broker instances |
| --container-image | None (mandatory) | The docker image containing rabbitmq (typically rabbitmq:3.X) (GCP only) |
| --container-env | None (optional) | Additional env vars to be passed to rabbitmq containers (GCP only) |
| --machine-type | None (mandatory) | The GCP machine type of the broker instances |
| --volume | None (mandatory) | The volume type. When EBS must be like ebs-io1 or ebs-st1 or ebs-gp2, pd-ssd or standard for GCP |
| --volume1-size | 50 | The size in MB of the volume 1 |
| --volume2-size | 0 | The size in MB of the volume 2. Zero signifies no volume. |
| --volume3-size | 0 | The size in MB of the volume 3. Zero signifies no volume. |
| --data-volume | volume1 | Which volume mnesia and message store data resides on |
| --logs-volume | volume1 | Which volume logs resides on |
| --wal-volume | volume1 | Which volume the WAL files resides on |
| --filesystem | None (mandatory) | xfs or ext4 |
| --tenancy | None (mandatory) | Default or Dedicated |
| --core-count | None (mandatory) | The number of cores will be half the vCPU count |
| --threads-per-core | None (mandatory) | Use 2 for hyperthreading or 1 with hyperthreading disabled |
| --cluster-size | 1 | The number of nodes in the cluster |
| --vars-file | None (optional) | A custom variables file for the Ansible provisioning script |
| --policies-file | None (optional) | The path of a json file with policies to be deployed |
| --no-tcp-delay | true | Whether Nagles algorithm is used or not, defaults to not, that is with no delay |
| --con-connect-to-node | roundrobin | Which node will a consumer connect to. "roundrobin", "local", "non-local", "random". Local refers to the node which hosts the queue (when mirrored or quorum means the master/leader) |
| --pub-connect-to-node | roundrobin | Which node will a publisher connect to. "roundrobin", "local", "non-local", "random". Local refers to the node which hosts the queue (only valid when using the default exchange for point-to-point messaging). |

Example with a single configuration with one volume:

```bash
python3.6 run-logged-aws-playlist.py \
--mode benchmark \
--playlist-file playlists/point-to-point-safe.json \
--aws-config-file path/to/aws-config.json \
--loadgen-instance c4.4xlarge \
--gap-seconds 120 \
--repeat 1 \
--parallel 1 \
--tags tag1,tag2 \
--override-step-seconds 300 \
--config-count 1 \
--config-tag c1 \
--technology rabbitmq \
--instance r5.4xlarge \
--volume-type ebs-io1 \
--volume1-size 200 \
--filesystem xfs \
--tenancy default \
--core-count 8 \
--threads-per-core 2 \
--cluster-size 3 \
--pub-connect-to-node local \
--version 3.8.3 \
--generic-unix-url https://github.com/rabbitmq/rabbitmq-server/releases/download/v3.8.0/rabbitmq-server-generic-unix-3.8.0.tar.xz
```

Example with a single configuration with three volumes for data, logs and wal:

```bash
python3.6 run-logged-aws-playlist.py \
--mode benchmark \
--playlist-file playlists/point-to-point-safe.json \
--aws-config-file path/to/aws-config.json \
--loadgen-instance c4.4xlarge \
--gap-seconds 120 \
--repeat 1 \
--parallel 1 \
--tags tag1,tag2 \
--override-step-seconds 300 \
--config-count 1 \
--config-tag c1 \
--technology rabbitmq \
--instance r5.4xlarge \
--volume-type ebs-io1 \
--volume1-size 200 \
--volume2-size 20 \
--volume3-size 50 \
--data-volume volume1 \
--logs-volume volume2 \
--wal-volume volume3 \
--filesystem xfs \
--tenancy default \
--core-count 8 \
--threads-per-core 2 \
--cluster-size 3 \
--pub-connect-to-node local \
--version 3.8.3 \
--generic-unix-url https://github.com/rabbitmq/rabbitmq-server/releases/download/v3.8.0/rabbitmq-server-generic-unix-3.8.0.tar.xz
```

Example with two configurations:

```bash
python3.6 run-logged-aws-playlist.py \
--mode benchmark \
--playlist-file playlists/point-to-point-safe.json \
--aws-config-file path/to/aws-config.json \
--loadgen-instance c4.4xlarge \
--gap-seconds 120 \
--repeat 1 \
--parallel 1 \
--tags tag1,tag2 \
--override-step-seconds 300 \
--config-count 2 \
--technology rabbitmq \
--instance r5.4xlarge \
--volume-type ebs-io1 \
--volume1-size 200 \
--filesystem xfs \
--tenancy default \
--core-count 8 \
--threads-per-core 2 \
--cluster-size 3 \
--pub-connect-to-node local \
--config-tag1 c1 \
--version1 3.8.0 \
--generic-unix-url1 https://github.com/rabbitmq/rabbitmq-server/releases/download/v3.8.0/rabbitmq-server-generic-unix-3.8.0.tar.xz \
--config-tag2 c2 \
--version2 3.8.1 \
--generic-unix-url2 https://github.com/rabbitmq/rabbitmq-server/releases/download/v3.8.1/rabbitmq-server-generic-unix-3.8.1.tar.xz \
```

Example with background load:
```bash
python3.6 run-logged-aws-playlist.py \
--mode benchmark \
--playlist-file playlists/point-to-point-safe.json \
--aws-config-file path/to/aws-config.json \
--loadgen-instance c4.4xlarge \
--gap-seconds 120 \
--repeat 1 \
--parallel 1 \
--tags tag1,tag2 \
--override-step-seconds 300 \
--bg-topology-file background/AddRemoveCpuLoad.json \
--bg-delay 0 \
--bg-step-seconds 120 \
--config-count 1 \
--config-tag c1 \
--technology rabbitmq \
--instance r5.4xlarge \
--volume-type ebs-gp2 \
--volume1-size 200 \
--filesystem xfs \
--tenancy default \
--core-count 8 \
--threads-per-core 2 \
--cluster-size 3 \
--pub-connect-to-node local \
--version 3.8.0 \
--generic-unix-url https://github.com/rabbitmq/rabbitmq-server/releases/download/v3.8.0/rabbitmq-server-generic-unix-3.8.0.tar.xz \
```

You can take any of the arguments that can be applied to a single configuration and have multiple configurations which share everything but that one argument.

### Config Files

When running benchmarks on EC2, there are arguments related to EC2 such as subnets and security groups. The recommended approach is to create a config file with those arguments in order to reduce the number of command line arguments.

```json
{
    "keypair": "MyKeyPair",
    "broker-sg": "sg-the-broker-sg-here",
    "loadgen-sg": "sg-the-sg-for-benchmark-prog-here",
    "ami": "ami-the-ubuntu1804-ami-id-here",
    "subnet": "subnet-your-subnet-id",
    "postgres-jdbc-url": "jdbc:postgresql://can.i.suggest.you.use.elephantsql.com:5432/yourdb",
    "postgres-password": "super-secret-password",
    "postgres-user": "youruser",
    "password": "this-is-for-your-broker-and-influx-(they-need-to-be-the-same)"
}
```

### Notes on local storage and EBS volumes

Up to 3 EBS volumes can be provisioned. The data, logs and wal files can be configured to use those volumes as required. Currently all volumes will share the same filesystem and volume type. io1 volumes are provisioned with 50 IOPS per GB.

When using c5d and z1d class instances, things work differently. An extra EBS volume is not created and mounted. Instead the NVMe local storage volume is used. The volume-size argument is used to identify the volume to be mounted, for c5d.large, the volume size is 46.6. If you set it to 50, as described in AS docs, it will fail to mount (a better way is needed of identifying the volume to mount). Data, logs and wal are all on this single NVMe volume.


### Notes on AWS CLI and Ansible

Automation scripts use the aws cli and Ansible (using dynamic inventory).

These scripts assume either an AWS profile is set up or you have temporary credentials in the environment variables:

- AWS_ACCESS_KEY_ID
- AWS_SECRET_ACCESS_KEY
- AWS_SESSION_TOKEN

This readme does not cover usage of AWS CLI or Ansible with dynamic inventory (ec2.py, ec2.ini).

### Notes on gcloud CLI

`run-logged-gcp-playlist.py` assumes that the `gcloud` cli is installed, and logged in with permissions to create vms. The `--gcp-project-id` flag should match the current project so that the internal DNS names of created vms are inferred correctly.

Unlike AWS orchestration, GCP orchestration creates vms via `gcloud compute instances create-with-container ...`. Therefore the broker version is effectively specified in choosing the `--container-image` flag. The image can be hosted on Docker Hub, or the private Google Container Registry of the GCP project of the vms.

The java benchmarking tool's version is also specified in this manner. Additional topologies and policies can be added by inheriting from the base image and placing them in `/rabbittesttool/policies` and `/rabbittesttool/topologies`.

### Notes on Influxdb

The scripts assume that influxdb is installed on an EC2 instance with the tag: inventorygroup=benchmarking_metrics. The script will not work without that and would have to be customized.

### Grafana Dashboards

There are three dashboards:

- One Node
- Node A vs Node B
- Broker Server Metrics
- Broker Server
- RabbitMQ Overview

Find the json dashboard files under deployment/grafana-dashboards.

#### One Node Dashboard

Has variables for choosing the technology and the node number. If you just ran a single benchmark then likely the node number is 1. If it was a Logged benchmark then you can find the configuration in Postgres.

#### Node A vs Node B Dashboard

This dashboard assumes you have run multiple benchmarks concurrently (either as a single benchmark run that was parallelised or a aide-by-side run). It allows you to select two benchmarks via technology and node number and show both results in each chart.

If it was a Logged benchmark run then you can find the configurations in Postgres.

#### Broker Server Metrics Dashboard

Shows the usual CPU, memory, disk and network stats sourced from the VM where the broker is running, extracted by Telegraf.
check_user_pass_login