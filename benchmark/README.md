# RabbitTestTool Java Benchmarker

The RabbitTestTool is a Java tool for running performance and correctness experiments on RabbitMQ.

## Table of Contents

- [Concepts](#concepts)
- [Topologies and Policies](#topologies-and-policies)
    - [Example Topology and Policies Files](#example-topology-and-policies-file)
    - [Topology Basics](#topology-basics)
        - [Topology Groups](#topology-groups)
        - [Exchanges](#exchanges)
        - [Queues](#queues)
        - [Publishers](#publishers)
        - [Consumers](#consumers)
        - [Dimensions](#dimensions)
            - [Fixed dimensions](#fixed-dimensions)
            - [Single Dimension](#single-dimension)
            - [Multiple Dimensions](#multiple-dimensions)
        - [Topology files with variables](#topology-files-with-variables)
    - [Policies Basics](#policies-basics)
- [Metrics](#metrics)
- [Running RabbitTestTool](#running-rabbittesttool)
    - [Building from Source](#building-from-source)
    - [Dependencies](#dependencies)
    - [Modes](#modes)
    - [Running a Benchmark](#running-a-benchmark)
    - [Compare Benchmarks](#compare-benchmarks)
    - [Running a Model Driven Property Based Test](#running-a-model-driven-property-based-test)

## Concepts

### Topologies and Policies

The Java program requires a topology file and optionally a policies file. 

A topology file that describes the virtual hosts, exchanges, queues, bindings, publishers and consumers. Additionally it can describe dimensions by which to scale out the test in a series of steps, for example, by increasing the publish rate or the message size.

Each topology defines one of three benchmark types:

- throughput
- latency
- stress

These are labels only, used as a reminder when interpreting the results later.

In general, latency tests do not make brokers reach 100% capacity, they are rate limited to avoid that. Throughput tests may have high rate limits or not rate limited at all as they are designed to test the maximum throughput possible for a given configuration. Stress tests place more load on a broker than it can handle.

Each topology has two main components:

- list of topology groups which describe the initial state (exchanges, queues etc)
- the dimensions

Each topology group is a self contained topology of publishers, exchanges, queues and consumers. You add add many groups and scale out each group by either making the groups larger or creating many copies of a group.

You can also supply a JSON file with a list of policies to be applied.

### Example Topology and Policies File

A topology file does not specify urls, users etc, just the exchanges, queues, publishers, consumers and any variable dimensions.

```json
{
    "topologyType": "SingleDimension",
    "benchmarkType": "throughput",
    "topologyGroups": [
        {
            "name": "benchmark",
            "exchanges": [
                {
                    "name": "ex1",
                    "type": "fanout"
                }
            ],
            "queues": [
                {
                    "prefix": "q1",
                    "scale": 1,
                    "bindings": [
                        { "from": "ex1" }
                    ]
                }
            ],
            "publishers": [
                {
                    "prefix": "p1",
                    "scale": 1,
                    "sendToExchange": {
                        "exchange": "ex1",
                        "routingKeyMode": "none"
                    },
                    "deliveryMode": "Persistent",
                    "messageSize": 16
                }
            ],
            "consumers": [
                {
                    "prefix": "c1",
                    "scale": 1,
                    "queuePrefix": "q1"
                }
            ]
        }
    ],
    "dimensions" : {
        "singleDimension": {
            "dimension": "messageSize",
            "values": [16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384, 32768, 65536, 131072, 262144, 524288, 1048576],
            "stepDurationSeconds": 60,
            "rampUpSeconds": 10
        }
    }
}
```

An policies file that defines all queues as lazy.

```json
{
    "policies": [
        {
            "name": "lazy-queues",
            "applyTo": "queues",
            "pattern": "",
            "priority": 0,
            "properties" : [
                { "key": "queue-mode", "value": "lazy", "type": "string" }
            ]
        }
    ]
}
```

You can define almost anything you need with a topology and policies file.

### Topology Basics

#### Topology Groups

One or more topology groups must be defined. Usually only a single topology group is needed. Each topology group consists of exchanges, queues, consumers and publishers.

A topology group can also be scaled out within a virtual host or across multiple virtual hosts. 

Because we can scale out an individual queue and also scale out an entire topology group, we need a naming scheme. Depending on whether we scale out inside a single vhost or scale out across vhosts, we get different vhost and queue names.

This topology group:

```json
{
    "name": "benchmark",
    "scale": "2",
    "scaleType": "single-vhost",
    "queues": [ 
        { 
            "prefix": "myqueue", 
            "scale": "3"
        },
        { 
            "prefix": "myotherqueue", 
            "scale": "1"
        } 
    ]
}

```

produces this naming (`sn` stands for scale number and is used to differentiate scaled queue instances within the same vhost):

- vhost: benchmark
    - myqueue-sn1_00001
    - myqueue-sn1_00002
    - myqueue-sn1_00003
    - myotherqueue-sn1_00001
    - myqueue-sn2_00001
    - myqueue-sn2_00002
    - myqueue-sn2_00003
    - myotherqueue-sn2_00001

This topology group that scales out over vhosts:

```json
{
    "name": "benchmark",
    "scale": "2",
    "scaleType": "multiple-vhosts",
    "queues": [ 
        { 
            "prefix": "myqueue", 
            "scale": "3"
        },
        { 
            "prefix": "myotherqueue", 
            "scale": "1"
        } 
    ]
}

```

produces this naming:

- vhost: benchmark00001
    - myqueue_00001
    - myqueue_00002
    - myqueue_00003
    - myotherqueue_00001
- vhost: benchmark00002
    - myqueue_00001
    - myqueue_00002
    - myqueue_00003
    - myotherqueue_00001

The queue naming convention is: `queuePrefix-scaleNumber_queueOrdinal`

#### Exchanges

The exchanges are defined in a JSON array, such as:

```json
"exchanges": [
      { "name": "ex1", "type": "topic" },
      { "name": "ex2", "type": "fanout" }
    ]
```

#### Queues

A queue is defined by its prefix, scale and bindings. The prefix acts as both an identifier for the queue but also for naming when scaling out.

For example, queue q1 consists of a single queue with a binding to the topic exchange ex1. Queue q2 is scaled out to become 10 queues, each bound to fanout exchange ex2.

Queues are named **prefix_scaleNumber_ordinal**, for example:

```json
{
    "name": "benchmark",
    "queues": [
        {
            "prefix": "q1",
            "scale": 1,
            "bindings": [
                { "from": "ex1", "bindingKeys": ["my.*.key"] }
            ]
        },
        {
            "prefix": "q2",
            "scale": 5,
            "bindings": [
                { "from": "ex2" }
            ]
        }
    ]
}
```

creates:

- vhost: benchmark
    - q1_sn1_00001
    - q2_sn1_00001
    - q2_sn1_00002
    - q2_sn1_00003
    - q2_sn1_00004
    - q2_sn1_00005

#### Publishers

A publisher is defined by its prefix, scale and a number of publish specific properties. Just like queues, publishers can also be scaled out.

For example, the publisher below publishes to the ex1 exchange with each message having a randomly selected routing key from the 5 keys defined.

```json
"publishers": [
      {
        "prefix": "p1",
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

#### Consumers

A consumer is defined by its prefix, scale and a number of consumer specific properties. Just like queues, consumers can also be scaled out.

For example, the consumer below will consume from the queues of queue prefix q1, using manual acks with a prefetch of 1000 messages and will acknowledge every 100th message with the multiple flag. The consumer is scaled out to 2 instances.

```json
"consumers": [
        {
            "prefix": "c1",
            "ackMode": {
                "manualAcks": true,
                "consumerPrefetch": 1000,
                "ackInterval": 100
            },
            "scale": 2,
            "queuePrefix": "q1"
        }
    ]
```

A set of consumer will be spread across the queues of the specified queuePrefix, with each consumer only ever consuming a single queue. For example, with a set of 5 queues a set of 10 consumers, each queue will be consumed by 2 consumers. Likewise, with a set of 10 queues and a set of 5 consumers, we'll have 5 queues unconsumed.

When a queue is scaled out as a single dimension, any consumers that consume it will not change accordingly, meaning that new queues will remain unconsumed. In order to scale out a queue and have the new queues consumed from, you must also scale out the consumer. See Multiple Dimensions below for an example.

#### Dimensions

A single run has either a fixed topology, or it can modify one or more dimensions. The currently supported dimensions are:

| Dimension | Description |
| --- | --- |
| PublishRate | Target publish rate per second per publisher |
| MessageSize | The message size in bytes |
| HeadersPerMessage | The number of message headers to send with each message |
| RoutingKeyIndex | The routing key to send by its index (in the array "routingKeys" in the publisher config) |
| InFlightLimit | Publisher in-flight message limit (number of unconfirmed messages) |
| Prefetch | Consumer prefetch count per consumer |
| AckInterval | Acknowledge every nth message |
| AckIntervalMs | Acknowledge every nth ms |
| Publishers | Publisher count |
| Consumers | Consumer count |
| Queues | Queue count |
| ProcessingMs | The number of milliseconds it takes each consumer to process each message |

Dimensions can be scaled out globally or just target a single prefix. So if we have two publisher prefixes, we can scale out the message size of just one publisher prefix, or all publisher prefixes.

##### Fixed dimensions

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

##### Single Dimension

When we define a single dimension, we specify the name, the values and the step duration. Each step is its own benchmark and will log its metrics as a separate benchmark which can be queried later.

For example, this dimension is applied to a single publisher prefix and affects the target publish rate per publisher. Each value constitutes a step with a duration of 60 seconds, with 10 seconds before each step to allow for fluctuations and for the publishing/consuming rate to stabilize before recording statistics. This test consists of 11 steps with each step consisting of 10 + 60 seconds.

```json
"dimensions" : {
    "singleDimension": {
        "dimension": "PublishRate",
        "values": [1, 5, 10, 50, 100, 500, 1000, 5000, 10000, 15000, 20000],
        "stepDurationSeconds": 60,
        "rampUpSeconds": 10,
        "applyToPrefix": "p1"
    }
}
```

##### Multiple Dimensions

Just like with single dimensions, we define the dimension names, values and step duration/ramp up.

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
        "applyToPrefix": "c1"
    }
}
```

This example shows the scaling out of a queue and consumer together:

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

#### Topology files with variables

We can specify variables with default values in our topology files. This allows us to override variables via the command line arguments. 

```json
{
  "topologyType": "fixed",
  "benchmarkType": "{{ var.benchmarkType }}",
  "variables": [
    { "name": "benchmarkType", "default": "throughput" },
    { "name": "groupScale", "default": "1" },
    { "name": "scaleType", "default": "single-vhost" },
    { "name": "queueCount", "default": "1" },
    { "name": "publisherCount", "default": "1" },
    { "name": "consumerCount", "default": "1" },
    { "name": "messageSize", "default": "20" },
    { "name": "publishRate", "default": "0"},
    { "name": "durationSeconds", "default": "120" }
  ],
  "topologyGroups": [
    {
      "name": "benchmark",
      "scale": "{{ var.groupScale }}",
      "scaleType": "{{ var.scaleType }}",
      "exchanges": [ { "name": "ex1", "type": "fanout" }],
      "queues": [ 
        { "prefix": "q1", 
          "scale": "{{ var.queueCount }}", 
          "bindings": [{ "from": "ex1" }],
        } 
      ],
      "publishers": [
        {
          "prefix": "p1",
          "scale": "{{ var.publisherCount }}",
          "sendToExchange": {
            "exchange": "ex1",
            "routingKeyMode": "none"
          },
          "deliveryMode": "{{ var.deliveryMode }}",
          "messageSize": "{{ var.messageSize }}",
          "msgsPerSecondPerPublisher": "{{ var.publishRate }}"
        }
      ],
      "consumers": [ 
        { 
          "prefix": "c1", 
          "scale": "{{ var.consumerCount }}", 
          "queuePrefix": "q1",
        } 
      ]
    }
  ],
  "dimensions" : {
    "fixedDimensions": {
      "durationSeconds": "{{ var.durationSeconds }}",
      "rampUpSeconds": 10
    }
  }
}
```

Then we can override variable defaults from the command line using the "tvar" prefix:

```bash
java -jar rabbittesttool-1.1-SNAPSHOT-jar-with-dependencies.jar \
--mode benchmark \
--topology path/to/topology-file.json \
...
--tvar.messageSize 1024 \
--tvar.durationSeconds 600
```

### Policies Basics

We can specify multiple policies in a single file. The below is an example.

```json
{
    "policies": [
        {
            "name": "policy1",
            "applyTo": "queues",
            "pattern": "^pat1",
            "priority": 0,
            "properties" : [
                { "key": "queue-mode", "value": "lazy", "type": "string" }
            ]
        },
        {
            "name": "policy2",
            "applyTo": "queues",
            "pattern": "^pat2",
            "priority": 0,
            "properties" : [
                { "key": "ha-mode", "value": "exactly", "type": "string" },
                { "key": "ha-params", "value": "2", "type": "int" },
                { "key": "ha-sync-mode", "value": "automatic", "type": "string" }
            ]
        }
    ]
}
```

Unlike RabbitMQ itself, we can declare queues to be quorum queues via a policy file. The below example uses the variables feature:

```json
{
    "variables": [
        { "name": "name", "default": "quorum-queues" },
        { "name": "pattern", "default": "" },
        { "name": "priority", "default": "0" },
        { "name": "groupSize", "default": "3" },
        { "name": "maxInMemoryLength", "default": "0" }
    ],
    "policies": [
        {
            "name": "{{ var.name }}",
            "applyTo": "queues",
            "pattern": "{{ var.pattern }}",
            "priority": "{{ var.priority }}",
            "properties" : [
                { "key": "x-queue-type", "value": "quorum", "type": "string" },
                { "key": "x-quorum-initial-group-size", "value": "{{ var.groupSize }}", "type": "int" },
                { "key": "x-max-in-memory-length", "value": "{{ var.maxInMemoryLength }}", "type": "int" }
            ]
        }
    ]
}
```

Variables can be overriden using the "pvar" prefix on command line arguments when running the RabbitTestTool. For example:

```bash
java -jar rabbittesttool-1.1-SNAPSHOT-jar-with-dependencies.jar \
--mode benchmark \
--policies path/to/policy-file.json \
...
--pvar.groupSize 5 \
--pvar.maxInMemoryLength 10000
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
- Blocked/unblocked connections 
- Number of publishers
- Number of consumers
- Number of queues
- Consumer prefetch
- Consumer ack interval
- Max ms consumer ack interval
- Delivery mode
- Acknowledgement rate
- Messages per Acknowledgement (multiple flag usage)
- Publisher fairness (do all publishers of a given prefix manage the same rate?)

In addition, if the deployment scripts are used, then host metrics (CPU, Disk IO, Memory, Network etc) are also published to InfluxDB. JSON files for Grafana dashboards are also available.

See the TOPOLOGY.md for greater detail.

## Running RabbitTestTool

### Building from Source

To build the JAR file:

./mvnw clean package

Creates a single JAR file called rabbittesttool-*version*-jar-with-dependencies.

### Dependencies

Optionally the benchmark will:

- post metrics to an instance of InfluxDB
- log meta data and aggregated metrics to PostgreSQL

In the future these may be abstracted to allow any time series database and any RDBMS.

### Modes

There are three modes available. 

Modes:

- benchmark: Runs a benchmark.
- comparison: Generates a report that compares to runs
- model: Tests for availability, data loss, message ordering and message duplication.

### Running a Benchmark

There are many arguments and they control the behaviour of the benchmark as well as:

 - connection details for the broker
 - connection details for PostgreSql if used
 - connection details for InfluxDb if used
 - logging of the benchmark configuration (the hardware used mostly)
 - tagging of metrics

Arguments can be supplied from the command line, a config file or a mix of both.

Arguments used for logging and tagging do not affect behaviour. For example, specifying the version of RabbitMQ does not affect the behaviour of the benchmark itself, only the meta data logged about the benchmark.

Run a benchmark using the "--mode benchmark" argument.

| Argument | Usage | Required | Description |
| --- | --- | --- | --- |
| config-file | | Optional | File path tp config file. An alternate source for all argument values. The fields in the JSON file must be the same as the command line but without --. For example: {"broker-hosts": "localhost"} |
| topology | behaviour | Mandatory | The path to the topology file |
| policies | behaviour | Optional | The path to the policies file |
| broker-hosts | broker | Mandatory | The broker hostnames or IP addresses, comma separated |
| broker-mgmt-port | broker | Mandatory | The broker management plugin port |
| broker-port | broker | Mandatory | The broker amqp port |
| broker-user | broker | Mandatory | The broker user |
| broker-password | broker | Mandatory | The broker password |
| run-ordinal | logging | Optional | If this benchmark is part of a wider set of benchmarks, this sets where this benchmark sits in the set. Used later to be able to identify and compare the same benchmark in different runs. Defaults to 1. See comparison mode. |
| technology | logging | Optional | The broker technology being tested, defaults to RabbitMQ. |
| version | logging | Optional | The broker version. |
| instance | logging | Optional | Details of the broker server. If in the cloud, the instance type, like c5.large |
| volume | logging | Optional | Details of the broker disk drive. If in the cloud, for example gp2, io1 etc |
| filesystem | logging | Optional | Filesystem of the broker: XFS, EXT4 etc |
| hosting | logging | Optional | AWS, GCP, local etc |
| tenancy | logging | Optional | In AWS dedicated or default for example |
| core-count | logging | Optional | The number of cores available to the broker |
| threads-per-core | logging | Optional | The number of threads per core: 1 or 2 |
| config-tag | logging | Mandatory with use of PostgreSQL | An alias for the configuration as a whole. Required when comparing two concurrent benchmarks with different configurations. |
| run-id | logging | Mandatory with use of PostgreSQL | A unique id for this run. |
| run-tag | logging | Mandatory with use of PostgreSQL | Used for cloud deployments, can be set to anything when running locally |
| benchmark-tags | logging | Optional | Add some tags to the meta data stored about this benchmark |
| metrics-influx-uri | influx | Optional | The url of the influxdb server |
| metrics-influx-user | influx | Optional | The influxdb user |
| metrics-influx-password | influx | Optional | The influxdb password |
| metrics-influx-database | influx | Optional | The influxdb database |
| metrics-influx-interval | influx | Optional | The interval to post metrics to influx |
| postgres-jdbc-url | postgres | Optional | The postgres connection url |
| postgres-user | postgres | Optional | The postgres user |
| postgres-pwd | postgres | Optional | The postgres password |


Run a benchmark with the minimum arguments:

```bash
java -jar rabbittesttool-1.1-SNAPSHOT-jar-with-dependencies.jar \
--mode benchmark \
--topology /path/to/topology-file \
--technology rabbitmq \
--version 3.7.15 \
--broker-hosts localhost:5672 \
--broker-mgmt-port 15672 \
--broker-port 5672 \
--broker-user guest \
--broker-password guest 
```

Run a benchmark that publishes metrics to InfluxDB:

```bash
java -jar rabbittesttool-1.1-SNAPSHOT-jar-with-dependencies.jar \
--mode benchmark \
--topology /path/to/topology-file \
--technology rabbitmq \
--version 3.7.15 \
--broker-hosts localhost:5672 \
--broker-mgmt-port 15672 \
--broker-port 5672 \
--broker-user guest \
--broker-password guest \
--metrics-influx-uri http://localhost:8086 \
--metrics-influx-user amqp \
--metrics-influx-password amqp \
--metrics-influx-database amqp \
--metrics-influx-interval 10
```

Run a benchmark that publishes metrics to InfluxDB and logs all runs to PostgreSQL:

```bash
java -jar rabbittesttool-1.1-SNAPSHOT-jar-with-dependencies.jar \
--mode benchmark \
--topology /path/to/topology-file \
--technology rabbitmq \
--version 3.7.15 \
--broker-hosts localhost:5672 \
--broker-mgmt-port 15672 \
--broker-port 5672 \
--broker-user guest \
--broker-password guest \
--metrics-influx-uri http://localhost:8086 \
--metrics-influx-user amqp \
--metrics-influx-password amqp \
--metrics-influx-database amqp \
--metrics-influx-interval 10 \
--postgres-jdbc-url jdbc:postgresql://localhost:5432/amqpbenchmarks \
--postgres-user postgres \
--postgres-pwd supersecret
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
  "postgres-pwd": "supersecret"
}
```

Then run the Java program with less arguments:

```bash
java -jar rabbittesttool-1.1-SNAPSHOT-jar-with-dependencies.jar \
--mode benchmark \
--topology /path/to/topology-file \
--technology rabbitmq \
--version 3.7.15 \
--config-file /path/to/config-file.json
```

### Compare Benchmarks

You can generate a CSV with statistical comparison of two sets of benchmarks using the "comparison" mode.

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
|Topology|Topology Description|Dimensions|Step|StepValue|BenchmarkType|Duration|Measurement|C1 Runs|C2 Run|C1 Avg|C2 Avg|Change %|C1 StdDev|C2 StdDev|Change %|C1 Min|C2 Min|Change %|C1 Max|C2 Max|Change %|
| -- | -- | -- | -- | -- | -- | -- | -- |  -- | -- | -- | -- | -- | -- | -- |  -- | -- | -- | -- | -- | -- | -- |
|topology-file-name.json|The-topology-desc-here|Fixed|0|null|Throughput|120|Avg Send Rate|5|5|53600|63600|18.65|4037.325848|4037.325848|0|50000|60000|20|60000|70000|16.66|

We compare a set of benchmarks by matching results based on:

- run-id
- config-tag
- run-ordinal

As an example, using the AWS Python based orchestration, we can run a side-by-side playlist of benchmarks where multiple benchmarks are run under the same run id. Using this orchrestration we could run 10 different benchmarks with two different configurations:

 - 3 parallel runs of 10 benchmarks with config-tag c1 run against RabbitMQ brokers of version 3.7.17
 - 3 parallel runs of 10 benchmarks with config-tag c2 run against RabbitMQ brokers of version 3.8.1
 
All those benchmarks share the same run id (as they were run at the same time by the same orchestration - see the orchestration readme).

Then we run comparison mode:

```bash
java -jar rabbittesttool-1.1-SNAPSHOT-jar-with-dependencies.jar \
--mode comparison \
--config-file path/to/reports.json \
--report-dir ~/tmp/reports \
--run-id1 6d89c595-ad63-4f5b-8466-fd1f922a5baa \
--technology1 rabbitmq \
--version1 3.7.16 \
--config-tag1 c1 \
--run-id2 6d89c595-ad63-4f5b-8466-fd1f922a5baa \
--technology2 rabbitmq \
--version2 3.8.1 \
--config-tag2 c2 \
--desc-vars publisherCount,publishRate,queueCount,consumerCount
```

Note that the `--desc-vars` argument exists to select which variables to add to the description column of the generated csv. 

The above omitted the PostgreSQL connection details by specifying a config file.

```json
{
  "postgres-jdbc-url": "jdbc:postgresql://localhost:5432/amqpbenchmarks",
  "postgres-user": "postgres",
  "postgres-pwd": "supersecret"
}
```

The output is a CSV file with the results. In the example we have c1 vs c2 with 3 parallel runs of each of the 10 benchmarks. The statistical analysis includes for each benchmark:
- c1 vs c2 send rate (average, min, max and std dev)
- c1 vs c2 consume rate (average, min, max and std dev)
- c1 vs c2 end-to-end latency (average, min, max and std dev)
- c1 vs c2 confirm latency (average, min, max and std dev)

Additionally, because both c1 and c2 were also run in parallel the results also include the min, average, max and std dev of the above results. This allows you to identify whether results are produced reliably (with little variance) or produced with high amounts of variability.

### Running a Model Driven Property Based Test

Using "--mode model" as an argument for the Java program will run a benchmark as normal but also make it run as a model driven property-based test that checks the following safety properties:

- no loss of consumer availability
- no loss of confirmed messages
- no messages delivered out of order (ignoring redelivered messages)
- no messages duplicated (ignoring redelivered messages)

This allows you to do:
- perform test runs of an upgrade process or blue/green deployment and ensure no dataloss or loss of availability
- get confidence that your RabbitMQ installation does not lose data while under a stress test
- allows the RabbitMQ team to perform additional correctness checking

Recent improvements to the model allow for tests that last days.

The test runs a benchmark with added safety property testing with two new arguments:

| Argument | Values | Description |
| -- | -- | -- |
| `--grace-period-sec` | int | Determines a rolling wait period after publishers have stopped in order for consumers to receive all the messages. Sometimes publishers can get ahead of consumers and they need extra time to catch up. If the message loss property is checked too soon it will falsely identify a message loss property violation. |
| `--unavailability-sec` | int | Determines the time threshold that counts as unavailability. For example, when set to 20 seconds, if a consumer is unable to consume messages for 15 seconds then that does not acount as an unavailability period, but if it was unable to consume for 25 seconds then an unavailability period would be logged. |
| `--checks` | Comma separated. Possible checks: `dataloss,duplicates,ordering,availability` (or `all`) | Determines which checks to perform. By default it will check for data loss, duplication and availability. Message ordering needs to be enalbed explicitly because not all workloads allow for ordered delivery. For example, two competing consumers on the same queue will not necessarily process the messages in a total order. |

```bash
java -jar rabbittesttool-1.1-SNAPSHOT-jar-with-dependencies.jar \
--mode model \
--topology /path/to/topology-file \
--grace-period-sec 60 \
--unavailability-sec 30 \
--checks all
... see normal benchmark arguments
```