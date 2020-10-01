package com.rabbitmq.orchestrator.run.ec2;

import com.rabbitmq.orchestrator.InvalidInputException;
import com.rabbitmq.orchestrator.ProcessExecutor;
import com.rabbitmq.orchestrator.deploy.OutputData;
import com.rabbitmq.orchestrator.meta.EC2Meta;
import com.rabbitmq.orchestrator.deploy.ec2.model.EC2System;
import com.rabbitmq.orchestrator.model.Workload;
import com.rabbitmq.orchestrator.run.Runner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class EC2Runner implements Runner {
    private static final Logger LOGGER = LoggerFactory.getLogger("EC2_RUNNER");
    EC2Meta ec2Meta;
    OutputData outputData;
    ProcessExecutor processExecutor;
    EC2System system;
    String runTag;
    File scriptDir;

    public EC2Runner(String runTag,
                     EC2System system,
                     EC2Meta ec2Meta,
                     OutputData outputData,
                     ProcessExecutor processExecutor) {
        this.runTag = runTag;
        this.system = system;
        this.ec2Meta = ec2Meta;
        this.outputData = outputData;
        this.processExecutor = processExecutor;

        scriptDir = new File(ec2Meta.getRunScriptsRoot());
        if(!scriptDir.exists())
            throw new InvalidInputException("The script directory provided does not exist");
    }

    @Override
    public void runMainLoad(Workload workload,
                            int runOrdinal,
                            Set<String> failedSystems,
                            AtomicBoolean isCancelled,
                            String runId,
                            String tags) {
        try {
            String federationArgs = "";
            if (system.isFederationEnabled()) {
                federationArgs = "--downstream-broker-hosts " + String.join(",", system.getDownstreamPrivateIps());
            }

            List<String> variables = Arrays.asList(
                    "NODE_NUMBER=" + system.getFirstNode(false),
                    "KEY_PAIR=" + ec2Meta.getKeyPair(),
                    "TECHNOLOGY=" + "rabbitmq",
                    "BROKER_VERSION=" + system.getRabbitmq().getRabbitmqVersion(),
                    "INSTANCE=" + system.getHardware().getRabbitMQInstance().getInstanceType(),
                    "VOLUME1_TYPE=" + system.getHardware().getVolumeConfigs().get(0).getVolumeType(),
                    "FILESYSTEM=" + system.getOs().getFilesystem(),
                    "HOSTING=" + "EC2",
                    "TENANCY=" + system.getHardware().getTenancy(),
                    "PASSWORD=" + outputData.getInfluxPassword(),
                    "POSTGRES_URL=" + outputData.getPostgresUrl(),
                    "POSTGRES_USER=" + outputData.getPostgresUser(),
                    "POSTGRES_PWD=" + outputData.getPostgresPassword(),
                    "TOPOLOGY=" + workload.getMainLoad().getTopologyFile(),
                    "RUN_ID=" + runId,
                    "USERNAME=" + outputData.getRabbitmqUsername(),
                    "PASSWORD=" + outputData.getRabbitmqPassword(),
                    "RUN_TAG=" + runTag,
                    "CORE_COUNT=" + system.getHardware().getRabbitMQInstance().getCoreCount(),
                    "THREADS_PER_CORE=" + system.getHardware().getRabbitMQInstance().getThreadsPerCore(),
                    "CONFIG_TAG=" + system.getName(),
                    "CLUSTER_SIZE=" + system.getHardware().getInstanceCount(),
                    "NO_TCP_DELAY=" + workload.getClientConfiguration().isTcpNoDelay(),
                    "POLICIES=" + workload.getMainLoad().getPoliciesFile(),
                    "OVERRIDE_STEP_SECONDS=" + workload.getMainLoad().getStepSeconds(),
                    "OVERRIDE_STEP_REPEAT=" + workload.getMainLoad().getStepRepeat(),
                    "OVERRIDE_STEP_MSG_LIMIT=" + workload.getMainLoad().getStepMsgLimit(),
                    "OVERRIDE_BROKER_HOSTS=", // TODO where to source this from in v2?
                    "PUB_CONNECT_TO_NODE=" + workload.getClientConfiguration().getPublisherConnectMode(),
                    "CON_CONNECT_TO_NODE=" + workload.getClientConfiguration().getConsumerConnectMode(),
                    "PUB_HEARTBEAT_SEC=" + workload.getClientConfiguration().getPublisherHeartbeatSeconds(),
                    "CON_HEARTBEAT_SEC=" + workload.getClientConfiguration().getConsumerHeartbeatSeconds(),
                    "MODE=" + workload.getLoadgenConfiguration().getMode(),
                    "GRACE_PERIOD_SEC=" + workload.getLoadgenConfiguration().getGracePeriodSeconds(),
                    "WARMUPSECONDS=" + workload.getLoadgenConfiguration().getWarmUpSeconds(),
                    "CHECKS=" + workload.getLoadgenConfiguration().getChecksStr(),
                    "RUN_ORDINAL=" + runOrdinal,
                    "TAGS=" + tags,
                    "ATTEMPTS=" + "1",
                    "INFLUX_SUBPATH=" + outputData.getInfluxSubpath(),
                    "TOPOLOGY_VARIABLES='" + workload.getMainLoad().getTopologyVariablesStr() + "'",
                    "POLICY_VARIABLES='" + workload.getMainLoad().getPoliciesVariablesStr() + "'",
                    "FEDERATION_ARGS=" + federationArgs);

            String variablesFilePath = processExecutor.createFile(variables, ".vars");

            List<String> args = Arrays.asList("bash",
                    "run-main-workload.sh",
                    variablesFilePath);

            String logPrefix = "Execution of workload for system: " + system.getName();
            processExecutor.runProcess(scriptDir, args, logPrefix, system.getName(), isCancelled, failedSystems);
        } catch(Exception e) {
            LOGGER.error("Benchmark on system " + system.getName() + " has failed", e);
            failedSystems.add(system.getName());
        }
    }

    @Override
    public void runBackroundLoad(Workload workload, Set<String> failedSystems, AtomicBoolean isCancelled) {
        try {
            List<String> variables = Arrays.asList(
                    "BROKER_USER=" + outputData.getRabbitmqUsername(),
                    "BROKER_PASSWORD=" + outputData.getRabbitmqPassword(),
                    "CLUSTER_SIZE=" + system.getHardware().getInstanceCount(),
                    "KEY_PAIR=" + ec2Meta.getKeyPair(),
                    "NODE_NUMBER=" + system.getFirstNode(false),
                    "POLICY=" + workload.getBackgroundLoad().getPoliciesFile(),
                    "OVERRIDE_STEP_SECONDS=" + workload.getBackgroundLoad().getStepSeconds(),
                    "OVERRIDE_STEP_REPEAT=" + workload.getBackgroundLoad().getStepRepeat(),
                    "RUN_TAG=" + runTag,
                    "TECHNOLOGY=" + "rabbitmq",
                    "TOPOLOGY=" + workload.getBackgroundLoad().getTopologyFile(),
                    "VERSION=" + system.getRabbitmq().getRabbitmqVersion());

            String variablesFilePath = processExecutor.createFile(variables, ".vars");

            List<String> args = Arrays.asList("bash",
                    "run-background-workload.sh",
                    variablesFilePath);

            String logPrefix = "Execution of background workload for system: " + system.getName();
            processExecutor.runProcess(scriptDir, args, logPrefix, system.getName(), isCancelled, failedSystems);
        } catch(Exception e) {
            LOGGER.error("Benchmark on system " + system.getName() + " has failed", e);
            failedSystems.add(system.getName());
        }

    }
}
