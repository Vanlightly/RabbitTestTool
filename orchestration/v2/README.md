# Orchestration Guide

Deploys systems and executes benchmarks over those systems.

It can run a series of benchmarks over 1 to N systems at the same time. It can also deploy each system between 1 and 5 times so that multiple results can be obtained for each system-benchmark combination, which can be important for detecting and accounting for variance.

## Terms:

A playlist is a set of systems and a suite of benchmarks, run as a unit.

A system is:

- a host (EC2, GKE or EKS)
- Hardware
- OS (when using EC2)
- RabbitMQ and Erlang
- RabbitMQ configuration

A benchmark is a recorded workload:

- metrics sent to InfluxDB
- benchmark configuration recorded to PostgreSQL

A workload is:

- the publishers, consumers, exchanges, queues etc
- any traffic control
- any changes to RabbitMQ configuration

## API

The main API is a set of YAML files which model the tests. The root file is the playlist file which defines the systems and benchmarks to run.

Locations:

Playlists: 
- configuration/playlists
    - ... playlist files

Systems:

- configuration/systems
    - /ec2
        - /base (contains definitions for instances, volume configurations and defaults)
        - ... system files
    - /eks
        - /base
        - ... system files
    - /gke
        - /base 
        - ... system files

Workloads:

- configuration/workloads
    - /defaults (contains default values for various load generation and traffic control settings)
    - /topologies (topology files that the load generator uses to generate load)
    - /policies (policy files that the load generator uses create RabbitMQ policies)
    - ...workload-files

RabbitMQ configuration:

- configuration/rabbitmq-config
    - ...config files

The orchestrator itself is a Java application which has the following arguments:

- --playlist-file /path/to/playlist-file.yml (the root yaml file to run)
- --config-dir /path/to/configuration (the directory of configuration files like the systems, workloads etc)
- --meta-data-dir /path/to/meta (the directory of meta-data files which contain host specific configuration)
- --no-destroy true/false (whether to teardown all deployed systems at the end of the playlist run)
- --no-deploy true/false (used with a run-tag to identify an existing set of deployment systems)
- --run-tag 123456 (the run-tag to target, when using --no-deploy true)


Example deploys, run the playlist and then tears down everything.
```bash
java -jar orchestrator-0.1-SNAPSHOT-jar-with-dependencies.jar \
--playlist-file /home/me/github/RabbitTestTool/orchestration/v2/configuration/playlists/my-playlist.yml
--config-dir /home/me/github/RabbitTestTool/orchestration/v2/configuration
--meta-data-dir /home/me/my-meta-file-dir
--no-destroy false
--no-deploy false
```

## Dependencies

- Ansible with dynamic inventory for EC2 host (see dependencies/ansible)
- Java 8+
- AWS CLI
- eksctl
- gcloud CLI

There is a Dockerfile, which is more aspirational as it has not working yet. But it clearly describes the dependencies required to make the orchestration work.

## Load Generation

The load generator is found under RabbitTestTool/benchmark and has its own readme. The orchestration frameowkr simply deploys it and passes it:

- a topology file
- a policies file (optional)
- arguments

These are all defined in the playlist workload and is passed through to the load generator.

## YAML Guide

### The Playlist File

A playlist file is composed of three sections:

- systems
- common-workload
- benchmarks

The common workload can be considered the defaults for the playlist workloads. Each benchmark can specify changes to that workload that are executed as part of that benchmark.

Each benchmark will either consist of a single workload that is applied to all systems of that playlist, or multiple workloads which are matched to specific systems. For example, system 1 might run one workload and system 2 might run another. The difference should normally be minimal, for example system 1 uses mirrored queues and system 2 uses quorum queues.

There are examples of either common systems, unique workload or unique systems, common workloads, in the playlists directory.

Each system is based on a system file, and can specify overrides. For example, it can use a system file that has an 8 core instance, with 1 broker, then override the broker count to set it to 5.

### The system file/section

The system file is composed of the sections:

- hardware
    - loadgen
    - broker
- os (EC2 only)
- rabbitmq
    - broker
    - erlang
    - config

Each host has its own systems default.yml which stores default values to be used when not specified in a system file or the overrides in a playlist file.

### The workload file/section

A workload is composed of the section:

- main
    - topology
    - policies
- background
    - topology
    - policies
- loadgen-config
- client-config
- broker-actions (like traffic control)
- rabbitmq
    - config

There are three defaults files that are used to source values when not specified by:

- the system (only applicable to RabbitMQ configuration)
- the common-workload section
- the workload section of each benchmark

Topology and policy files themselves have defaults in their variables section.