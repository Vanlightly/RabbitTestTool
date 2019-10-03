# RabbitTestTool

The RabbitTestTool is a tool for running performance and correctness experiments on RabbitMQ.

## Table of Contents

- [Concepts](#concepts)
- [Topologies](#topologies)
    - [Example Topology File](#example-topology-file)
    - [Virtual Hosts](#virtual-hosts)
    - [Exchanges](#exchanges)
    - [Queue Groups](#queue-groups)
    - [Publisher Groups](#publisher-groups)
    - [Consumer Groups](#consumer-groups)
    - [Dimensions](#dimensions)
        - [Fixed dimensions](#fixed-dimensions)
        - [Single Dimension](#single-dimension)
        - [Multiple Dimensions](#multiple-dimensions)
- [Metrics](#metrics)
- [Running RabbitTestTool](#running-rabbittesttool)
    - [Building from Source](#building-from-source)
    - [Dependencies](#dependencies)
    - [Modes](#modes)
    - [Playlists](#playlists)
    - [Benchmark Arguments](#benchmark-arguments)
    - [Simple Benchmarks](#simple-benchmarks)
        - [Running a single Simple benchmark run locally](#running-a-single-simple-benchmark-run-locally)
        - [Running a playlist of Simple benchmarks](#running-a-playlist-of-simple-benchmarks)
    - [Logged Benchmarks](#logged-benchmarks)
        - [Running a single Logged benchmark run locally](#running-a-single-logged-benchmark-run-locally)
        - [Running a playlist of Logged benchmarks](#running-a-playlist-of-logged-benchmarks)
        - [Logged Benchmark Deployment and Execution on AWS](#logged-benchmark-deployment-and-execution-on-aws)
        - [Notes on local storage and EBS volumes](#notes-on-local-storage-and-ebs-volumes)
        - [Notes on --run_tag and --config_tag](#notes-about---run_tag-and---config_tag)
        - [Notes on --repeat and --parallel](#notes-on---repeat-and---parallel)
        - [Notes on --new-instance-per-run](#notes-on---new-instance-per-run)
        - [Notes on --cluster-size](#notes-on---cluster-size)
        - [Deployments Visualized](#deployments-visualized)
        - [Notes AWS CLI and Ansible](#notes-aws-cli-and-ansible)
        - [Notes on Influxdb](#notes-on-influxdb)
        - [Single broker playlist example](#single-broker-playlist)
        - [Side-by-side broker playlist example](#side-by-side-broker-playlist)
    - [Grafana Dashboards](#grafana-dashboards)          
        - [One Node Dashboard](#one-node-dashboard)
        - [Node A vs Node B Dashboard](#node-a-vs-node-b-dashboard)
        - [Broker Server Metrics](#broker-server-metrics-dashboard)
    - [Reports](#reports)
    - [Model Driven Property Based Test](#model-driven-property-based-test)

## Concepts

### Topologies

Each benchmark run requires a topology file that describes the virtual hosts, exchanges, queues, bindings, publishers and consumers. Additionally it can describe dimensions by which to scale out the test in a series of steps, for example, by increasing the publish rate or the message size.

Each topology defines one of three benchmark types:

- throughput
- latency
- stress

In general, latency tests do not make brokers reach 100% capacity, they are rate limited to avoid that. Throughput tests are not generally rate limited as they are designed to test the maximum throughput possible for a given configuration. Stress tests place more load on a broker than it can handle.

Each topology has two main components:

- list of virtual hosts which describes the initial state (exchanges, queues etc)
- the dimensions

A series of benchmarks can be run via playlist files which simply list the topology files to be run.

### Example Topology File

A topology file does not specify urls, users etc, just the exchanges, queues, publishers, consumers and any variable dimensions.

```json
{
    "topologyName": "TP_MessageSizeBenchmark1",
    "topologyType": "SingleDimension",
    "benchmarkType": "throughput",
    "description": "Slowly increasing message size",
    "vhosts": [
        {
            "name": "benchmark",
            "exchanges": [
                {
                    "name": "ex1",
                    "type": "fanout"
                }
            ],
            "queueGroups": [
                {
                    "group": "q1",
                    "scale": 1,
                    "bindings": [
                        { "from": "ex1" }
                    ]
                }
            ],
            "publisherGroups": [
                {
                    "group": "p1",
                    "scale": 1,
                    "sendToExchange": {
                        "exchange": "ex1",
                        "routingKeyMode": "none"
                    },
                    "deliveryMode": "Persistent",
                    "messageSize": 16
                }
            ],
            "consumerGroups": [
                {
                    "group": "c1",
                    "scale": 1,
                    "queueGroup": "q1"
                }
            ]
        }
    ],
    "dimensions" : {
        "singleDimension": {
            "dimension": "messageSize",
            "values": [16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144, 524288, 1048576],
            "stepDurationSeconds": 60,
            "rampUpSeconds": 10,
            "applyToGroup": "p1"
        }
    }
}
```

### Virtual Hosts

One or more virtual hosts must be defined. Usually only a single virtual host is needed. Each virtual host consists of exchanges, queues, consumers and publishers.

A virtual host can also be scaled out. For example, when the virtual host "test" has its "scale" field set to 5, five copies (with all its exchanges, queue s etc) are declared, with the names:

- test00001
- test00002
- test00003
- test00004
- test00005

### Exchanges

The exchanges are defined in a JSON array, such as:

```json
"exchanges": [
      { "name": "ex1", "type": "topic" },
      { "name": "ex2", "type": "fanout" }
    ]
```

### Queue Groups

A queue group is a group of queues that share the same configuration and can be scaled out.

For example, queue group q1 consists of a single queue with a binding to the topic exchange ex1. Queue group q2 consists of 10 queues, each bound to fanout exchange ex2.

Queues are named **group_ordinal**, for example:

- q1_00001
- q1_00002
- q1_00003
- q1_00004
- q1_00005

```json
"queueGroups": [
    {
        "group": "q1",
        "scale": 1,
        "bindings": [
            { "from": "ex1", "bindingKey": "my.*.key" }
        ]
    },
    {
        "group": "q2",
        "scale": 10,
        "bindings": [
            { "from": "ex2" }
        ]
    }
]
```

### Publisher Groups

A publisher group is a group of publishers that share the same configuration and can be scaled out. For example, the publisher group below publishes to the ex1 exchange with each message having a randomly selected routing key from the 5 keys defined.

```json
publisherGroups": [
      {
        "group": "p1",
        "scale": 5,
        "sendToExchange": {
          "exchange": "ex1",
          "routingKeys": ["rk1", "rk2", "rk3", "rk4", "rk5"],
          "routingKeyMode": "MultiValue"
        },
        "deliveryMode": "Persistent"
      }
    ]
```

### Consumer Groups

A consumer group is a group of consumers that share the same configuration and can be scaled out. For example, the consumer group below will consume from the queues of queue group q1, using manual acks with a prefetch of 1000 messages and will acknowledge every 100th message with the multiple flag. The group will start with 2 consumers.

```json
"consumers": [
        {
            "group": "c1",
            "ackMode": {
                "manualAcks": true,
                "consumerPrefetch": 1000,
                "ackInterval": 100
            },
            "scale": 2,
            "queueGroup": "q1"
        }
    ]
```

A consumer group will be spread across its queue group evenly, with each consumer only ever consuming a single queue. For example, a queue group of 5 and a consumer group of 10, will see each queue consumed by 2 consumers. Likewise, a queue group of 10 and a consumer group of 5 will see 5 queues unconsumed.

When a queue group is scaled out as a single dimension, any consumer groups that consume it will not change accordingly, meaning that new queues will remain unconsumed. In order to scale out a queue group and have the new queues consumed from, you must also scale out the consumer group. See Multiple Dimensions below for an example.

### Dimensions

A single run has either a fixed topology, or it can modify one or more dimensions. The currently supported dimensions are:

| Dimension | Description |
| --- | --- |
| PublisherRate | Target publish rate per second per publisher |
| MessageSize | The message size in bytes |
| HeadersPerMessage | The number of message headers to send with each message |
| RoutingKeyIndex | The routing key to send by its index (in the array "routingKeys" in the publisher config) |
| InFlightLimit | Publisher in-flight message limit (number of unconfirmed messages) |
| Prefetch | Consumer prefetch count per consumer |
| AckInterval | Consumer ack interval |
| Publishers | Publisher count |
| Consumers | Consumer count |
| Queues | Queue count |

Dimensions can be scaled out globally or just target a single group. So if we have two publisher groups, we can scale out the message size of just one publisher group, or all publisher groups.

#### Fixed dimensions

When a topology is fixed, all we need to do is specify a duration for the test and the ramp up time. The ramp up time is the period allowed for the publishing and consuming rate to stabilize before recording statistics.

For example:

```json
"dimensions" : {
    "fixedDimensions": {
      "durationSeconds": 120,
      "rampUpSeconds": 10
    }
}
```

#### Single Dimension

When we define a single dimension, we specify the name, the values and the step duration. Each step is its own benchmark and will log its metrics as a separate benchmark which can be queried later.

For example, this dimension is applied to a single publisher group and affects the target publish rate per publisher. Each value constitutes a step with a duration of 60 seconds, with 10 seconds before each step to allow for fluctuations and for the publishing/consuming rate to stabilize before recording statistics. This test consists of 11 steps with each step consisting of 10 + 60 seconds.

```json
"dimensions" : {
    "singleDimension": {
        "dimension": "PublisherRate",
        "values": [1, 5, 10, 50, 100, 500, 1000, 5000, 10000, 15000, 20000],
        "stepDurationSeconds": 60,
        "rampUpSeconds": 10,
        "applyToGroup": "p1"
    }
}
```

#### Multiple Dimensions

Just like with single dimensions, we define the dimenion names, values and step duration/ramp up.

For example, the following defines how prefetch and ack interval are modified in each step:

```json
"dimensions" : {
    "multipleDimensions": {
        "dimensions": ["Prefetch","AckInterval"],
        "multiValues": [[1,1],
            [10, 5],
            [50, 25],
            [100, 50],
            [500, 250],
            [1000, 500],
            [5000, 2500],
            [10000, 5000]
        ],
        "stepDurationSeconds": 60,
        "rampUpSeconds": 10,
        "applyToGroup": "c1"
    }
}
```

This example shows the scaling out of a queue and consumer group together:

```json
"dimensions" : {
    "multipleDimensions": {
        "dimensions": ["Queues","Consumers"],
        "multiValues": [[1,1],
            [2, 2],
            [3, 3],
            [4, 4],
            [5, 5],
            [6, 6]
        ],
        "stepDurationSeconds": 60
    }
}
```

## Metrics

Metrics and statistics are compiled and sent to InfluxDB. Later on more providers may be added. The following metrics/statistics are gathered:

- Published msgs/s
- Published bytes/s
- Confirmed msgs/s
- Nacked msgs/s
- Returned msgs/s
- Consumed msgs/s
- Consumed bytes/s
- Latency at percentiles 50, 75, 95, 99, 99.9
- Confirm Latency at percentiles 50, 75, 95, 99, 99.9
- Published message size
- Consumed message size
- Routing key length
- Message header count
- Connection failures per interval
- Number of publishers
- Number of consumers
- Number of queues
- Consumer prefetch
- Consumer ack interval
- Delivery mode
- Multiple flag usage in publisher confirms

In addition, if the deployment scripts are used, then host metrics (CPU, Disk IO, Memory, Network etc) are also published to InfluxDB. JSON files for Grafana dashboards are also available.

See the TOPOLOGY.md for greater detail.

## Running RabbitTestTool

### Building from Source

To build the JAR file:

./mvnw clean package

Creates a single JAR file called rabbittesttool-*version*-jar-with-dependencies.

### Dependencies

Optionally the benchmark will post metrics to an instance of InfluxDB.
When running a "logged" benchmark, an instance of Postgres is required.

In the future these may be abstracted to allow any time series database and any RDBMS.

### Modes

There are four modes available. Two for running benchmarks, one for generating comparison reports (comparing two runs) and a model driven property based test that checks message ordering and message loss.

Modes:

- simple-benchmark: For running the benchmark locally, without logging and comparing of results. Can still publish metrics to InfluxDB.
- logged-benchmark: Logs results to postgres and supports parallel runs running on different machines.
- comparison: Generates a report that compares to runs
- model: Tests correctness

### Playlists

A playlist is simply a text file with a list of topology file paths. Python scripts are available that run an instance of the Java RabbitTestTool program for each benchmark.

Playlists are not a concept that is understood by the Java program itself. The Java program can only run a single benchmark based on a single topology file.

### Benchmark Arguments

The java program takes many arguments which can be supplied via command line, JSON file or a mix of both. The usage of these arguments are as follows:

- benchmark workings
- logging/tagging of metrics and results
- connection to broker
- connection to InfluxDB
- connection to Postgres

Many arguments are for tagging metrics in InfluxDB and logging data to postgres. It is your responsibility to ensure that the arguments match reality. Simple benchmarks do not require all arguments.

Find the postgres tables in the sql folder.

| Argument | Usage | Description  |
| --- | --- | --- |
| topology | benchmark | The path to the topology file to run |
| config-tag | logging | An alias for the configuration as a whole. Useful when comparing two concurrent benchmarks with different configurations |
| technology | logging | The broker technology being tested. Used in tagging and logging metrics. |
| version | logging | The broker version |
| run-id | logging | A unique id for this run. Used when logging results |
| run-tag | logging | Used for cloud deployments, can be set to anything when running locally |
| node | logging | The suffix to the broker name. For example, for rabbitmq1, the node would be 1 |
| instance | logging | Details of the broker server. If in the cloud, the instance type, like c5.large |
| volume | logging | Details of the broker disk drive. If in the cloud, for example gp2, io1 etc |
| filesystem | logging | Filesystem of the broker: XFS, EXT4 etc |
| hosting | logging | AWS, GCP, local etc |
| tenancy | logging | In AWS dedicated or default for example |
| core-count | logging | The number of cores available to the broker |
| threads-per-core | logging | The number of threads per core: 1 or 2 |
| metrics-influx-uri | influx | The url of the influxdb server |
| metrics-influx-user | influx | The influxdb user |
| metrics-influx-password | influx | The influxdb password |
| metrics-influx-database | influx | The influxdb database |
| metrics-influx-interval | influx | The interval to post metrics to influx |
| broker-hosts | broker | The broker hostnames or IP addresses, comma separated |
| broker-mgmt-port | broker | The broker management plugin port |
| broker-port | broker | The broker amqp port |
| broker-user | broker | The broker user |
| broker-password | broker | The broker password |
| broker-vhost | broker | The broker virtual host |
| postgres-jdbc-url | postgres | The postgres connection url |
| postgres-user | postgres | The postgres user |
| postgres-pwd | postgres | The postgres password |

### Simple Benchmarks

Simple benchmarks do not log results to Postgres and cannot be compared against each other.

#### Running a single Simple benchmark run locally

Run a simple benchmark with the minimum arguments:

```bash
java -jar rabbittesttool-1.0-SNAPSHOT-jar-with-dependencies.jar \
--mode simple-benchmark \
--topology /path/to/topology-file \
--technology rabbitmq \
--version 3.7.15 \
--broker-hosts localhost \
--broker-mgmt-port 15672 \
--broker-port 5672 \
--broker-user guest \
--broker-password guest \
--broker-vhost benchmark
```

Run a local benchmark that publishes metrics to InfluxDB:

```bash
java -jar rabbittesttool-1.0-SNAPSHOT-jar-with-dependencies.jar \
--mode simple-benchmark \
--topology /path/to/topology-file \
--technology rabbitmq \
--version 3.7.15 \
--metrics-influx-uri http://localhost:8086 \
--metrics-influx-user amqp \
--metrics-influx-password amqp \
--metrics-influx-database amqp \
--metrics-influx-interval 10 \
--broker-hosts localhost \
--broker-mgmt-port 15672 \
--broker-port 5672 \
--broker-user guest \
--broker-password guest \
--broker-vhost benchmark
```

Alternatively, we can create a JSON file to store values that never change.

```json
{
  "metrics-influx-uri": "http://localhost:8086",
  "metrics-influx-user": "amqp",
  "metrics-influx-password": "amqp",
  "metrics-influx-database": "amqp",
  "metrics-influx-interval": "10",
  "broker-hosts": "localhost",
  "broker-mgmt-port": "15672",
  "broker-port": "5672",
  "broker-user": "guest",
  "broker-password": "guest",
  "broker-vhost": "benchmark",
  "postgres-jdbc-url": "jdbc:postgresql://localhost:5432/amqpbenchmarks",
  "postgres-user": "postgres",
  "postgres-pwd": "docker"
}
```

Then run the Java program with less arguments:

```bash
java -jar rabbittesttool-1.0-SNAPSHOT-jar-with-dependencies.jar \
--mode simple-benchmark \
--topology /path/to/topology-file \
--technology rabbitmq \
--version 3.7.15 \
--config-file /path/to/config-file.json
```

#### Running a playlist of Simple benchmarks

You can run a playlist of benchmarks by creating a playlist file in the playlist directory. See the existing playlist files for an example.

```bash
$ cd run
$ python3.6 run-simple-local-playlist.py \
--topologies-root ~/github/RabbitTestTool/deploy/topologies \
--playlist-file playlists/direct-exchange.txt \
--technology rabbitmq \
--version 3.7.15 \
--gap-seconds 30
```

### Logged Benchmarks

Logged benchmarks write a history and statistics to Postgres. This helps you keep track of benchmarks that have been run and allows for comparison of results. Logged benchmarks are primarily designed to be deployed to cloud servers.

#### Running a single Logged benchmark run locally

Run a logged benchmark with the minimum arguments (assuming that the config file includes postgres connection fields):

```bash
java -jar rabbittesttool-1.0-SNAPSHOT-jar-with-dependencies.jar \
--mode logged-benchmark \
--topology /path/to/topology-file \
--technology rabbitmq \
--version 3.7.15 \
--config-file /path/to/config-file.json \
--run-id 12345 \
--run-tag 98765 \
--config-tag c1 \
--node 1 \
--instance local \
--volume local \
--volume-size 1000 \
--filesystem ext4 \
--tenancy local \
--hosting local \
--core-count 2 \
--threads-per-core 2
```

### Running a playlist of Logged benchmarks

You can run a playlist of Logged benchmarks by creating a playlist file in the playlist directory. See the existing playlist files for an example.

```bash
python3.6 run-logged-local-playlist.py \
--topologies-root ~/github/RabbitTestTool/deploy/topologies \
--playlist-file playlists/direct-exchange.txt \
--config-file /path/to/config-file.json \
--technology rabbitmq \
--version 3.7.15 \
--gap-seconds 30
```

### Logged Benchmark Deployment and Execution on AWS

The run-logged-aws-playlist.py and run-logged-aws-side-by-side-playlist.py are the easiest to use. Each one deploys new EC2 instances for the brokers and benchmark programs, runs the benchmarks and then terminates all instances.

The run-logged-aws-side-by-side-playlist.py script takes arguments for two brokers, each with a different configuration, which allows you to compare two different broker technologies and/or configurations.

Brokers are numbered and in single run each broker gets a unique ordinal suffix, for example:

- rabbitmq1
- rabbitmq2
- rabbitmq3

Note that in a side-by-side run, each configuration enumerates its broker numbers like this:

| Config 1 | Config 2 |
| --- | --- |
| 1 | 2 |
| 3 | 4 |
| 5 | 6 |
| 7 | 8 |
| 9 | 10 |
| ... | ... |

So, in a side-by-side run with --parallel 3, if c1 was RabbitMQ 3.7.15 and c2 was RabbitMQ 3.7.16 then we'd see:

| Config 1 | Config 2 |
| --- | --- |
| rabbitmq1 | rabbitmq2 |
| rabbitmq3 | rabbitmq4 |
| rabbitmq5 | rabbitmq6 |

#### Arguments for a single benchmark (not side-by-side)

| Argument | Default | Description  |
| --- | --- | --- |
| --playlist-file | None (mandatory) | The path to the playlist file that will be run |
| --aws-config-file | None (optional) | The path to the AWS configuration file (described further below) |
| --loadgen-instance | None (mandatory) | The EC2 instance type that the benchmark Java program will run on |
| --config-tag | None (mandatory) | The alias for this configuration |
| --technology | None (mandatory) | The technology being tested, rabbitmq |
| --version | None (mandatory) | The version to be deployed |
| --instance | None (mandatory) | The EC2 instance type of the broker instances |
| --volume | None (mandatory) | The volume type. When EBS must be like ebs-io1 or ebs-st1 or ebs-gp2 |
| --volume-size | None (mandatory) | The size in MB of the volume |
| --filesystem | None (mandatory) | xfs or ext4 |
| --tenancy | None (mandatory) | Default or Dedicated |
| --core-count | None (mandatory) | The number of cores will be half the vCPU count |
| --threads-per-core | None (mandatory) | Use 2 for hyperthreading or 1 with hyperthreading disabled |
| --cluster-size | 1 | The number of nodes in the cluster |
| --gap-seconds | None (mandatory) | The number of seconds between each benchmark |
| --repeat | 1 | The number of times the playlist is run sequentially. Default is once. |
| --parallel | 1 | The number of parallel executions of the playlist. Default is one. |
| --vars-file | None (optional) | A custom variables file for the Ansible provisioning script |
| --policies-file | None (optional) | The path of a json file with policies to be deployed |
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
| --no-tcp-delay | true | Whether Nagles algorithm is used or not, defaults to not, that is with no delay |

#### Notes on local storage and EBS volumes

A separate EBS volume is created and dedicated for data/logs of the broker. You can specify the size using the --volume-size argument. If the volume will be a provisioned IOPS io1 volume with 50 IOPS per GB configured.

When using c5d and z1d class instances, things work differently. An extra EBS volume is not created and mounted. Instead the NVMe local storage volume is used. The volume-size argument is used to identify the volume to be mounted, for c5d.large, the volume size is 46.6. If you set it to 50, as described in AS docs, it will fail to mount (a better way is needed of identifying the volume to mount).

#### Notes about --run_tag and --config_tag

All EC2 instances are tagged to allow automation scripts and Ansible to use these tags to get IP addresses of the servers. The tags are composed from the technology, node number and run_tag.

The run_tag is generated by the python script and is used to discriminate between the EC2 instances of other concurrent playlists being run, or run recently. One run-tag corresponds to a single EC2 instance which corresponds to an individual benchmark instance.

The config_tag acts as an alias for the various configurations (volume, instance etc) and is used primarily in side-by-side benchmarks later to simplify queries and report generation.

#### Notes on --repeat and --parallel

Due to variability of benchmark results in the cloud, multiple runs are advised. The report generation can take multiple runs and compute averages, std dev, min and max across multiple runs.

We can run a playlist multiple times either concurrently or sequentially. Because a playlist can take a long time to run, parallel execution is probably the most useful.

To set the parallelization to 5, set --parallel 5.

Be careful, this can spin up a lot of machines. With a side-by-side test with --parallel 5, you'll end up with 20 EC2 instances for a few hours. 20 because:

- 5 instances for broker config 1
- 5 instances for the benchmark program for config 1
- 5 instances for broker config 2
- 5 instances for the benchmark program for config 2

#### Notes on --new-instance-per-run

This argument for the python scripts applies to when --repeat > 1. It will deploy new EC2 instances for each repetition rather than the default of reusing them.

This slows down total run time.

#### Notes on --cluster-size

By default, only one broker is deployed per benchmark instance. But using this argument you can deploy clusters instead. The numbering of broker nodes changes when using clusters. For example, with a side-by-side run, with --parallel 2 --cluster-size1 3 --cluster-size2 3, the numbering is:

| Config 1 | Config 2 |
| --- | --- |
| 1 | 4 |
| 2 | 5 |
| 3 | 6 |
| 7 | 8 |
| 8 | 9 |
| 9 | 10 |

#### Deployments Visualized

![](https://github.com/vanlightly/rabbittesttool/blob/master/images/rabbittesttool-single.png)
Fig 1 shows a single benchmark run with --parallel 4. Each of the four instances gets its own run-tag but they share the same config-tag.

![](https://github.com/vanlightly/rabbittesttool/blob/master/images/rabbittesttool-side-by-side.png)
Fig 2 shows a side-by-side run with --parallel 2. Each of the four instances gets its own run-tag then two have config-tag c1 and the other two have config-tag c2. 

![](https://github.com/vanlightly/rabbittesttool/blob/master/images/rabbittesttool-cluster.png)
Fig 3 shows a single run with --parallel 2 and a cluster size of 3. The first benchmark VM and its three VMs share the same run-tag, the second benchmark VM and its three VMs share the same run-tag and they all share the same config-tag.

#### Notes AWS CLI and Ansible

Automation scripts use the aws cli and Ansible (using dynamic inventory).

These scripts assume either an AWS profile is set up or you have temporary credentials in the environment variables:

- AWS_ACCESS_KEY_ID
- AWS_SECRET_ACCESS_KEY
- AWS_SESSION_TOKEN

This readme does not cover usage of AWS CLI or Ansible with dynamic inventory (ec2.py, ec2.ini).

#### Notes on Influxdb

The scripts assume that influxdb is installed on an EC2 instance with the tag: inventorygroup=benchmarking_metrics. The script will not work without that and would have to be customized.

#### Single broker playlist

When running benchmarks on EC2, there are new arguments related to EC2 such as subnets and security groups. The recommended approach is to create a config file with those arguments in order to reduce the number of command line arguments.

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

Of course, for passwords you can use a more secure method, like a secret store, and pass them via command line arguments.

```bash
$ python3.6 run-logged-aws-playlist.py \
--playlist-file playlists/all-rounder.txt \
--aws-config-file /path/to/your/aws-config-file.json \
--loadgen-instance c5.xlarge \
--config-tag c1 \
--technology rabbitmq \
--version 3.7.15 \
--instance c5d.large \
--volume local-nvme \
--volume-size 46.6 \
--filesystem xfs \
--tenancy default \
--core-count 1 \
--threads-per-core 2 \
--gap-seconds 30 \
--repeat 1 \
--parallel 5
```

#### Side-by-side broker playlist

A side-by-side run takes two configurations and runs them at the same time. Each of the two configurations gets its own config-tag, in this example c1 and c2.

The list of arguments with a 1 or 2 suffix are:
- --config-tag
- --technology
- --version
- --instance
- --volume
- --volume-size
- --filesystem
- --tenancy
- --core-count
- --no-tcp-delay
- --cluster-size
- --policies-file

```bash
$ python3.6 run-logged-aws-side-by-side-playlist.py \
--playlist-file playlists/all-rounder.txt \
--aws-config-file /path/to/your/aws-config-file.json \
--loadgen-instance c5.xlarge \
--config-tag1 c1 \
--technology1 rabbitmq \
--version1 3.7.15 \
--instance1 c5d.large \
--volume1 local-nvme \
--volume-size1 46.6 \
--filesystem1 xfs \
--tenancy1 default \
--core-count1 1 \
--threads-per-core1 2 \
--config-tag2 c2 \
--technology2 rabbitmq \
--version2 3.7.16 \
--instance2 c5d.large \
--volume2 local-nvme \
--volume-size2 46.6 \
--filesystem2 xfs \
--tenancy2 default \
--core-count2 1 \
--threads-per-core2 2 \
--gap-seconds 30 \
--repeat 1 \
--parallel 5
```

### Grafana Dashboards

There are three dashboards:

- One Node
- Node A vs Node B
- Broker Server Metrics

Find the json dashboard files under deployment/grafana-dashboards.

#### One Node Dashboard

Has variables for choosing the technology and the node number. If you just ran a single benchmark then likely the node number is 1. If it was a Logged benchmark then you can find the configuration in Postgres.

#### Node A vs Node B Dashboard

This dashboard assumes you have run multiple benchmarks concurrently (either as a single benchmark run that was parallelised or a aide-by-side run). It allows you to select two benchmarks via technology and node number and show both results in each chart.

If it was a Logged benchmark run then you can find the configurations in Postgres.

#### Broker Server Metrics Dashboard

Shows the usual CPU, memory, disk and network stats sourced from the VM where the broker is running, extracted by Telegraf.

### Reports

Running the benchmark program with the --mode comparison argument will compare two runs, looking for matching topologies and comparing their results. It outputs:

- a header file listing the configurations of each run and the timestamps of each benchmark so that you can then look them up in InfluxDB.
- a csv file with various statistics per topology

The following statistics are logged to Postgres by the benchmark program at the end of each benchmark, or for a multiple step benchmark, at the end of every step:

- Avg Send Rate
- Std Dev Send Rate
- Avg Receive Rate
- Std Dev Receive Rate
- Min Latency
- 50th Latency
- 75th Latency
- 95th Latency
- 99th Latency
- 99.9th Latency

When the publisher uses confirms, the following are also logged:

- 50th Confirm Latency
- 75th Confirm Latency
- 95th Confirm Latency
- 99th Confirm Latency
- 99.9th Confirm Latency

So if a benchmark runs for 5 minutes, all the above apply to that 5 minute period.

It is assumed that usually you will be running each benchmark or playlist multiple times in parallel to account for variability and the above statistics are also recalculated in the form of:

- average
- std dev
- min
- max

So for example, for a given benchmark that was run with two configurations side by side with --parallel 5, we might have seen the following Avg Send Rate results of:

- c1 50000, c2 60000
- c1 60000, c2 70000
- c1 55000, c2 65000
- c1 52000, c2 62000
- c1 51000, c2 61000

The csv will show this as:
Topology|Topology Description|Dimensions|Step|StepValue|BenchmarkType|Duration|Measurement|C1 Runs|C2 Run|C1 Avg|C2 Avg|Change %|C1 StdDev|C2 StdDev|Change %|C1 Min|C2 Min|Change %|C1 Max|C2 Max|Change %
topology-file-name.json|The-topology-desc-here|Fixed|0|null|Througput|120|Avg Send Rate|5|5|53600|63600|18.65|4037.325848|4037.325848|0|50000|60000|20|60000|70000|16.66

```bash
java -jar rabbittesttool-1.0-SNAPSHOT-jar-with-dependencies.jar \
--mode comparison \
--report-dir /home/jack/tmp/reports \
--config-file /home/jack/editor/github/RabbitTestTool/run/.config/reports.json \
--run-id1 467efe40-930c-407d-a002-4a64f9f47e2a \
--technology1 rabbitmq \
--version1 3.7.15 \
--config-tag1 c1 \
--run-id2 826gae12-734w-909r-v235-6fgh923hd53d \
--technology2 rabbitmq \
--version2 3.7.16 \
--config-tag2 c2
```

### Model Driven Property Based Test

Using "--mode model" as an argument for the Java program will make it run as a property based test that checks the following safety properties:

- no loss of confirmed messages
- no messages delivered out of order (ignoring redelivered messages)
- no messages duplicated (ignoring redelivered messages)

The test runs a simple-benchmark with added safety property testing with one new argument, --grace-period-sec, which determines a wait period after publishers have stopped in order for consumers to receive all the messages. Sometimes publishers can get ahead of consumers and they need extra time to catch up. If the message loss property is checked too soon it will falsely identify a message loss property violation.

```bash
java -jar rabbittesttool-1.0-SNAPSHOT-jar-with-dependencies.jar \
--mode model \
--grace-period-sec 60 \
--topology /path/to/topology-file \
--technology rabbitmq \
--version 3.7.15 \
--config-file /path/to/config-file.json
```

This test mode is still extremely basic, without any additional features such as:

- starting/stopping consumers
- crashing consumers
- starting and stopping the broker
- crashing the broker
- killing TCP connections

This feature has not been thoroughly tested yet.
