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
import com.rabbitmq.orchestrator.parsers.YamlParser;
import com.rabbitmq.orchestrator.run.EC2Runner;
import com.rabbitmq.orchestrator.run.K8sRunner;
import com.rabbitmq.orchestrator.run.Runner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class WorkDispatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger("DeploymentDispatcher");

    YamlParser yamlParser;
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

    public WorkDispatcher(YamlParser yamlParser,
                          OutputData outputData,
                          ProcessExecutor processExecutor,
                          boolean noDeploy,
                          boolean noDestroy) {
        this.yamlParser = yamlParser;
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

        if(noDeploy) {
            LOGGER.info("No deployment step - using existing systems with run tag " + runTag);
            return true;
        }

        if(isCancelled.get()) {
            LOGGER.warn("Deployment cancelled");
            return false;
        }

        this.opInProgress.set(true);
        this.failedSystems.clear();

        ExecutorService executorService = Executors.newCachedThreadPool();
        for(Deployment deployment : deployments) {
            executorService.submit(() -> deployment.getDeployer().deploySystem());
        }

        executorService.shutdown();
        while(!isCancelled.get() && !anyOpHasFailed() && !executorService.isTerminated()) {
            Waiter.waitMs(1000, isCancelled);
        }

        boolean success = true;
        if(anyOpHasFailed()) {
            LOGGER.info("Deployment failed for systems: " + String.join(",", failedSystems));
            try {
                for(Deployment deployment : deployments) {
                    LOGGER.info("Tearing down system: " + deployment.getBaseSystem().getName());
                    deployment.getDeployer().teardown();
                }
            } catch(Exception e) {
                LOGGER.error("TEARDOWN FAILED! MANUAL TEARDOWN REQUIRED.", e);
            }

            success = false;
        } else if(isCancelled.get()) {
            LOGGER.info("Deployment cancelled before completion");
            success = false;
        }

        opInProgress.set(false);

        return success;
    }

    public boolean updateBrokers(Benchmark benchmark) {
        this.opInProgress.set(true);
        this.failedSystems.clear();

        ExecutorService executorService = Executors.newCachedThreadPool();
        for(Deployment deployment : deployments) {
            Workload workload = benchmark.getWorkloadFor(deployment.getBaseSystem().getName());

            if (workload.hasConfigurationChanges()) {
                executorService.submit(() -> deployment.getDeployer().updateBroker(workload.getBrokerConfiguration()));
            }
        }

        executorService.shutdown();
        while(!isCancelled.get() && !anyOpHasFailed() && !executorService.isTerminated()) {
            Waiter.waitMs(1000, isCancelled);
        }

        boolean success = true;
        if(anyOpHasFailed()) {
            LOGGER.info("Broker restart failed for systems: " + String.join(",", failedSystems));
            success = false;
        } else if(isCancelled.get()) {
            LOGGER.info("Broker restart cancelled before completion");
            success = false;
        }

        this.opInProgress.set(false);

        return success;
    }

    public boolean run(Benchmark benchmark, String tags) {
        if(isCancelled.get()) {
            LOGGER.warn("Workload execution cancelled");
            return false;
        }

        this.opInProgress.set(true);
        this.failedSystems.clear();

        String runId = UUID.randomUUID().toString();
        ExecutorService executorService = Executors.newCachedThreadPool();

        LOGGER.info("Kicking off benchmark #" + benchmark.getOrdinal());

        for(Deployment deployment : deployments) {
            LOGGER.info("Launching workload on system: " + deployment.getBaseSystem().getName() + " in benchmark #" + benchmark.getOrdinal());
            final int thisRunOrdinal = benchmark.getOrdinal();
            Workload workload = benchmark.getWorkloadFor(deployment.getBaseSystem().getName());

            executorService.submit(() -> deployment.getRunner().runMainLoad(workload,
                                                                            thisRunOrdinal,
                                                                            failedSystems,
                                                                            isCancelled,
                                                                            runId,
                                                                            tags));

            if(workload.hasBackgroundLoad()) {
                executorService.submit(() ->
                        deployment.getRunner().runBackroundLoad(workload,
                                failedSystems,
                                isCancelled));
            }
        }

        executorService.shutdown();
        while(!isCancelled.get() && !anyOpHasFailed() && !executorService.isTerminated()) {
            Waiter.waitMs(1000, isCancelled);
        }

        boolean success = true;
        if(anyOpHasFailed()) {
            LOGGER.info("Benchmark #" + benchmark.getOrdinal() + " failed for systems: " + String.join(",", failedSystems));
            success = false;
        } else if(isCancelled.get()) {
            LOGGER.info("Benchmark #" + benchmark.getOrdinal() + " cancelled before completion");
            success = false;
        }

        opInProgress.set(false);
        return success;
    }

    public void restartBrokers() {
        this.opInProgress.set(true);
        this.failedSystems.clear();

        this.opInProgress.set(false);
    }

    public void retrieveLogs() {
        this.opInProgress.set(true);
        this.failedSystems.clear();

        this.opInProgress.set(false);
    }

    public void teardown() {
        if (noDestroy) {
            LOGGER.info("No teardown as no-destroy mode activated");
            return;
        }

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
                EC2Meta ec2Meta = yamlParser.loadEc2Meta();
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
                        processExecutor);
                return new Deployment(ec2Deployer, ec2Runner, system);
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
                        processExecutor);
                return new Deployment(k8sDeployer, k8sRunner, system);
            default:
                throw new InvalidInputException("Host deployment not supported");
        }
    }

    private K8sMeta getK8sMeta(K8sEngine k8sEngine) {
        switch(k8sEngine) {
            case EKS:
                return yamlParser.loadEksMeta();
            case GKE:
                return yamlParser.loadGkeMeta();
            default:
                throw new InvalidInputException("K8sEngine not supported: " + k8sEngine);
        }
    }

    private boolean anyOpHasFailed() {
        return !failedSystems.isEmpty();
    }
}
