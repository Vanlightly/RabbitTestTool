# Topology Guide

A topology file defines the virtual hosts, exchanges, queues, publishers and consumers of a benchmark. It can optionally also define one or more scaling dimensions. Topology files do not reference any particular AMQP technology or connection details.

A large number of topologies are already defined in the deploy/topologies folder.

Each benchmark should be oriented towards testing throughput, latency or stress testing. Latency numbers are not as useful when running a broker at its limit. These numbers have some utility, but mostly when testing latency, we want the broker to not be at 100% capacity. For this reason latency tests should use publisher rate limiting, whereas throughput tests usually don't specify a rate limit.

An example topology file with one publisher, one exchange, one queue and one consumer.

```json
{
    "topologyName": "TP_FanoutBenchmark1",
    "topologyType": "fixed",
    "benchmarkType": "throughput",
    "description": "One pub/queue/con, 16 byte msg",
    "vhosts": [
        {
            "name": "benchmark",
            "exchanges": [
                { "name": "ex1", "type": "fanout" }
            ],
            "queueGroups": [
                { "group": "q1", "scale": 1, "bindings": [{ "from": "ex1"}] }
            ],
            "publisherGroups": [
                {
                    "group": "p1",
                    "scale": 1,
                    "sendToExchange": {
                    "exchange": "ex1",
                    "routingKeyMode": "none"
                    },
                    "deliveryMode": "Persistent"
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
        "fixedDimensions": {
            "durationSeconds": 120,
            "rampUpSeconds": 10
        }
    }
}
```

## Topology Reference

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| topologyName | yes | - | The name of the topology |
| description | no | - | A very short description of the topology. Useful when reviewing benchmark reports. |
| topologyType | yes | - | Either FixedDimensions, SingleDimension or MultipleDimensions |
| benchmarkType | yes | - | Either throughput, latency or stress. This information is only used to indicate to others the intent of the test and to show in reports. When we see that a test is a throughput test, we know latency numbers may not be relevant. |
| vhosts | yes | - | The list of virtual hosts with the initially deployed artefacts. See Virtual Hosts. |
| dimensions | yes | - | The dimensions of the topology. See Dimensions. |

### Virtual Hosts

At least one virtual host must be defined. Each virtual host consists of a name, optionally a scale and the list of exchanges, queues, consumers and publishers.

Underscores ARE NOT supported in virtual host names.

```json
"vhosts": [
    {
        "name": "test",
        "scale": 1,
        "exchanges": [],
        "queueGroups": [],
        "publisherGroups": [],
        "consumerGroups": []
    }
]
```

A virtual host can be scaled out. For example, when the virtual host "test" has its "scale" field set to 5, five copies (with all its exchanges, queue s etc) are declared, with the names:

- test00001
- test00002
- test00003
- test00004
- test00005

#### Exchange Fields

Exchanges is a JSON array of exchange objects.

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| name | yes | - | The name of the exchange |
| type | yes | - | Fanout, Direct, Topic, Headers or ConsistentHash |
| bindings | no | - | An array of bindings. See Binding Fields |

Example of two exchanges.

```json
"exchanges": [
    { "name": "ex1", "type": "fanout" },
    { "name": "ex2", "type": "topic" }
]
```

Example of two exchanges, with an exchange to exchange binding.

```json
"exchanges": [
    { "name": "ex1", "type": "topic" },
    { "name": "ex2", "type": "fanout", "bindings": [ { "from": "ex1", "bindingKey": "error.#" }] }
]
```

#### Binding Fields

Bindings can bind a queue to an exchange, or an exchange to an exchange.

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| from | yes | - | The name of exchange to bind to |
| bindingKey | no | - | When binding to a direct or topic exchange, the routing key or routing key pattern. Use the value "self" to set the binding key as the name of the queue/exchange. This is useful when you are scaling out a queue group and want to route based on the queue name. |
| properties | no | - | See Properties |

#### Property Fields

Bindings and queues can have optional properties.

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| key | yes | - | The property key |
| value | yes | - | The property value |
| type | yes | string | The type of value. Currently only int and string are supported |

#### Queue Group Fields

The queueGroups field is a JSON array of queueGroup objects.

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| group | yes | - | The name of the group |
| scale | yes | - | The number of queues in the group |
| properties | no | - | An array of properties. See Property Fields |
| bindings | yes | - | An array of bindings. See Binding Fields |

An example of two queue groups.

```json
"queueGroups": [
    {|"group": "q1", "scale": 1, "bindings": [ { "from": "ex1" } ]},
    {|"group": "q2", "scale": 10, "bindings": [ { "from": "ex2" } ]}
]
```

#### Publisher Group Fields

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| group | yes | - | The name of the group |
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

An example of a single publisher group with the default message size, no publishing rate limit and no publisher confirms.

```json
"publisherGroups": [
    {
        "group": "p1",
        "scale": 1,
        "sendToExchange": {
            "exchange": "ex1",
            "routingKeyMode": "none"
        },
        "deliveryMode": "Persistent"
    }
]
```

An example of a single publisher group with the a message size, publishing rate limit and publisher confirms.

```json
"publisherGroups": [
    {
        "group": "p1",
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

##### SendToQueueGroup Fields

A publisher group can publish messages to a queue group via the default exchange. Each publisher can choose a random queue each time it sends a message, or it can send a message to its "counterpart", the queue with the same number as itself. 

When using Counterpart mode, a publisher group of 3 that sends to a queue group "q1" of the same size will have publisher 1 send to queue q1_00001, publisher 2 to queue q1_00002 and publisher 3 to queue q1_00003.

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| queueGroup | yes | - | The name of the queue group to send messages to |
| mode | yes | - | Random or Counterpart |

An example of a publisher group that sends messages directly to queues of queue group q1 (via default exchange), with each message going to a randomly chosen queue of that group each time.

```json
"queueGroups": [
    {|"group": "q1", "scale": 5, "bindings": [ { "from": "ex1" } ]}
],
"publisherGroups": [
    {
        "group": "p1",
        "scale": 10,
        "sendToQueueGroup": {
            "queueGroup": "q1",
            "mode": "Random"
        },
        "deliveryMode": "Persistent"
    }
]
```

An example of a publisher group and queue group of the same scale where each publisher sends messages to the queue with the same number suffix as itself of queue group q1 (via default exchange).

```json
"queueGroups": [
    {|"group": "q1", "scale": 10, "bindings": [ { "from": "ex1" } ]}
],
"publisherGroups": [
    {
        "group": "p1",
        "scale": 10,
        "sendToQueueGroup": {
            "queueGroup": "q1",
            "mode": "Counterpart"
        },
        "deliveryMode": "Persistent"
    }
]
```

##### SendToExchange Fields

A publisher group can publish messages to a single exchange. When sending to an exchange, various routing key modes are made available, including none at all when sending messages to fanout and headers exchanges.

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

An example where a publisher group sends messages to exchange ex1 without a routing key.

```json
"publisherGroups": [
    {
        "group": "p1",
        "scale": 1,
        "sendToExchange": {
            "exchange": "ex1",
            "routingKeyMode": "none"
        },
        "deliveryMode": "Persistent"
    }
]
```

An example where a publisher group sends all messages to exchange ex1 with the same routing key value of rk1.

```json
"publisherGroups": [
    {
        "group": "p1",
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

An example where a publisher group sends messages to exchange ex1 choosing a random routing from the routingKeys array for each message.

```json
"publisherGroups": [
    {
        "group": "p1",
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
"publisherGroups": [
    {
        "group": "p1",
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
        "applyToGroup": "p1"
    }
}
```

#### PublishMode Fields

When using confirms.

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| useConfirms | no | false | True or False |
| inFlightLimit | When useConfirm is true | - | The maximum number of unconfirmed messages a single publisher can have in flight |

#### Consumer Group Fields

| Field | Mandatory | Default | Description  |
| --- | --- | --- | --- |
| group | yes | - | The name of the group |
| queueGroup | yes | - | The name of the queue group that the consumers will consume |
| scale | yes | - | The number of consumers in the group |
| frameMax | no | 0 | The frameMax setting for each consumer |
| ackMode | no | - | Settings related to acknowledgements. See AckMode Fields. When not specified, uses NoAck mode. |

An example of a consumer group of 5 consumers, consuming from queue group q1. Using NoAck mode and no prefetch.

```json
"consumerGroups": [
    {
        "group": "c1",
        "scale": 5,
        "queueGroup": "q1"
    }
]
```

An example of a consumer group with one consumer that uses manual acks with a prefetch and acks with multiple flag every 5th message.

```json
"consumerGroups": [
    {
        "group": "c1",
        "scale": 1,
        "queueGroup": "q1",
        "ackMode": {
            "manualAcks": true,
            "consumerPrefetch": 10,
            "ackInterval": 5
        }
    }
]
```

##### AckMode Fields

| Field | Mandatory | Description  |
| --- | --- | --- |
| manualAcks | yes | True makes the consumers use consumer acks, false is for NoAck |
| consumerPrefetch | yes | The prefetch count per consumer |
| ackInterval | yes | Governs the use of the multiple flag. When set to 1, each message is individually acked, when set to 5, every 5th message is acked using the multiple flag |

### Dimensions Fields

#### Fixed Dimensions Fields

| Field | Mandatory | Description  |
| --- | --- | --- |
| durationSeconds | yes | The number of seconds to run the benchmark for  |

Example:

```json
"dimensions" : {
    "fixedDimensions": {
        "durationSeconds": 60
    }
}
```

#### Single Dimension Fields

| Field | Mandatory | Description  |
| --- | --- | --- |
| dimension | yes | The name of the dimension to modify |
| values | yes | The array of values to apply to the dimension |
| stepDurationSeconds | yes | The number of seconds duration of each step |
| applyToGroup | no | The queue, consumer or publisher group to apply the modifications to |

Example:

```json
"dimensions" : {
    "singleDimension": {
        "dimension": "messageSize",
        "values": [16, 32, 64, 128, 256, 512],
        "stepDurationSeconds": 60,
        "applyToGroup": "p1"
    }
}
```

#### Multiple Dimensions Fields

| Field | Mandatory | Description  |
| --- | --- | --- |
| dimensions | yes | An array of the names of the dimensions to modify |
| multiValues | yes | The array of array of values to apply to the dimensions. |
| stepDurationSeconds | yes | The number of seconds duration of each step |

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
