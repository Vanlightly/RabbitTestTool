# Policies File Guide

A policies file allows you to define one or more policies you want for a given benchmark.

You can define variables with default values to avoid requiring multiple similar policies files.

We also can define federation configuration in thise file.

## Example of a lazy queue without variables

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

## Example of a mirrored queue with variables

```json
{
    "variables": [
        { "name": "name", "default": "mirrored-queues" },
        { "name": "pattern", "default": "" },
        { "name": "priority", "default": "0" },
        { "name": "haMode", "default": "all" },
        { "name": "haParams", "default": "0" },
        { "name": "haSyncMode", "default": "automatic" }
    ],
    "policies": [
        {
            "name": "{{ var.name }}",
            "applyTo": "queues",
            "pattern": "{{ var.pattern }}",
            "priority": "{{ var.priority }}",
            "properties" : [
                { "key": "ha-mode", "value": "{{ var.haMode }}", "type": "string" },
                { "key": "ha-params", "value": "{{ var.haParams }}", "type": "int" },
                { "key": "ha-sync-mode", "value": "{{ var.haSyncMode }}", "type": "string" }
            ]
        }
    ]
}
```

## Example of a quorum queue

Note that quorum queues cannot be created using actual RabbitMQ policies - support for quorum queues only exists within this tool.

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

## Example of a sharded quorum queue (Sharding plugin)

```json
{
    "variables": [
        { "name": "name", "default": "sharded" },
        { "name": "priority", "default": "0" },
        { "name": "shardsPerNode", "default": "1" },
        { "name": "groupSize", "default": "3" },
        { "name": "maxInMemoryLength", "default": "0" }
    ],
    "policies": [
        {
            "name": "sharded",
            "applyTo": "exchanges",
            "pattern": "^sharded$",
            "priority": "{{ var.priority }}",
            "properties" : [
                { "key": "shards-per-node", "value": "{{ var.shardsPerNode }}", "type": "int" },
                { "key": "x-queue-type", "value": "quorum", "type": "string" },
                { "key": "x-quorum-initial-group-size", "value": "{{ var.groupSize }}", "type": "int" },
                { "key": "x-max-in-memory-length", "value": "{{ var.maxInMemoryLength }}", "type": "int" }
            ]
        }
    ]
}
```

Note that the policy must be applied to a queue with the prefix value of "sharded". 

## Federation

An example of creating a federation upstream and federation policy.

```json
{
    "variables": [
        { "name": "fedPrefetch", "default": "10000" },
        { "name": "reconnectSec", "default": "5" },
        { "name": "fedAckMode", "default": "on-confirm" }
    ],
    "policies": [
        {
            "name": "fed-exchanges",
            "applyTo": "exchanges",
            "pattern": "",
            "priority": 0,
            "federation": "downstream",
            "properties" : [
                { "key": "federation-upstream-set", "value": "all", "type": "string" }
            ]
        }
    ],
    "federation": {
        "fed-prefetch-count": "{{ var.fedPrefetch }}",
        "fed-reconnect-delay-seconds": "{{ var.reconnectSec }}",
        "fed-ack-mode": "{{ var.fedAckMode }}"
    }
}
```