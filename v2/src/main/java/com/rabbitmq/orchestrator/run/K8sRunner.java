package com.rabbitmq.orchestrator.run;

import com.rabbitmq.orchestrator.InvalidInputException;
import com.rabbitmq.orchestrator.ProcessExecutor;
import com.rabbitmq.orchestrator.deploy.OutputData;
import com.rabbitmq.orchestrator.deploy.k8s.model.K8sSystem;
import com.rabbitmq.orchestrator.meta.K8sMeta;
import com.rabbitmq.orchestrator.model.Workload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class K8sRunner implements Runner {
    private static final Logger LOGGER = LoggerFactory.getLogger("K8S_RUNNER");

    K8sMeta k8sMeta;
    OutputData outputData;
    ProcessExecutor processExecutor;
    K8sSystem system;
    String runTag;
    File scriptDir;

    public K8sRunner(String runTag,
                     K8sSystem system,
                     K8sMeta k8sMeta,
                     OutputData outputData,
                     ProcessExecutor processExecutor) {
        this.runTag = runTag;
        this.system = system;
        this.k8sMeta = k8sMeta;
        this.outputData = outputData;
        this.processExecutor = processExecutor;

        scriptDir = new File(k8sMeta.getRunScriptsDir());
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
            List<String> variables = Arrays.asList(
                    "TECHNOLOGY=" + "rabbitmq",
                    "BROKER_VERSION=" + system.getRabbitmq().getRabbitmqVersion(),
                    "INSTANCE=" + system.getHardware().getInstance().getInstanceType(),
                    "VOLUME1_TYPE=" + system.getHardware().getVolumeConfig().getVolumeType(),
                    "FILESYSTEM=xfs",
                    "HOSTING=" + system.getHost(),
                    "TENANCY=na",
                    "PASSWORD=" + outputData.getInfluxPassword(),
                    "POSTGRES_URL=" + outputData.getPostgresUrl(),
                    "POSTGRES_USER=" + outputData.getPostgresUser(),
                    "POSTGRES_PWD=" + outputData.getPostgresPassword(),
                    "TOPOLOGY=" + workload.getTopologyFile(),
                    "RUN_ID=" + runId,
                    "RUN_TAG=" + runTag,
                    "VCPU_COUNT=" + (system.getHardware().getInstance().getVcpuCount()),
                    "CONFIG_TAG=" + system.getName(),
                    "CLUSTER_SIZE=" + system.getHardware().getInstanceCount(),
                    "NO_TCP_DELAY=" + workload.getClientConfiguration().isTcpNoDelay(),
                    "POLICIES=" + workload.getPoliciesFile(),
                    "OVERRIDE_STEP_SECONDS=" + workload.getOverrideStepSeconds(),
                    "OVERRIDE_STEP_REPEAT=" + workload.getOverrideStepRepeat(),
                    "OVERRIDE_STEP_MSG_LIMIT=" + workload.getOverrideStepMsgLimit(),
                    "OVERRIDE_BROKER_HOSTS=" + workload.getOverrideBrokerHostsStr(),
                    "PUB_CONNECT_TO_NODE=" + workload.getClientConfiguration().getPublisherConnectMode(),
                    "CON_CONNECT_TO_NODE=" + workload.getClientConfiguration().getConsumerConnectMode(),
                    "PUB_HEARTBEAT_SEC=" + workload.getClientConfiguration().getPublisherHeartbeatSeconds(),
                    "CON_HEARTBEAT_SEC=" + workload.getClientConfiguration().getConsumerHeartbeatSeconds(),
                    "MODE=" + workload.getClientConfiguration().getMode(),
                    "GRACE_PERIOD_SEC=" + workload.getClientConfiguration().getGracePeriodSeconds(),
                    "WARMUPSECONDS=" + workload.getClientConfiguration().getWarmUpSeconds(),
                    "CHECKS=" + workload.getClientConfiguration().getChecksStr(),
                    "RUN_ORDINAL=" + runOrdinal,
                    "TAGS=" + tags,
                    "ATTEMPTS=" + "1",
                    "INFLUX_SUBPATH=" + outputData.getInfluxSubpath(),
                    "TOPOLOGY_VARIABLES=" + workload.getTopologyVariablesStr(),
                    "POLICY_VARIABLES=" + workload.getPoliciesVariablesStr(),
                    "K_CONTEXT=" + k8sMeta.getK8sContext(system.getName(), system.getK8sEngine(), runTag),
                    "KUBERNETES_ENGINE=" + system.getK8sEngine(),
                    "RABBITMQ_CLUSTER_NAME=" + k8sMeta.getRabbitClusterName(system.getName(), system.getK8sEngine()),
                    "MEMORY_GB=" + system.getHardware().getInstance().getMemoryGb());

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
                    "POLICY=" + workload.getPoliciesFile(),
                    "OVERRIDE_STEP_SECONDS=" + workload.getOverrideStepSeconds(),
                    "OVERRIDE_STEP_REPEAT=" + workload.getOverrideStepRepeat(),
                    "RUN_TAG=" + runTag,
                    "TECHNOLOGY=" + "rabbitmq",
                    "TOPOLOGY=" + workload.getTopologyFile(),
                    "VERSION=" + system.getRabbitmq().getRabbitmqVersion(),
                    "VCPU_COUNT=" + system.getHardware().getInstance().getVcpuCount(),
                    "K_CONTEXT=" + k8sMeta.getK8sContext(system.getName(), system.getK8sEngine(), runTag),
                    "KUBERNETES_ENGINE=" + system.getK8sEngine(),
                    "RABBITMQ_CLUSTER_NAME=" + k8sMeta.getRabbitClusterName(system.getName(), system.getK8sEngine()),
                    "MEMORY_GB=" + system.getHardware().getInstance().getMemoryGb());

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
