package com.rabbitmq.orchestrator.deploy.ec2;

import com.rabbitmq.orchestrator.InvalidInputException;
import com.rabbitmq.orchestrator.ProcessExecutor;
import com.rabbitmq.orchestrator.deploy.*;
import com.rabbitmq.orchestrator.deploy.ec2.model.EC2System;
import com.rabbitmq.orchestrator.deploy.ec2.model.EC2VolumeConfig;
import com.rabbitmq.orchestrator.deploy.ec2.model.EC2VolumeType;
import com.rabbitmq.orchestrator.meta.EC2Meta;
import com.rabbitmq.orchestrator.model.actions.BrokerAction;
import com.rabbitmq.orchestrator.run.RabbitMQConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class EC2Deployer implements Deployer {
    private static final Logger LOGGER = LoggerFactory.getLogger("EC2_DEPLOYER");
    String runTag;
    EC2System system;
    EC2Meta ec2Meta;
    AtomicBoolean isCancelled;
    AtomicBoolean opInProgress;
    volatile Set<String> failedSystems;
    File scriptDir;
    OutputData outputData;
    ProcessExecutor processExecutor;

    public EC2Deployer(String runTag,
                       BaseSystem baseSystem,
                       AtomicBoolean isCancelled,
                       Set<String> failedSystems,
                       EC2Meta ec2Meta,
                       OutputData outputData,
                       ProcessExecutor processExecutor) {
        this.runTag = runTag;
        this.failedSystems = failedSystems;
        this.isCancelled = isCancelled;
        this.system = (EC2System)baseSystem;
        this.ec2Meta = ec2Meta;
        this.outputData = outputData;
        this.processExecutor = processExecutor;
        this.opInProgress = new AtomicBoolean(false);

        this.scriptDir = new File(ec2Meta.getDeployScriptsRoot());
        if(!scriptDir.exists())
            throw new InvalidInputException("The script directory '" + ec2Meta.getDeployScriptsRoot() + "' provided does not exist");
    }

    @Override
    public void deploySystem() {
        LOGGER.info("Deploying system: " + system.getName());
        if(system.isFederationEnabled()) {
            ExecutorService executorService = Executors.newCachedThreadPool();
            executorService.submit(() -> deployCluster(false));
            executorService.submit(() -> deployCluster(true));
            executorService.shutdown();

            while(!isCancelled.get() && !anyOpHasFailed() && !executorService.isTerminated()) {
                Waiter.waitMs(1000, isCancelled);
            }

            if(!isCancelled.get() && !anyOpHasFailed()) {
                addUpstreamHosts();
                LOGGER.info("Deployment of upstream and downstream cluster complete");
            }
        }
        else {
            deployCluster(false);
        }
    }

    @Override
    public void obtainSystemInfo() {
        LOGGER.info("Obtaining IP addresses for system: " + system.getName());
        List<String> mainPrivateIps = getPrivateBrokerIps( false);
        system.setMainPrivateIps(mainPrivateIps);

        List<String> mainPublicIps = getPublicBrokerIps( false);
        system.setMainPublicIps(mainPublicIps);

        List<String> downstreamIps = getPrivateBrokerIps( true);
        system.setDownstreamPrivateIps(downstreamIps);

        List<String> downstreamPublicIps = getPublicBrokerIps( true);
        system.setDownstreamPublicIps(downstreamPublicIps);
    }

    private void deployCluster(boolean isDownstream) {
        String whichCluster = isDownstream ? "downstream" : "main";
        String logPrefix = "Deployment of " + whichCluster + " cluster for system " + system.getName();

        try {
            if (!deployEC2Instances(system, isDownstream)) {
                failedSystems.add(system.getName());
                return;
            }

            if (this.isCancelled.get()) {
                LOGGER.info("Stopping deployment, cancellation requested");
                return;
            }


            int masterNodeNumber = system.getNodeNumbers().get(0);
            deployBroker(system, masterNodeNumber, isDownstream);
            if (anyOpHasFailed())
                return;

            if (this.isCancelled.get()) {
                LOGGER.info("Stopping deployment, cancellation requested");
                return;
            }

            ExecutorService executorService = Executors.newCachedThreadPool();
            for (int nodeNumber : system.getNodeNumbers()) {
                if (nodeNumber == masterNodeNumber)
                    continue;

                executorService.submit(() -> deployBroker(system, nodeNumber, isDownstream));
            }

            if (!isDownstream)
                executorService.submit(() -> deployBenchmark(system));

            executorService.shutdown();

            while (!isCancelled.get() && !anyOpHasFailed() && !executorService.isTerminated()) {
                Waiter.waitMs(1000, isCancelled);
            }

            if (isCancelled.get()) {
                LOGGER.info(logPrefix + " is cancelled, waiting for current op to terminate.");
                Waiter.awaitTermination(executorService, 30, TimeUnit.SECONDS);
            } else if (failedSystems.contains(system.getName())) {
                LOGGER.info(logPrefix + " has failed");
            } else {
                LOGGER.info(logPrefix + " is complete");
            }

        } catch(Exception e) {
            LOGGER.error(logPrefix + " has failed", e);
            failedSystems.add(system.getName());
        }
    }

    private boolean deployEC2Instances(EC2System system, boolean isDownstreamCluster) {
        String logPrefix = "Deployment of instances for system: " + system.getName();
        int firstNodeNumber = system.getNodeNumbers().get(0);
        if(isDownstreamCluster)
            firstNodeNumber = system.getDownstreamNodeNumbers().get(0);

        List<String> variables = Arrays.asList(
                "LOADGEN_AMI=" + system.getHardware().getLoadGenInstance().getAmi(),
                "BROKER_AMI=" + system.getHardware().getRabbitMQInstance().getAmi(),
                "CLUSTER_SIZE=" + system.getHardware().getInstanceCount(),
                "CORE_COUNT=" + system.getHardware().getRabbitMQInstance().getCoreCount(),
                "INFLUX_SUBPATH=" + outputData.getInfluxSubpath(),
                "INSTANCE=" + system.getHardware().getRabbitMQInstance().getInstanceType(),
                "KEY_PAIR=" + ec2Meta.getKeyPair(),
                "LG_INSTANCE=" + system.getHardware().getLoadGenInstance().getInstanceType(),
                "LG_SG=" + ec2Meta.getLoadgenSecurityGroup(),
                "NODE_NUMBER_START=" + firstNodeNumber,
                "RUN_TAG=" + runTag,
                "SG=" + ec2Meta.getBrokerSecurityGroup(),
                "SUBNETS=" + String.join(",", ec2Meta.getSubnets()),
                "TENANCY=" + system.getHardware().getTenancy(),
                "TPC=" + system.getHardware().getRabbitMQInstance().getThreadsPerCore(),
                "VOL1_IOPS_PER_GB=" + getVolumeIopsPerGb(1, system.getHardware().getVolumeConfigs()),
                "VOL2_IOPS_PER_GB=" + getVolumeIopsPerGb(2, system.getHardware().getVolumeConfigs()),
                "VOL3_IOPS_PER_GB=" + getVolumeIopsPerGb(3, system.getHardware().getVolumeConfigs()),
                "VOL1_SIZE=" + getVolumeSize(1, system.getHardware().getVolumeConfigs()),
                "VOL2_SIZE=" + getVolumeSize(2, system.getHardware().getVolumeConfigs()),
                "VOL3_SIZE=" + getVolumeSize(3, system.getHardware().getVolumeConfigs()),
                "VOL1_TYPE=" + getVolumeType(1, system.getHardware().getVolumeConfigs()),
                "VOL2_TYPE=" + getVolumeType(2, system.getHardware().getVolumeConfigs()),
                "VOL3_TYPE=" + getVolumeType(3, system.getHardware().getVolumeConfigs()));

        String variablesFilePath = processExecutor.createFile(variables, ".vars");

        List<String> args = Arrays.asList("bash",
                "deploy-rmq-cluster-instances.sh",
                variablesFilePath);

        return processExecutor.runProcess(scriptDir, args, logPrefix, isCancelled, failedSystems);
    }

    private String getVolumeIopsPerGb(int volumeOrdinal, List<EC2VolumeConfig> volumeConfigs) {
        if(volumeOrdinal <= volumeConfigs.size())
            return String.valueOf(volumeConfigs.get(volumeOrdinal-1).getIopsPerGb());
        else
            return "50"; // TODO: remove hard-coded values
    }

    private String getVolumeSize(int volumeOrdinal, List<EC2VolumeConfig> volumeConfigs) {
        if(volumeOrdinal <= volumeConfigs.size())
            return String.valueOf(volumeConfigs.get(volumeOrdinal-1).getSizeGb());
        else
            return "0";
    }

    private String getVolumeType(int volumeOrdinal, List<EC2VolumeConfig> volumeConfigs) {
        if(volumeOrdinal <= volumeConfigs.size())
            return String.valueOf(volumeConfigs.get(volumeOrdinal-1).getVolumeType()).toLowerCase();
        else
            return String.valueOf(EC2VolumeType.GP2).toLowerCase();
    }

    private String getVolumeMountpoint(int volumeOrdinal, List<EC2VolumeConfig> volumeConfigs) {
        if(volumeOrdinal <= volumeConfigs.size())
            return volumeConfigs.get(volumeOrdinal-1).getMountpoint();
        else
            return "/volume" + volumeOrdinal;
    }

    private void deployBroker(EC2System system, int nodeNumber, boolean isDownstream) {
        int deployedNode = isDownstream ? nodeNumber + 100 : nodeNumber;
        int firstNode = system.getNodeNumbers().get(0);
        int lastNode = system.getNodeNumbers().get(system.getNodeNumbers().size()-1);
        String role = nodeNumber == firstNode ? "master" : "joinee";

        if(isDownstream) {
            firstNode = system.getDownstreamNodeNumbers().get(0);
            lastNode = system.getDownstreamNodeNumbers().get(system.getDownstreamNodeNumbers().size()-1);
        }

        List<String> secrets = Arrays.asList(
                "influx_db_name: metrics", // TODO not hard-coded
                "influx_url: " + outputData.getInfluxUrl(),
                "influx_username: " + outputData.getInfluxUser(),
                "influx_password: " + outputData.getInfluxPassword(),
                "rabbitmq_username: " + outputData.getRabbitmqUsername(),
                "rabbitmq_password: " + outputData.getRabbitmqPassword()
        );
        String secretsFilePath = processExecutor.createFile(secrets, ".secrets");

        List<String> variables = Arrays.asList(
                "ERLANG_DEB_FILE_URL=" + system.getRabbitmq().getErlangUrl(),
                "GENERIC_UNIX_URL=" + system.getRabbitmq().getRabbitmqUrl(),
                "FS=" + system.getOs().getFilesystem(),
                "INFLUX_SUBPATH=" + outputData.getInfluxSubpath(),
                "INSTANCE=" + system.getHardware().getRabbitMQInstance().getInstanceType(),
                "KEY_PAIR=" + ec2Meta.getKeyPair(),
                "NODE_NUMBER=" + deployedNode,
                "NODE_RANGE_START=" + firstNode,
                "NODE_RANGE_END=" + lastNode,
                "NODE_ROLE=" + role,
                "RUN_TAG=" + runTag,
                "VOLUME_DATA=" + system.getHardware().getVolumeFor(RabbitMQData.MNESIA).getMountpoint(),
                "VOLUME_LOGS=" + system.getHardware().getVolumeFor(RabbitMQData.LOGS).getMountpoint(),
                "VOLUME_QUORUM=" + system.getHardware().getVolumeFor(RabbitMQData.QUORUM).getMountpoint(),
                "VOLUME_WAL=" + system.getHardware().getVolumeFor(RabbitMQData.WAL).getMountpoint(),
                "VOL1_SIZE=" + getVolumeSize(1, system.getHardware().getVolumeConfigs()),
                "VOL1_MOUNTPOINT=" + getVolumeMountpoint(1, system.getHardware().getVolumeConfigs()),
                "VOL2_SIZE=" + getVolumeSize(2, system.getHardware().getVolumeConfigs()),
                "VOL2_MOUNTPOINT=" + getVolumeMountpoint(2, system.getHardware().getVolumeConfigs()),
                "VOL3_SIZE=" + getVolumeSize(3, system.getHardware().getVolumeConfigs()),
                "VOL3_MOUNTPOINT=" + getVolumeMountpoint(3, system.getHardware().getVolumeConfigs()),
                "ENV_VARS='" + RabbitConfigGenerator.generateEnvConfig(system.getRabbitmqConfig().getEnvConfig(), ",") + "'",
                "STANDARD_VARS='" + RabbitConfigGenerator.generateStandardConfig(system.getRabbitmqConfig().getStandard(), ",") + "'",
                "ADVANCED_VARS_RABBIT='" + RabbitConfigGenerator.generateAdvancedConfig(system.getRabbitmqConfig().getAdvancedRabbit()) + "'",
                "ADVANCED_VARS_RA='" + RabbitConfigGenerator.generateAdvancedConfig(system.getRabbitmqConfig().getAdvancedRa()) + "'",
                "ADVANCED_VARS_ATEN='" + RabbitConfigGenerator.generateAdvancedConfig(system.getRabbitmqConfig().getAdvancedAten()) + "'",
                "SECRET_VARS_FILE=" + secretsFilePath,
                "RABBITMQ_PLUGINS='" + String.join(",", system.getRabbitmqConfig().getPlugins()) + "'"
        );

        String variablesFilePath = processExecutor.createFile(variables, ".vars");

        List<String> args = Arrays.asList("bash",
                "deploy-rmq-cluster-broker.sh",
                variablesFilePath);

        String logPrefix = "Deployment of broker " + role + "  for system: " + system.getName();
        processExecutor.runProcess(scriptDir, args, logPrefix, system.getName(), isCancelled, failedSystems);

        if(!isCancelled.get() && !anyOpHasFailed())
            deployScripts(deployedNode);
    }

    private void deployScripts(int nodeNumber) {
        String logPrefix = "Deploying scripts for node " + nodeNumber + " in system: " + system.getName();
        List<String> args = Arrays.asList("bash",
                "deploy-scripts.sh",
                ec2Meta.getKeyPair(),
                String.valueOf(nodeNumber),
                runTag,
                "rabbitmq");
        processExecutor.runProcess(scriptDir, args, logPrefix, isCancelled, failedSystems);
    }

    private void deployBenchmark(EC2System system) {
        String logPrefix = "Deployment of workload generation tooling for system: " + system.getName();
        List<String> args = Arrays.asList("bash",
                        "deploy-benchmark.sh",
                        ec2Meta.getKeyPair(),
                        String.valueOf(system.getFirstNode(false)),
                        "rabbitmq",
                        runTag);
        processExecutor.runProcess(scriptDir, args, logPrefix, system.getName(), isCancelled, failedSystems);
    }

    private void addUpstreamHosts() {
        String logPrefix = "Addition of upstream hosts for system: " + system.getName();
        List<String> variables = Arrays.asList(
                "KEY_PAIR=" + ec2Meta.getKeyPair(),
                "RUN_TAG=" + runTag,
                "DOWNSTREAM_NODE_RANGE_START=" + system.getFirstNode(true),
                "DOWNSTREAM_NODE_RANGE_END=" + system.getLastNode(true),
                "UPSTREAM_NODE_RANGE_START=" + system.getFirstNode(false),
                "UPSTREAM_NODE_RANGE_END=" + system.getLastNode(false)
        );

        String variablesFilePath = processExecutor.createFile(variables, ".vars");

        List<String> args = Arrays.asList("bash",
                "add-upstream-hosts.sh",
                variablesFilePath);
        processExecutor.runProcess(scriptDir, args, logPrefix, system.getName(), isCancelled, failedSystems);
    }

    private List<String> getPrivateBrokerIps(boolean isDownstream) {
        String logPrefix = "Getting private IPs for system: " + system.getName();
        List<String> args = Arrays.asList("bash",
                "get-broker-private-ips.sh",
                String.valueOf(system.getHardware().getInstanceCount()),
                String.valueOf(system.getFirstNode(isDownstream)),
                runTag,
                "rabbitmq");
        String ips = processExecutor.readFromProcess(scriptDir, args, logPrefix, isCancelled, failedSystems);
        return Arrays.stream(ips.split(",")).collect(Collectors.toList());
    }

    private List<String> getPublicBrokerIps(boolean isDownstream) {
        String logPrefix = "Getting private IPs for system: " + system.getName();
        List<String> args = Arrays.asList("bash",
                "get-broker-public-ips.sh",
                String.valueOf(system.getHardware().getInstanceCount()),
                String.valueOf(system.getFirstNode(isDownstream)),
                runTag,
                "rabbitmq");
        String ips = processExecutor.readFromProcess(scriptDir, args, logPrefix, isCancelled, failedSystems);
        return Arrays.stream(ips.split(",")).collect(Collectors.toList());
    }

    @Override
    public void applyNewConfiguration(RabbitMQConfiguration brokerConfiguration) {
        ExecutorService executorService = Executors.newCachedThreadPool();
        for(int nodeNumber : system.getNodeNumbers()) {
            executorService.submit(() -> updateBroker(brokerConfiguration, nodeNumber));

            if(system.isFederationEnabled())
                executorService.submit(() -> updateBroker(brokerConfiguration, nodeNumber + 100));
        }

        executorService.shutdown();

        while(!isCancelled.get() && !anyOpHasFailed() && !executorService.isTerminated()) {
            Waiter.waitMs(1000, isCancelled);
        }

        if(isCancelled.get()) {
            LOGGER.warn("Update cancelled before completion for system " + system.getName());
            Waiter.awaitTermination(executorService, 30, TimeUnit.SECONDS);
        }
        else if(failedSystems.contains(system.getName())) {
            LOGGER.warn("Update failed for system " + system.getName());
        }
        else {
            restartCluster();
            LOGGER.info("Update complete for system " + system.getName());
        }
    }

    private void updateBroker(RabbitMQConfiguration brokerConfiguration, int nodeNumber) {
        List<String> variables = Arrays.asList(
            "KEY_PAIR=" + ec2Meta.getKeyPair(),
                "NODE=" + nodeNumber,
                "RUN_TAG=" + runTag,
                "ENV_VARS='" + RabbitConfigGenerator.generateEnvConfig(brokerConfiguration.getEnvConfig(), ",") + "'",
                "STANDARD_VARS='" + RabbitConfigGenerator.generateStandardConfig(brokerConfiguration.getStandard(), ",") + "'",
                "ADVANCED_VARS_RABBIT='" + RabbitConfigGenerator.generateAdvancedConfig(brokerConfiguration.getAdvancedRabbit()) + "'",
                "ADVANCED_VARS_RA='" + RabbitConfigGenerator.generateAdvancedConfig(brokerConfiguration.getAdvancedRa()) + "'",
                "ADVANCED_VARS_ATEN='" + RabbitConfigGenerator.generateAdvancedConfig(brokerConfiguration.getAdvancedAten()) + "'"
        );

        String variablesFilePath = processExecutor.createFile(variables, ".vars");

        List<String> args = Arrays.asList("bash",
                "update-rabbitmq-config.sh",
                variablesFilePath);

        String logPrefix = "Updating of RabbitMQ configuration on broker " + nodeNumber + " in system: " + system.getName();
        processExecutor.runProcess(scriptDir, args, logPrefix, system.getName(), isCancelled, failedSystems);
    }

    @Override
    public void restartBrokers() {
        if(system.getRabbitmq().rebootBetweenBenchmarks()) {
            restartCluster();
        }
    }

    private void restartCluster() {
        ExecutorService executorService = Executors.newCachedThreadPool();
        for (int nodeNumber : system.getNodeNumbers()) {
            executorService.submit(() -> restartBroker(nodeNumber));

            if (system.isFederationEnabled())
                executorService.submit(() -> restartBroker(nodeNumber + 100));
        }

        executorService.shutdown();

        while (!isCancelled.get() && !anyOpHasFailed() && !executorService.isTerminated()) {
            Waiter.waitMs(1000, isCancelled);
        }

        if (isCancelled.get()) {
            LOGGER.warn("Restart of RabbitMQ apps cancelled before completion for system " + system.getName());
            Waiter.awaitTermination(executorService, 30, TimeUnit.SECONDS);
        } else if (failedSystems.contains(system.getName()))
            LOGGER.warn("Restart of RabbitMQ apps failed for system " + system.getName());
        else
            LOGGER.info("Restart of RabbitMQ apps complete");
    }

    private void restartBroker(int nodeNumber) {
        List<String> args = Arrays.asList("bash",
                "restart-broker.sh",
                ec2Meta.getKeyPair(),
                String.valueOf(nodeNumber),
                runTag,
                "rabbitmq");

        String logPrefix = "Restarting RabbitMQ app on node " + nodeNumber + " in system: " + system.getName();
        processExecutor.runProcess(scriptDir, args, logPrefix, system.getName(), isCancelled, failedSystems);
    }


    @Override
    public void teardown() {
        teardownSystem(false);

        if(system.isFederationEnabled())
            teardownSystem(true);
    }

    private void teardownSystem(boolean isDownstream) {
        String logPrefix = "Teardown for system: " + system.getName() + " and run_tag: " + runTag;

        String nodeNumber = isDownstream
                            ? String.valueOf(system.getNodeNumbers().get(0))
                            : String.valueOf(system.getDownstreamNodeNumbers().get(0));

        List<String> args = Arrays.asList("bash",
                "terminate-instances.sh",
                "rabbitmq",
                nodeNumber,
                runTag);

        // pass it a separate AtomicBoolean to ensure that cancellation does not interrupt teardown.
        processExecutor.runProcess(scriptDir, args, logPrefix, system.getName(), new AtomicBoolean(), failedSystems);
    }

    @Override
    public void retrieveLogs(String path) {
        retrieveLogs(path, false);

        if(system.isFederationEnabled())
            retrieveLogs(path, true);
    }

    private void retrieveLogs(String path, boolean isDownstream) {
        String log_dir = path + "/" + system.getName();

        List<String> args = Arrays.asList("bash",
                        "get-logs.sh",
                        ec2Meta.getKeyPair(),
                        system.getHardware().getVolumeFor(RabbitMQData.LOGS).getMountpoint(),
                        String.valueOf(system.getFirstNode(isDownstream)),
                        String.valueOf(system.getLastNode(isDownstream)),
                        runTag,
                        "rabbitmq",
                        log_dir);
        String logPrefix = "Retrieval of logs for "
                + (isDownstream ? "downstream" : "main")
                + " system: " + system.getName();

        // pass it a separate AtomicBoolean to ensure that cancellation does not interrupt log gathering.
        processExecutor.runProcess(scriptDir, args, logPrefix, system.getName(), new AtomicBoolean(), failedSystems);
    }

    private boolean anyOpHasFailed() {
        return failedSystems.contains(system.getName());
    }


}
