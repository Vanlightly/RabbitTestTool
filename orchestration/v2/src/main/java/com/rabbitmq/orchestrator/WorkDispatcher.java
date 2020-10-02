package com.rabbitmq.orchestrator;

import com.rabbitmq.orchestrator.deploy.*;
import com.rabbitmq.orchestrator.deploy.ec2.EC2Deployer;
import com.rabbitmq.orchestrator.deploy.ec2.model.EC2System;
import com.rabbitmq.orchestrator.deploy.k8s.K8sDeployer;
import com.rabbitmq.orchestrator.deploy.k8s.model.K8sEngine;
import com.rabbitmq.orchestrator.deploy.k8s.model.K8sSystem;
import com.rabbitmq.orchestrator.meta.EC2Meta;
import com.rabbitmq.orchestrator.meta.K8sMeta;
import com.rabbitmq.orchestrator.model.Workload;
import com.rabbitmq.orchestrator.model.Benchmark;
import com.rabbitmq.orchestrator.parsers.PlaylistParser;
import com.rabbitmq.orchestrator.run.BrokerActioner;
import com.rabbitmq.orchestrator.run.ec2.EC2BrokerActioner;
import com.rabbitmq.orchestrator.run.ec2.EC2Runner;
import com.rabbitmq.orchestrator.run.k8s.K8sBrokerActioner;
import com.rabbitmq.orchestrator.run.k8s.K8sRunner;
import com.rabbitmq.orchestrator.run.Runner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class WorkDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger("DeploymentDispatcher");

    PlaylistParser playlistParser;
    String runTag;
    List<BaseSystem> systems;
    AtomicBoolean isCancelled;
    AtomicBoolean opInProgress;
    volatile Set<String> failedSystems;
    OutputData outputData;
    ProcessExecutor processExecutor;
    boolean noDeploy;
    boolean noDestroy;
    List<Deployment> deployments;

    public WorkDispatcher(PlaylistParser playlistParser,
                          OutputData outputData,
                          ProcessExecutor processExecutor,
                          boolean noDeploy,
                          boolean noDestroy) {
        this.playlistParser = playlistParser;
        this.outputData = outputData;
        this.processExecutor = processExecutor;
        this.noDeploy = noDeploy;
        this.noDestroy = noDestroy;
        this.isCancelled = new AtomicBoolean(false);
        this.opInProgress = new AtomicBoolean(false);
        this.deployments = new ArrayList<>();
        this.failedSystems = new HashSet<>();
    }

    public boolean deploySystems(String runTag, List<BaseSystem> systems) {
        this.runTag = runTag;
        this.systems = systems;

        for(BaseSystem system : this.systems) {
            deployments.add(createDeployment(system, outputData));
        }

        this.isCancelled.set(false);
        this.opInProgress.set(true);
        this.failedSystems.clear();

        if(noDeploy) {
            LOGGER.info("No deployment step - using existing systems with run tag " + runTag);
        } else {
            boolean deploySuccess = deploySystems();
            if(!deploySuccess) {
                opInProgress.set(false);
                return false;
            }
        }

        boolean success = obtainSystemInfo();
        opInProgress.set(false);
        return success;
    }

    private boolean deploySystems() {
        ExecutorService executorService = Executors.newCachedThreadPool();
        for(Deployment deployment : deployments) {
            executorService.submit(() -> deployment.getDeployer().deploySystem());
        }

        executorService.shutdown();
        while(!isCancelled.get() && !anyOpHasFailed() && !executorService.isTerminated()) {
            Waiter.waitMs(1000, isCancelled);
        }

        if(anyOpHasFailed()) {
            LOGGER.info("Deployment failed for systems: " + String.join(",", failedSystems) + ". Cancelling all parallel operations.");
            isCancelled.set(true);
            Waiter.awaitTermination(executorService, 30, TimeUnit.SECONDS);

            try {
                for (Deployment deployment : deployments) {
                    LOGGER.info("Tearing down system: " + deployment.getBaseSystem().getName());
                    deployment.getDeployer().teardown();
                }
            } catch (Exception e) {
                LOGGER.error("TEARDOWN FAILED! MANUAL TEARDOWN REQUIRED.", e);
            }

            return false;
        } else if(isCancelled.get()) {
            LOGGER.info("Deployment cancelled before completion");
            return false;
        }

        return true;
    }

    private boolean obtainSystemInfo() {
        ExecutorService executorService = Executors.newCachedThreadPool();
        for(Deployment deployment : deployments) {
            executorService.submit(() -> deployment.getDeployer().obtainSystemInfo());
        }

        executorService.shutdown();
        while(!isCancelled.get() && !anyOpHasFailed() && !executorService.isTerminated()) {
            Waiter.waitMs(1000, isCancelled);
        }

        if(anyOpHasFailed()) {
            LOGGER.info("Could not obtain information for systems: " + String.join(",", failedSystems));
            isCancelled.set(true);
            Waiter.awaitTermination(executorService, 60, TimeUnit.SECONDS);
            return false;
        } else if(isCancelled.get()) {
            LOGGER.info("Could not obtain information - cancelled before completion");
            return false;
        }

        return true;
    }

    public boolean prepareSystems(Benchmark benchmark) {
        this.isCancelled.set(false);
        this.opInProgress.set(true);
        this.failedSystems.clear();

        ExecutorService executorService = Executors.newCachedThreadPool();
        for(Deployment deployment : deployments) {
            Workload workload = benchmark.getWorkloadFor(deployment.getBaseSystem().getName());

            if (workload.hasConfigurationChanges()) {
                executorService.submit(() -> deployment.getDeployer().applyNewConfiguration(workload.getBrokerConfiguration()));
            } else {
                executorService.submit(() -> deployment.getDeployer().restartBrokers());
            }
        }

        executorService.shutdown();
        while(!isCancelled.get() && !anyOpHasFailed() && !executorService.isTerminated()) {
            Waiter.waitMs(1000, isCancelled);
        }

        boolean success = true;
        if(anyOpHasFailed()) {
            LOGGER.info("Broker restart failed for systems: " + String.join(",", failedSystems) + ". Cancelling all other parallel operations.");
            isCancelled.set(true);
            Waiter.awaitTermination(executorService, 60, TimeUnit.SECONDS);
            success = false;
        } else if(isCancelled.get()) {
            LOGGER.info("Broker restart cancelled before completion");
            success = false;
        }

        this.opInProgress.set(false);

        return success;
    }

    public boolean run(Benchmark benchmark,
                       String tags,
                       String runId) {
        this.isCancelled.set(false);
        this.opInProgress.set(true);
        this.failedSystems.clear();

        executePreBenchmarkTasks(benchmark);
        boolean success = executeBenchmark(benchmark, tags, runId);
        if(!success) {
            LOGGER.warn("Benchmark failed, retrying once after 60 seconds");
            Waiter.waitMs(60000, isCancelled);
            success = executeBenchmark(benchmark, tags, runId);
        }

        opInProgress.set(false);
        return success;
    }

    private boolean executePreBenchmarkTasks(Benchmark benchmark) {
//        LOGGER.info("Pre workload tasks for benchmark #" + benchmark.getOrdinal());
//        ExecutorService executorService = Executors.newCachedThreadPool();
//
//        for(Deployment deployment : deployments) {
//            executorService.submit(() -> deployment.getRunner().preWorkloadAction());
//        }
//
//        executorService.shutdown();
//        while(!isCancelled.get() && !anyOpHasFailed() && !executorService.isTerminated()) {
//            Waiter.waitMs(1000, isCancelled);
//        }
//
//        boolean success = true;
//        if(anyOpHasFailed()) {
//            LOGGER.info("Pre workload tasks for benchmark #" + benchmark.getOrdinal() + " failed for systems: " + String.join(",", failedSystems) + ". Cancelling all parallel operations");
//            isCancelled.set(true);
//            Waiter.awaitTermination(executorService, 60, TimeUnit.SECONDS);
//            success = false;
//        } else if(isCancelled.get()) {
//            LOGGER.info("Pre workload tasks for benchmark #" + benchmark.getOrdinal() + " cancelled before completion");
//            success = false;
//        }
//
//        return success;
        return true;
    }

    private boolean executeBenchmark(Benchmark benchmark,
                                     String tags,
                                     String runId) {
        LOGGER.info("Kicking off benchmark #" + benchmark.getOrdinal());
        ExecutorService executorService = Executors.newCachedThreadPool();

        for(Deployment deployment : deployments) {
            LOGGER.info("Launching workload on system: " + deployment.getBaseSystem().getName() + " in benchmark #" + benchmark.getOrdinal());
            final int thisRunOrdinal = benchmark.getOrdinal();
            Workload workload = benchmark.getWorkloadFor(deployment.getBaseSystem().getName());

            executorService.submit(() -> deployment.getRunner().runMainLoad(workload,
                    runId,
                    thisRunOrdinal,
                    tags));

            if(workload.hasBackgroundLoad()) {
                executorService.submit(() ->
                        deployment.getRunner().runBackroundLoad(workload));
            }

            executorService.submit(() -> deployment.getBrokerActioner().applyActionsIfAny(
                    deployment.getBaseSystem(),
                    workload.getBrokerAction(),
                    isCancelled,
                    failedSystems));
        }

        executorService.shutdown();
        Set<String> failedSeen = new HashSet<>();
        boolean success = true;
        while(!isCancelled.get() && !executorService.isTerminated()) {
            Waiter.waitMs(1000, isCancelled);

            if(anyOpHasFailed()) {
                for(String system : failedSystems) {
                    if(!failedSeen.contains(system)) {
                        success = false;
                        failedSeen.add(system);
                        LOGGER.warn("Workload on system " + system + " has failed in benchmark #" + benchmark.getOrdinal());
                    }
                }
            }
        }

        if(isCancelled.get()) {
            LOGGER.info("Benchmark #" + benchmark.getOrdinal() + " cancelled before completion");
            success = false;
        }

        removeAnyAffects(benchmark);
        if(!failedSystems.isEmpty()) {
            LOGGER.info("Benchmark #" + benchmark.getOrdinal() + " failed to remove effects");
            success = false;
        }

        return success;
    }

    private boolean removeAnyAffects(Benchmark benchmark) {
        ExecutorService executorService = Executors.newCachedThreadPool();
        for(Deployment deployment : deployments) {
            Workload workload = benchmark.getWorkloadFor(deployment.getBaseSystem().getName());

            executorService.submit(() -> deployment.getBrokerActioner().removeAnyAffects(
                    deployment.getBaseSystem(),
                    workload.getBrokerAction(),
                    isCancelled,
                    failedSystems));
        }

        executorService.shutdown();
        while(!isCancelled.get() && !anyOpHasFailed() && !executorService.isTerminated()) {
            Waiter.waitMs(1000, isCancelled);
        }

        boolean success = true;
        if(anyOpHasFailed()) {
            LOGGER.info("Failed removing effects for systems: " + String.join(",", failedSystems) + ". Cancelling all parallel operations");
            isCancelled.set(true);
            Waiter.awaitTermination(executorService, 60, TimeUnit.SECONDS);
            success = false;
        } else if(isCancelled.get()) {
            LOGGER.info("Removal of effects cancelled before completion");
            success = false;
        }

        return success;
    }

    public boolean restartBrokers() {
        this.isCancelled.set(false);
        this.opInProgress.set(true);
        this.failedSystems.clear();

        ExecutorService executorService = Executors.newCachedThreadPool();
        for(Deployment deployment : deployments)
            executorService.submit(() -> deployment.getDeployer().restartBrokers());

        executorService.shutdown();
        while(!isCancelled.get() && !anyOpHasFailed() && !executorService.isTerminated()) {
            Waiter.waitMs(1000, isCancelled);
        }

        boolean success = true;
        if(anyOpHasFailed()) {
            LOGGER.info("Failed restarting brokers for systems: " + String.join(",", failedSystems) + ". Cancelling all parallel operations");
            isCancelled.set(true);
            Waiter.awaitTermination(executorService, 60, TimeUnit.SECONDS);
            success = false;
        } else if(isCancelled.get()) {
            LOGGER.info("Restarting of brokers cancelled before completion");
            success = false;
        }

        this.opInProgress.set(false);
        return success;
    }

    public void retrieveLogs() {
        this.isCancelled.set(false);
        this.opInProgress.set(true);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
                .withZone(ZoneId.systemDefault());
        String path = outputData.getLogsStorageDir() + "/" + formatter.format(new Date().toInstant());

        ExecutorService executorService = Executors.newCachedThreadPool();
        for(Deployment deployment : deployments)
            executorService.submit(() -> deployment.getDeployer().retrieveLogs(path));

        executorService.shutdown();
        while(!isCancelled.get() && !executorService.isTerminated()) {
            Waiter.waitMs(1000, isCancelled);
        }

        this.opInProgress.set(false);
    }

    public void teardown() {
        if (noDestroy) {
            LOGGER.info("No teardown as no-destroy mode activated");
            return;
        }

        this.isCancelled.set(false);
        this.opInProgress.set(true);
        this.failedSystems.clear();

        ExecutorService executorService = Executors.newCachedThreadPool();
        for(Deployment deployment : deployments)
            executorService.submit(() -> deployment.getDeployer().teardown());

        executorService.shutdown();
        while(!isCancelled.get() && !anyOpHasFailed() && !executorService.isTerminated()) {
            Waiter.waitMs(1000, isCancelled);
        }

        if(anyOpHasFailed()) {
            LOGGER.info("Teardown failed for systems: " + String.join(",", failedSystems));
        } else if(isCancelled.get()) {
            LOGGER.info("Teardown cancelled before completion");
        }

        this.opInProgress.set(false);
    }

    public void cancelAllOperations() {
        isCancelled.set(true);
        while(opInProgress.get()) {
            Waiter.waitMs(100);
        }
    }

    private Deployment createDeployment(BaseSystem system, OutputData metrics) {
        switch(system.getHost()) {
            case EC2:
                EC2Meta ec2Meta = playlistParser.loadEc2Meta();
                Deployer ec2Deployer = new EC2Deployer(
                        runTag,
                        system,
                        isCancelled,
                        failedSystems,
                        ec2Meta,
                        metrics,
                        processExecutor);
                Runner ec2Runner = new EC2Runner(runTag,
                        (EC2System) system,
                        ec2Meta,
                        metrics,
                        processExecutor,
                        failedSystems,
                        isCancelled);
                BrokerActioner actioner = new EC2BrokerActioner(ec2Meta,
                        processExecutor,
                        runTag,
                        outputData.getRabbitmqUsername(),
                        outputData.getRabbitmqPassword(),
                        ec2Meta.getRunScriptsRoot());
                return new Deployment(ec2Deployer, ec2Runner, actioner, system);
            case K8S:
                K8sMeta k8sMeta = getK8sMeta(system.getK8sEngine());
                Deployer k8sDeployer = new K8sDeployer(
                        runTag,
                        system,
                        isCancelled,
                        failedSystems,
                        k8sMeta,
                        metrics,
                        processExecutor);
                Runner k8sRunner = new K8sRunner(runTag,
                        (K8sSystem) system,
                        k8sMeta,
                        metrics,
                        processExecutor,
                        failedSystems,
                        isCancelled);
                return new Deployment(k8sDeployer, k8sRunner, new K8sBrokerActioner(), system);
            default:
                throw new InvalidInputException("Host deployment not supported");
        }
    }

    private K8sMeta getK8sMeta(K8sEngine k8sEngine) {
        switch(k8sEngine) {
            case EKS:
                return playlistParser.loadEksMeta();
            case GKE:
                return playlistParser.loadGkeMeta();
            default:
                throw new InvalidInputException("K8sEngine not supported: " + k8sEngine);
        }
    }

    private boolean anyOpHasFailed() {
        return !failedSystems.isEmpty();
    }
}
