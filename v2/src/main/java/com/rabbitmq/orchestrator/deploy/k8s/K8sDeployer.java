package com.rabbitmq.orchestrator.deploy.k8s;

import com.rabbitmq.orchestrator.InvalidInputException;
import com.rabbitmq.orchestrator.ProcessExecutor;
import com.rabbitmq.orchestrator.deploy.BaseSystem;
import com.rabbitmq.orchestrator.deploy.Deployer;
import com.rabbitmq.orchestrator.deploy.OutputData;
import com.rabbitmq.orchestrator.deploy.Waiter;
import com.rabbitmq.orchestrator.deploy.k8s.model.K8sSystem;
import com.rabbitmq.orchestrator.meta.K8sMeta;
import com.rabbitmq.orchestrator.run.RabbitMQConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class K8sDeployer implements Deployer {

    private static final Logger LOGGER = LoggerFactory.getLogger("KUBERNETES_DEPLOYER");
    String runTag;
    K8sMeta k8sMeta;
    K8sSystem system;
    AtomicBoolean isCancelled;
    AtomicBoolean opInProgress;
    volatile Set<String> failedSystems;
    File scriptDir;
    OutputData outputData;
    ProcessExecutor processExecutor;

    public K8sDeployer(String runTag,
                       BaseSystem baseSystem,
                       AtomicBoolean isCancelled,
                       Set<String> failedSystems,
                       K8sMeta k8sMeta,
                       OutputData outputData,
                       ProcessExecutor processExecutor) {
        this.runTag = runTag;
        this.failedSystems = failedSystems;
        this.isCancelled = isCancelled;
        this.system = (K8sSystem)baseSystem;
        this.k8sMeta = k8sMeta;
        this.outputData = outputData;
        this.processExecutor = processExecutor;
        this.opInProgress = new AtomicBoolean(false);

        this.scriptDir = new File(k8sMeta.getDeployScriptsDir());
        if(!scriptDir.exists())
            throw new InvalidInputException("The script directory provided does not exist");
    }

    public void deploySystem() {
        String logPrefix = "Deployment of cluster complete for system " + system.getName();

        try {
            if(!deployOnManagedK8s()) {
                failedSystems.add(system.getName());
                return;
            }
        } catch(Exception e) {
            LOGGER.error(logPrefix + " has failed", e);
            failedSystems.add(system.getName());
        }
    }

    @Override
    public void obtainSystemInfo() {
        // not required for this host
    }

    private boolean deployOnManagedK8s() {
        String logPrefix = "Deployment of instances for system: " + system.getName();

        String manifestFilePath = null;
        try {
            ManifestGenerator g = new ManifestGenerator(new File(k8sMeta.getManifestsDir()));
            manifestFilePath = g.generateManifest(system, processExecutor);
        } catch (IOException e) {
            throw new RuntimeException("Failed generating manifest", e);
        }

        List<String> args = Arrays.asList("bash",
                "deploy-all.sh",
                "-i", system.getHardware().getInstance().getInstanceType(),
                "-b", String.valueOf(system.getHardware().getInstanceCount()),
                "-n", k8sMeta.getKubeClusterName(system.getName(), system.getK8sEngine(), runTag),
                "-N", k8sMeta.getRabbitClusterName(system.getName(), system.getK8sEngine()),
                "-k", String.valueOf(system.getK8sEngine()).toLowerCase(),
                "-u", k8sMeta.getUserOrProject(),
                "-v", k8sMeta.getK8sVersion(),
                "-m", manifestFilePath,
                "-z", k8sMeta.getRegionOrZones(),
                "-c", k8sMeta.getK8sContext(system.getName(), system.getK8sEngine(), runTag)
        );

        return processExecutor.runProcess(scriptDir, args, logPrefix, isCancelled, failedSystems);
    }

    @Override
    public void updateBrokers(RabbitMQConfiguration brokerConfiguration) {
        String logPrefix = "Configuration update of instances for system: " + system.getName();
        String manifestFilePath = null;
        try {
            ManifestGenerator g = new ManifestGenerator(new File(k8sMeta.getManifestsDir()));
            manifestFilePath = g.generateManifest(system, brokerConfiguration, processExecutor);
        } catch (IOException e) {
            throw new RuntimeException("Failed generating manifest", e);
        }

        List<String> args = Arrays.asList("bash",
                "update-configuration.sh",
                "-b", String.valueOf(system.getHardware().getInstanceCount()),
                "-n", k8sMeta.getKubeClusterName(system.getName(), system.getK8sEngine(), runTag),
                "-N", k8sMeta.getRabbitClusterName(system.getName(), system.getK8sEngine()),
                "-k", String.valueOf(system.getK8sEngine()).toLowerCase(),
                "-m", manifestFilePath,
                "-c", k8sMeta.getK8sContext(system.getName(), system.getK8sEngine(), runTag)
        );

        processExecutor.runProcess(scriptDir, args, logPrefix, isCancelled, failedSystems);
    }

    @Override
    public void restartBrokers() {
        if(system.getRabbitmq().rebootBetweenBenchmarks()) {
            String logPrefix = "Restarting brokers for system: " + system.getName() + " and run_tag: " + runTag;

            List<String> args = Arrays.asList("bash",
                    "restart.sh",
                    "-c", k8sMeta.getK8sContext(system.getName(), system.getK8sEngine(), runTag),
                    "-N", system.getRabbitClusterName(),
                    "-b", String.valueOf(system.getHardware().getInstanceCount()));
            processExecutor.runProcess(scriptDir, args, logPrefix, system.getName(), isCancelled, failedSystems);
        }
    }

    @Override
    public void teardown() {
        String logPrefix = "Teardown for system: " + system.getName() + " and run_tag: " + runTag;

        List<String> args = Arrays.asList("bash",
                "gke/delete-gke-cluster.sh",
                k8sMeta.getKubeClusterName(system.getName(), system.getK8sEngine(), runTag));
        processExecutor.runProcess(scriptDir, args, logPrefix, system.getName(), isCancelled, failedSystems);
    }

    @Override
    public void retrieveLogs(String path) {
        String logPrefix = "Log retrieval for system: " + system.getName() + " and run_tag: " + runTag;
        String log_dir = path + "/" + system.getName();

        List<String> args = Arrays.asList("bash",
                "get-logs.sh",
                "-c", k8sMeta.getK8sContext(system.getName(), system.getK8sEngine(), runTag),
                "-d", log_dir);
        processExecutor.runProcess(scriptDir, args, logPrefix, system.getName(), isCancelled, failedSystems);
    }

}
