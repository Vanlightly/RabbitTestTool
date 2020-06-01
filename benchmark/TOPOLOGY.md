# Topology File Guide

A topology file defines one or more topology groups (exchanges, queues, publishers and consumers) of a benchmark. It can optionally also define one or more scaling dimensions. Topology files do not reference any particular AMQP technology or connection details.

A large number of topologies are already defined in the benchmark/topologies folder and most use the variable templating feature.

Each benchmark should be oriented towards testing throughput, latency or stress testing. Latency numbers are not as useful when running a broker at its limit. These numbers have some utility, but mostly when testing latency, we want the broker to not be at 100% capacity. For this reason latency tests should use publisher rate limiting, whereas throughput tests usually don't specify a rate limit.

An example topology file with one publisher, one exchange, one queue and one consumer.

```json
{
    "topologyType": "fixed",
    "benchmarkType": "throughput",
    "topologyGroups": [
        {
            "name": "benchmark",
            "exchanges": [
                { "name": "ex1", "type": "fanout" }
            ],
            "queues": [
                { "prefix": "q1", "scale": 1, "bindings": [{ "from": "ex1"}] }
            ],
            "publishers": [
                {
                    "prefix": "p1",
                    "scale": 1,
                    "sendToExchange": {
                        "exchange": "ex1",
                        "routingKeyMode": "none"
                    },
                    "deliveryMode": "Persistent"
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
        "fixedDimensions": {
            "durationSeconds": 120,
            "rampUpSeconds": 10
        }
    }
}
```

## Templated Topology Files

You can define variables in topology files to avoid the need for many similar variants of a given topology. Each variable is defined with a default value.

The variables are defined in an array and placed in the topology using the "{{ var.variable_name }}" syntax. All values are strings as they will be interpolated into the generated json file.

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
    { "name": "deliveryMode", "default": "persistent" },
    { "name": "messageSize", "default": "20" },
    { "name": "publishRate", "default": "0"},
    { "name": "useConfirms", "default": "false" },
    { "name": "inflightLimit", "default": "0" },
    { "name": "manualAcks", "default": "false" },
    { "name": "consumerPrefetch", "default": "0" },
    { "name": "ackInterval", "default": "0" },
    { "name": "queueMode", "default": "default" },
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
          "properties": [
            { "key": "x-queue-mode", "value": "{{ var.queueMode }}", "type": "string" }
          ]
        } 
      ],
      "publishers": [
        {
          "prefix": "p1",
          "scale": "{{ var.publisherCount }}",
          "publishMode": {
            "useConfirms": "{{ var.useConfirms }}",
            "inFlightLimit": "{{ var.inflightLimit }}"
          },
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
          "ackMode": {
            "manualAcks": "{{ var.manualAcks }}",
            "consumerPrefetch": "{{ var.consumerPrefetch }}",
            "ackInterval": "{{ var.ackInterval }}"
          }
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

## Topology Reference

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| topologyType | yes | - | Either FixedDimensions, SingleDimension or MultipleDimensions |
| benchmarkType | yes | - | Either throughput, latency or stress. This information is only used to indicate to others the intent of the test and to show in reports. When we see that a test is a throughput test, we know latency numbers may not be relevant. |
| topologyGroups | yes | - | The list of topology groups with the initially deployed artefacts. See Topology Groups. |
| dimensions | yes | - | The dimensions of the topology. See Dimensions. |

### Topology Groups

At least one topology group must be defined. Each topology group consists of a name, optionally a scale and the list of exchanges, queues, consumers and publishers.

Underscores ARE NOT supported in topology group names.

Topology group can optionally be marked as either `upstream` (the default) or `downstream` via the **federation** field. This is required when benchmarking either federated exchanges, federated queue or shovels.

```json
"topologyGroups": [
    {
        "name": "test",
        "scale": 1,
        "federation": "downstream",
        "exchanges": [],
        "queues": [],
        "publishers": [],
        "consumers": []
    }
]
```

A topology group can be scaled out. For example, when the topology group "test" has its "scale" field set to 5, five copies (with all its exchanges, queue s etc) are declared. Each topology group is declared inside a virtual host of the name of the group. The group can be scaled out inside this virtual hosts, or across many virtual hosts.

This topology group:

```json
{
    "name": "test",
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

produces this naming of vhost and queues (`sn` stands for scale number and is used to differentiate scaled queue instances within the same vhost):

- vhost: test
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
    "name": "test",
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

- vhost: test00001
    - myqueue_00001
    - myqueue_00002
    - myqueue_00003
    - myotherqueue_00001
- vhost: test00002
    - myqueue_00001
    - myqueue_00002
    - myqueue_00003
    - myotherqueue_00001

#### `exchanges` Object

`exchanges` is a JSON array of exchange objects.

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| name | yes | - | The name of the exchange |
| type | yes | - | Fanout, Direct, Topic, Headers, ConsistentHash, ModulusHash |
| bindings | no | - | An array of bindings. See Binding Objects |
| shovel | no | - | See shovel field section |

Example of five different exchanges.

```json
"exchanges": [
    { "name": "ex1", "type": "fanout" },
    { "name": "ex2", "type": "topic" },
    { "name": "ex3", "type": "headers" },
    { "name": "ex4", "type": "consistenthash" },
    { "name": "ex5", "type": "modulushash" },
]
```

Example of two exchanges, with an exchange to exchange binding.

```json
"exchanges": [
    { "name": "ex1", "type": "topic" },
    { "name": "ex2", "type": "fanout", "bindings": [ { "from": "ex1", "bindingKeys": ["error.#"] }] }
]
```

Exchanges also have a `shovel` field to allow for defining a shovel destination.

#### Binding Objects

Bindings can bind a queue to an exchange, or an exchange to an exchange.

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| from | yes | - | The name of exchange to bind to |
| bindingKey | no | - | When binding to a direct or topic exchange, the routing key or routing key pattern. Use the value "self" to set the binding key as the name of the queue/exchange. This is useful when you are scaling out a queue group and want to route based on the queue name. |
| properties | no | - | See Property Objects |

#### Property Objects

Bindings and queues can have optional properties.

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| key | yes | - | The property key |
| value | yes | - | The property value |
| type | yes | string | The type of value. Currently only int and string are supported |

#### Queue Objects

The queueGroups field is a JSON array of queueGroup objects.

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| prefix | yes | - | The name of the queue prefix |
| scale | yes | - | The number of queues in the group |
| properties | no | - | An array of properties. See Property Fields |
| bindings | yes | - | An array of bindings. See Binding Fields |
| shovel | no | - | See shovel field section |

An example of two queues.

```json
"queues": [
    {"prefix": "q1", "scale": 1, "bindings": [ { "from": "ex1" } ]},
    {"prefix": "q2", "scale": 10, "bindings": [ { "from": "ex2" } ]}
]
```

Example of 10 lazy queues bound to a single exchange.

```json
"queues": [
    {
        "prefix": "q1", 
        "scale": 10, 
        "bindings": [ { "from": "ex1" } ],
        "properties": [
            { "key": "x-queue-mode", "value": "lazy", "type": "string" }
        ]
    }
]    
```

#### Publisher Fields

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| prefix | yes | - | The name of the publisher prefix |
| scale | yes | - | The number of queues in the group |
| deliveryMode | yes | - | Persistent or Transient |
| headersPerMessage | no | 0 | The number of message headers to add to each message. When > 0, the availableHeaders field must have at least one header. |
| availableHeaders | no | - | Defines the message headers that will be sent with each message. Message headers have the same fields as properties, see Property Fields |
| streams | no | 1 | A stream is a monotonically increasing number sequence. Each message of a given stream gets a value of the last message + 1. Streams are used when using the Property Based Test feature. For benchmarking, this field can be ignored. |
| messageSize | no | 16 | The minimum number of bytes is 16, which corresponds to the stream (4 bytes) / sequence number (4 bytes) /  nanosecond timestamp (8 bytes). Additional bytes are randomized. |
| msgsPerSecondPerPublisher | no | 0 | The target publish rate per publisher |
| sendToQueueGroup | no | - | See SendToQueueGroup Fields |
| sendToExchange | no | - | See SendToExchange Fields |
| publishMode | no | - | See PublishMode fields |

An example of a single publisher with the default message size, no publishing rate limit and no publisher confirms.

```json
"publishers": [
    {
        "prefix": "p1",
        "scale": 1,
        "sendToExchange": {
            "exchange": "ex1",
            "routingKeyMode": "none"
        },
        "deliveryMode": "Persistent"
    }
]
```

An example of a single publisher with the a message size, publishing rate limit and publisher confirms.

```json
"publishers": [
    {
        "prefix": "p1",
        "scale": 1,
        "publishMode": {
            "useConfirms": true,
            "inFlightLimit": 100
        },
        "sendToExchange": {
            "exchange": "ex1",
            "routingKeyMode": "none"
        },
        "deliveryMode": "Persistent",
        "messageSize": 65535,
        "msgsPerSecondPerPublisher": 100
    }
]
```

##### SendToQueuePrefix Fields

A publisher can publish messages to a queue via the default exchange. Each publisher can choose a random queue each time it sends a message, or it can send a message to its "counterpart", the queue with the same number as itself or that has a modulo of itself. For example, 10 publishers and 3 queues:

- pub1 -> q1
- pub2 -> q2
- pub3 -> q3
- pub4 -> q1
- pub5 -> q2
- pub6 -> q3
- pub7 -> q1
- pub8 -> q2
- pub9 -> q3
- pub10 -> q1

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| queuePrefix | yes | - | The name of the queue prefix to send messages to |
| mode | yes | - | Random or Counterpart |

An example of a publisher that sends messages directly to queues of queue prefix q1 (via default exchange), with each message going to a randomly chosen queue of that group each time.

```json
"queues": [
    {|"prefix": "q1", "scale": 5, "bindings": [ { "from": "ex1" } ]}
],
"publishers": [
    {
        "prefix": "p1",
        "scale": 10,
        "sendToQueuePrefix": {
            "queuePrefix": "q1",
            "mode": "Random"
        },
        "deliveryMode": "Persistent"
    }
]
```

An example of a publisher group and queue of the same scale where each publisher sends messages to the queue with the same number suffix as itself (via default exchange).

```json
"queues": [
    {|"prefix": "q1", "scale": 10, "bindings": [ { "from": "ex1" } ]}
],
"publishers": [
    {
        "prefix": "p1",
        "scale": 10,
        "sendToQueuePrefix": {
            "queuePrefix": "q1",
            "mode": "Counterpart"
        },
        "deliveryMode": "Persistent"
    }
]
```

##### SendToExchange Fields

A publisher can publish messages to a single exchange. When sending to an exchange, various routing key modes are made available, including none at all for when sending messages to fanout and headers exchanges.

The routing key modes available are:

- __None__
- __Random__. Each message is sent with a unique UUID for a routing key.
- __StreamKey__. Each message uses the number of its stream as the routing key. Only used in the Property Based Test feature.
- __FixedValue__. The routing key is the same for all messages, and given in the "routingKey" field.
- __MultiValue__. The routing key for each message is randomly chosen from an array of routing keys defined in the "routingKeys" field.
- __Index__. An array of routing keys is defined in the "routingKeys" field and the dimension RoutingKeyIndex defines which routing key is sent in each step. So we can define 5 routing keys and a single dimension which determines the routing key to be sent (using its index in the array).

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| exchange | yes | - | The name of the exchange to send messages to |
| routingKeyMode | yes | - | The routing key mode to use |
| routingKey | When FixedValue mode is used | - | The routing key to use for FixedValue mode |
| routingKeys | When MultiValue or Index mode is used | - | The routing keys available |

An example where a publisher sends messages to exchange ex1 without a routing key.

```json
"publishers": [
    {
        "prefix": "p1",
        "scale": 1,
        "sendToExchange": {
            "exchange": "ex1",
            "routingKeyMode": "none"
        },
        "deliveryMode": "Persistent"
    }
]
```

An example where a publisher sends all messages to exchange ex1 with the same routing key value of rk1.

```json
"publishers": [
    {
        "prefix": "p1",
        "scale": 1,
        "sendToExchange": {
            "exchange": "ex1",
            "routingKey": "rk1",
            "routingKeyMode": "FixedValue"
        },
        "deliveryMode": "Persistent"
    }
]
```

An example where a publisher sends messages to exchange ex1 choosing a random routing from the routingKeys array for each message.

```json
"publishers": [
    {
        "prefix": "p1",
        "scale": 1,
        "sendToExchange": {
            "exchange": "ex1",
            "routingKeys": ["rk1", "rk2", "rk3", "rk4", "rk5"],
            "routingKeyMode": "MultiValue"
        },
        "deliveryMode": "Persistent"
    }
]
```

An example of using Index routing key mode. In each step of a SingleDimension topology, a different routing key is sent. 

```json
"publishers": [
    {
        "prefix": "p1",
        "scale": 1,
        "sendToExchange": {
            "exchange": "ex1",
            "routingKeys": ["a", "a.b", "a.b.c", "a.b.c.d.e", "a.b.c.d.e"],
            "routingKeyMode": "Index"
        },
        "deliveryMode": "Persistent"
    }
]
...
"dimensions" : {
    "singleDimension": {
        "dimension": "RoutingKeyIndex",
        "values": [0, 1, 2, 3, 4],
        "stepDurationSeconds": 60,
        "rampUpSeconds": 10,
        "applyToPrefix": "p1"
    }
}
```

#### PublishMode Fields

When using confirms.

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| useConfirms | no | false | True or False |
| inFlightLimit | When useConfirm is true | - | The maximum number of unconfirmed messages a single publisher can have in flight |

#### Consumer Fields

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| prefix | yes | - | The name of the consumer prefix |
| queuePrefix | yes | - | The name of the queue prefix that the consumers will consume |
| scale | yes | - | The number of consumers in the group |
| frameMax | no | 0 | The frameMax setting for each consumer |
| ackMode | no | - | Settings related to acknowledgements. See AckMode Fields. When not specified, uses NoAck mode. |
| processingMs | no | 0 | The number of milliseconds a consumer takes to process a single message |

An example of a set of 5 consumers, consuming from queue prefix q1. Using NoAck mode and no prefetch.

```json
"consumers": [
    {
        "prefix": "c1",
        "scale": 5,
        "queuePrefix": "q1"
    }
]
```

An example of a consumer that uses manual acks with a prefetch, acks with multiple flag every 5th message and takes 10ms to process each message.

```json
"consumers": [
    {
        "prefix": "c1",
        "scale": 1,
        "queuePrefix": "q1",
        "ackMode": {
            "manualAcks": true,
            "consumerPrefetch": 10,
            "ackInterval": 5
        },
        "processingMs": 10
    }
]
```

##### AckMode Fields

| Field | Mandatory | Description  |
| --- | --- | --- |
| manualAcks | yes | True makes the consumers use consumer acks, false is for NoAck |
| consumerPrefetch | yes | The prefetch count per consumer |
| ackInterval | yes | Governs the use of the multiple flag. When set to 1, each message is individually acked, when set to 5, every 5th message is acked using the multiple flag |
| ackIntervalMs | yes | Puts an upper limit on the amount of time that can pass before sending an acknowledgement |

#### `shovel` fields

To configure a shovel, you must define it on the shovel destination which can be either an exchange or a queue.

The source can also be an exchange or queue, meaning that there are 4 combinations of source and destination.

> Note that if the parent is a queue and it has a value > 1 for `scale` then you will end up with one shovel per queue in the queue prefix. For those cases use the `counterpart` value for srcName (see below).

| Field | Mandatory | Description  |
| --- | --- | --- |
| ackMode | yes | no-ack, on-publish or on-confirm |
| prefetch | yes | the shovel prefetch |
| reconnectDelaySeconds | no | defaults to 5 seconds |
| srcBroker | yes | Either `upstream` or `downstream`. In a single cluster test this should be `upstream`, in a test with an upstream and downstream broker, you would normally set this to upstream, but there's no reason you couldn't also declare shovels that source from the downstream server |
| srcType | yes | `queue` or `exchange` |
| srcName | yes | For exchange sources, this would be the name of the source exchange. For queue sources, either specify a queue name or use the value `counterpart` to configure the shovel to use the same name as this queue. This is important when using a `scale` > 1 as a queue prefix with a scale > 1 will generate multiple queues, which will generate multiple shovels. If you use any value other than `counterpart` all the shovels will have the same source which may not be what you want. |

Example of an exchange destination and source shovel:

```json
    "exchanges": [ 
        { 
            "name": "my-downstream-exchange", 
            "type": "fanout",
            "shovel": {
                "ackMode": "on-publish",
                "prefetch": "1000",
                "reconnectDelaySeconds": "5",
                "srcBroker": "upstream",
                "srcType": "exchange",
                "srcName": "my-upstream-exchange"
            } 
        }
    ],
```

Example of an queue destination and source shovel, where the group is scaled out to 10 queues, creating 10 shovels. Each shovel has a queue with the same name as its source.

```json
"queues": [
    { "prefix": "q1",
        "scale": "3",
        "shovel": {
            "ackMode": "on-confirm",
            "prefetch": "500",
            "reconnectDelaySeconds": "10",
            "srcBroker": "upstream",
            "srcType": "queue",
            "srcName": "counterpart"
        }
    }
],
```

The above would create the following shovels:

- queue source: q1_00001, queue destination: q1_00001
- queue source: q1_00002, queue destination: q1_00002
- queue source: q1_00003, queue destination: q1_00003

### Dimensions Fields

#### Fixed Dimensions Fields

| Field | Mandatory | Description  |
| --- | --- | --- |
| durationSeconds | yes | The number of seconds to run the benchmark for  |
| rampUpSeconds | yes | The number of seconds to allow for stabilisation before recording statistics  |

Example:

```json
"dimensions" : {
    "fixedDimensions": {
        "durationSeconds": 60,
        "rampUpSeconds": 10
    }
}
```

#### Single Dimension Fields

| Field | Mandatory | Description  |
| --- | --- | --- |
| dimension | yes | The name of the dimension to modify |
| values | yes | The array of values to apply to the dimension |
| stepDurationSeconds | yes | The number of seconds duration of each step |
| applyToPrefix | no | The queue, consumer or publisher prefix to apply the modifications to |
| rampUpSeconds | yes | The number of seconds to allow for stabilisation before recording statistics  |
| repeatSeries | no | The number of time to repeat the whole series of steps |

Example:

```json
"dimensions" : {
    "singleDimension": {
        "dimension": "messageSize",
        "values": [16, 32, 64, 128, 256, 512],
        "stepDurationSeconds": 60,
        "rampUpSeconds": 10,
        "applyToPrefix": "p1"
    }
}
```

#### Multiple Dimensions Fields

| Field | Mandatory | Description  |
| --- | --- | --- |
| dimensions | yes | An array of the names of the dimensions to modify |
| multiValues | yes | The array of array of values to apply to the dimensions. |
| stepDurationSeconds | yes | The number of seconds duration of each step |
| rampUpSeconds | yes | The number of seconds to allow for stabilisation before recording statistics  |


Example:

```json
"dimensions" : {
    "multipleDimensions": {
        "dimensions": ["Queues","Consumers","Publishers"],
        "multiValues": [
            [1,1,2],
            [2,2,4],
            [3,3,6],
            [4,4,8],
            [5,5,10]
        ],
        "stepDurationSeconds": 30
    }
}
```
