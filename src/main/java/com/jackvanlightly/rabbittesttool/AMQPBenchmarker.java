package com.jackvanlightly.rabbittesttool;

import com.jackvanlightly.rabbittesttool.clients.ConnectToNode;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.comparer.StatisticsComparer;
import com.jackvanlightly.rabbittesttool.metrics.InfluxMetrics;
import com.jackvanlightly.rabbittesttool.model.ConsumeInterval;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.model.Violation;
import com.jackvanlightly.rabbittesttool.register.BenchmarkRegister;
import com.jackvanlightly.rabbittesttool.register.ConsoleRegister;
import com.jackvanlightly.rabbittesttool.register.PostgresRegister;
import com.jackvanlightly.rabbittesttool.statistics.Stats;
import com.jackvanlightly.rabbittesttool.topology.*;
import com.jackvanlightly.rabbittesttool.topology.model.StepOverride;
import com.jackvanlightly.rabbittesttool.topology.model.Topology;
import com.jackvanlightly.rabbittesttool.topology.model.VirtualHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AMQPBenchmarker {
    private static final Logger LOGGER = LoggerFactory.getLogger("MAIN");

    public static void main(String[] args) {

        CmdArguments arguments = new CmdArguments(args);
        if(arguments.hasRequestedHelp()) {
            printHelp(arguments);
            System.exit(0);
        }

        if(!arguments.hasKey("--mode")) {
            System.out.println("You must specify the mode using the --mode argument. Modes are: " + Modes.getModes());
            System.exit(1);
        }

        arguments.printArguments();

        String mode = arguments.getStr("--mode");
        switch (mode) {
            case Modes.SimpleBenchmark:
                runSimpleBenchmark(arguments);
                break;
            case Modes.LoggedBenchmark:
                runLoggedBenchmark(arguments);
                break;
            case Modes.Model:
                runModelDrivenTest(arguments);
                break;
            case Modes.Comparison:
                runComparison(arguments);
                break;
            default:
                System.out.println("Invalid mode. Modes are: " + Modes.getModes());
        }
    }

    private static void printHelp(CmdArguments arguments) {
        if(arguments.hasKey("--mode")) {
            String mode = arguments.getStr("--mode");
            switch (mode) {
                case Modes.SimpleBenchmark:
                    CmdArguments.printLocalBenchmarkHelp(System.out);
                    break;
                case Modes.LoggedBenchmark:
                    CmdArguments.printLoggedBenchmarkHelp(System.out);
                    break;
                case Modes.Model:
                    CmdArguments.printModelHelp(System.out);
                    break;
                case Modes.Comparison:
                    CmdArguments.printComparisonHelp(System.out);
                    break;
                default:
                    System.out.println("Invalid mode. Modes are: " + Modes.getModes());
            }
        }
        else {
            CmdArguments.printTopLevelHelp(System.out);
        }
    }

    private static void runComparison(CmdArguments arguments) {
        BenchmarkRegister benchmarkRegister = null;
        if(arguments.hasKey("--postgres-jdbc-url")) {
            benchmarkRegister = new PostgresRegister(
                    arguments.getStr("--postgres-jdbc-url"),
                    arguments.getStr("--postgres-user"),
                    arguments.getStr("--postgres-pwd"));
        }
        else {
            LOGGER.info("To compare results, you must pass postgres connection arguments");
        }

        StatisticsComparer comparer = new StatisticsComparer(benchmarkRegister);

        try {
            comparer.generateReport(arguments.getStr("--report-dir"),
                    arguments.getStr("--run-id1"),
                    arguments.getStr("--technology1"),
                    arguments.getStr("--version1"),
                    arguments.getStr("--config-tag1"),
                    arguments.getStr("--run-id2"),
                    arguments.getStr("--technology2"),
                    arguments.getStr("--version2"),
                    arguments.getStr("--config-tag2"));
        }
        catch(Exception e) {
            LOGGER.error("Failed generating a comparison report", e);
        }
    }

    private static void runModelDrivenTest(CmdArguments arguments) {
        try {
            String runId = arguments.getStr("--run-id", UUID.randomUUID().toString());
            BenchmarkRegister benchmarkRegister = null;
            InfluxMetrics metrics = new InfluxMetrics();

            if(arguments.hasMetrics())
                metrics.configure(arguments);

            if (arguments.hasRegisterStore()) {
                benchmarkRegister = new PostgresRegister(
                        arguments.getStr("--postgres-jdbc-url"),
                        arguments.getStr("--postgres-user"),
                        arguments.getStr("--postgres-pwd"),
                        arguments.getListStr("--nodes").get(0),
                        runId,
                        arguments.getStr("--run-tag", "?"),
                        arguments.getStr("--config-tag", "?"));
            }
            else {
                benchmarkRegister = new ConsoleRegister(System.out);
            }

            String topologyPath = arguments.getStr("--topology");
            Map<String,String> topologyVariables = arguments.getTopologyVariables();
            String policiesPath = arguments.getStr("--policies", "none");
            Map<String,String> policyVariables = arguments.getPolicyVariables();
            int ordinal = arguments.getInt("--run-ordinal", 1);
            String benchmarkTags = arguments.getStr("--benchmark-tags", "");
            StepOverride stepOverride = getStepOverride(arguments);
            BrokerConfiguration brokerConfig = getBrokerConfigWithDefaults(arguments);
            InstanceConfiguration instanceConfig = getInstanceConfigurationWithDefaults(arguments);
            ConnectionSettings connectionSettings = getConnectionSettings(arguments, brokerConfig);
            Duration gracePeriod = Duration.ofSeconds(arguments.getInt("--grace-period-sec"));
            MessageModel messageModel = new MessageModel(true);

            ExecutorService modelExecutor = Executors.newSingleThreadExecutor();
            modelExecutor.execute(() -> messageModel.monitorProperties());

            String benchmarkId = runBenchmark(Modes.Model,
                    topologyPath,
                    topologyVariables,
                    policiesPath,
                    policyVariables,
                    ordinal,
                    stepOverride,
                    benchmarkRegister,
                    metrics,
                    brokerConfig,
                    instanceConfig,
                    connectionSettings,
                    arguments.hasMetrics(),
                    messageModel,
                    gracePeriod,
                    arguments.getArgsStr(","),
                    benchmarkTags);

            messageModel.stopMonitoring();
            modelExecutor.shutdown();

            List<Violation> violations = messageModel.getViolations();
            List<ConsumeInterval> consumeIntervals = messageModel.getConsumeIntervals();

            LOGGER.info("-------------------------------------------------------");
            LOGGER.info("---------- SUMMARY ------------------------------------");
            LOGGER.info("Run ID: " + runId + ", BenchmarkId: " + benchmarkId);
            LOGGER.info("Property Violations: " + violations.size());
            LOGGER.info("Unavailability Periods: " + consumeIntervals.size());
            LOGGER.info("-------------------------------------------------------");

            if(!consumeIntervals.isEmpty()) {
                LOGGER.info("Max Unavailability ms: " + consumeIntervals.stream()
                        .map(x -> x.getEndMessage().getReceiveTimestamp() - x.getStartMessage().getReceiveTimestamp())
                        .max(Long::compareTo)
                        .get());
            }
            benchmarkRegister.logViolations(benchmarkId, violations);
            benchmarkRegister.logConsumeIntervals(benchmarkId, consumeIntervals);
        }
        catch(Exception e) {
            LOGGER.error("Failed during model driven test", e);
        }
    }

    private static void runSimpleBenchmark(CmdArguments arguments) {
        try {
            BenchmarkRegister benchmarkRegister = null;
            InfluxMetrics metrics = new InfluxMetrics();

            if(arguments.hasMetrics())
                metrics.configure(arguments);

            benchmarkRegister = new ConsoleRegister(System.out);

            String topologyPath = arguments.getStr("--topology");
            Map<String,String> topologyVariables = arguments.getTopologyVariables();
            String policiesPath = arguments.getStr("--policies", "none");
            Map<String,String> policyVariables = arguments.getPolicyVariables();
            int ordinal = arguments.getInt("--run-ordinal", 1);
            String benchmarkTags = arguments.getStr("--benchmark-tags", "");
            StepOverride stepOverride = getStepOverride(arguments);
            BrokerConfiguration brokerConfig = getBrokerConfigWithDefaults(arguments);
            InstanceConfiguration instanceConfig = getInstanceConfigurationWithDefaults(arguments);
            ConnectionSettings connectionSettings = getConnectionSettings(arguments, brokerConfig);

            runBenchmark(Modes.SimpleBenchmark,
                    topologyPath,
                    topologyVariables,
                    policiesPath,
                    policyVariables,
                    ordinal,
                    stepOverride,
                    benchmarkRegister,
                    metrics,
                    brokerConfig,
                    instanceConfig,
                    connectionSettings,
                    arguments.hasMetrics(),
                    new MessageModel(false),
                    Duration.ZERO,
                    arguments.getArgsStr(","),
                    benchmarkTags);
        }
        catch(Exception e) {
            LOGGER.error("Failed preparing local benchmark", e);
        }
    }

    private static StepOverride getStepOverride(CmdArguments cmdArguments) {

        StepOverride stepOverride = new StepOverride();
        stepOverride.setStepSeconds(cmdArguments.getInt("--override-step-seconds", 0));
        stepOverride.setStepRepeat(cmdArguments.getInt("--override-step-repeat", 0));
        stepOverride.setMessageSize(cmdArguments.getInt("--override-step-msg-size", 0));
        stepOverride.setMessageLimit(cmdArguments.getInt("--override-step-msg-limit", 0));
        stepOverride.setMsgsPerSecondPerPublisher(cmdArguments.getInt("--override-step-pub-rate", -1));

        return stepOverride;
    }

    private static void runLoggedBenchmark(CmdArguments arguments) {
        try {
            BenchmarkRegister benchmarkRegister = null;
            InfluxMetrics metrics = new InfluxMetrics();

            if(arguments.hasMetrics())
                metrics.configure(arguments);

            if (arguments.hasRegisterStore()) {
                benchmarkRegister = new PostgresRegister(
                        arguments.getStr("--postgres-jdbc-url"),
                        arguments.getStr("--postgres-user"),
                        arguments.getStr("--postgres-pwd"),
                        arguments.getListStr("--nodes").get(0),
                        arguments.getStr("--run-id"),
                        arguments.getStr("--run-tag"),
                        arguments.getStr("--config-tag"));


                String benchmarkTags = arguments.getStr("--benchmark-tags","");
                String topologyPath = arguments.getStr("--topology");
                int ordinal = arguments.getInt("--run-ordinal", 1);
                Map<String,String> topologyVariables = arguments.getTopologyVariables();
                String policiesPath = arguments.getStr("--policies", "none");
                Map<String,String> policyVariables = arguments.getPolicyVariables();
                StepOverride stepOverride = getStepOverride(arguments);
                BrokerConfiguration brokerConfig = getBrokerConfig(arguments);
                InstanceConfiguration instanceConfig = getInstanceConfiguration(arguments);
                ConnectionSettings connectionSettings = getConnectionSettings(arguments, brokerConfig);

                runBenchmark(Modes.LoggedBenchmark,
                        topologyPath,
                        topologyVariables,
                        policiesPath,
                        policyVariables,
                        ordinal,
                        stepOverride,
                        benchmarkRegister,
                        metrics,
                        brokerConfig,
                        instanceConfig,
                        connectionSettings,
                        arguments.hasMetrics(),
                        new MessageModel(false),
                        Duration.ZERO,
                        arguments.getArgsStr(","),
                        benchmarkTags);
            } else {
                LOGGER.error("No Postgres connection details were provided, a logged benchmark require postgres");
            }
        }
        catch(Exception e) {
            LOGGER.error("Failed preparing logged benchmark", e);
        }
    }

    private static String runBenchmark(String mode,
                                       String topologyPath,
                                       Map<String,String> topologyVariables,
                                       String policyPath,
                                       Map<String,String> policyVariables,
                                       int ordinal,
                                       StepOverride stepOverride,
                                       BenchmarkRegister benchmarkRegister,
                                       InfluxMetrics metrics,
                                       BrokerConfiguration brokerConfig,
                                       InstanceConfiguration instanceConfig,
                                       ConnectionSettings connectionSettings,
                                       boolean publishRealTimeMetrics,
                                       MessageModel messageModel,
                                       Duration gracePeriod,
                                       String argumentsStr,
                                       String benchmarkTags) {
        String benchmarkId = UUID.randomUUID().toString();

        Stats stats = null;
        boolean loggedStart = false;

        try {
            TopologyLoader topologyLoader = new TopologyLoader();
            Topology topology = topologyLoader.loadTopology(topologyPath, policyPath, stepOverride, topologyVariables, policyVariables);

            TopologyGenerator topologyGenerator = new TopologyGenerator(connectionSettings, brokerConfig);

            int sampleIntervalMs = 10000;

            if(publishRealTimeMetrics) {
                String instanceTag = instanceConfig.getInstanceType() + "-" + instanceConfig.getVolume() + "-" + instanceConfig.getFileSystem() + "-" + instanceConfig.getTenancy();
                stats = new Stats(sampleIntervalMs,
                        brokerConfig,
                        instanceTag,
                        metrics.getRegistry(),
                        "amqp_");
            }
            else {
                stats = new Stats(sampleIntervalMs, brokerConfig);
            }

            QueueHosts queueHosts = new QueueHosts(topologyGenerator);
            queueHosts.addHosts(brokerConfig);

            Orchestrator orchestrator = new Orchestrator(topologyGenerator,
                    benchmarkRegister,
                    connectionSettings,
                    stats,
                    messageModel,
                    queueHosts,
                    mode);


            benchmarkRegister.logBenchmarkStart(benchmarkId, ordinal, brokerConfig.getTechnology(), brokerConfig.getVersion(), instanceConfig, topology, argumentsStr, benchmarkTags);
            loggedStart = true;

            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    try {
                        if(orchestrator.isComplete()) {
                            return;
                        }
                        else {
                            orchestrator.jumpToCleanup();
                            System.out.println("Jumping to cleanup ...");
                            while (!orchestrator.isComplete())
                                Thread.sleep(1000);

                            if(mode.equals("model")) {
                                LOGGER.info("Shutting down in 30 seconds ...");
                                Thread.sleep(30000);
                            }
                        }
                    } catch (InterruptedException e) {
                        LOGGER.info("INTERRUPTED! ...");
                        Thread.currentThread().interrupt();
                        e.printStackTrace();
                    }
                }
            });

            orchestrator.runBenchmark(benchmarkId, topology, brokerConfig, gracePeriod);

            // ensure all auto-recovering connections can recover before nuking the vhosts
            // to prevent unkillable threads
            waitFor(10000);

            if(topology.shouldDeclareArtefacts()) {
                for (VirtualHost vhost : topology.getVirtualHosts()) {
                    LOGGER.info("Deleting vhost: " + vhost);
                    topologyGenerator.deleteVHost(vhost);
                }
            }
        }
        catch(Exception e) {
            LOGGER.error("Unexpected error in main", e);
            if(loggedStart)
                benchmarkRegister.logException(benchmarkId, e);
        }
        finally {
            try {
                if(loggedStart)
                    benchmarkRegister.logBenchmarkEnd(benchmarkId);

                metrics.close();

                if(stats != null)
                    stats.close();
            }
            catch(Exception e) {
                LOGGER.error("Unexpected error in main on completion", e);
            }
        }
        LOGGER.info("Benchmark complete on node " + String.join(",", brokerConfig.getNodeNames()));
        //printThreads(node);

        return benchmarkId;
    }

    private static InstanceConfiguration getInstanceConfigurationWithDefaults(CmdArguments arguments) {
        String instance = arguments.getStr("--instance", "?");
        String hosting = arguments.getStr("--hosting", "?");
        String volume = arguments.getStr("--volume", "?");
        String fileSystem = arguments.getStr("--filesystem", "?");
        String tenancy = arguments.getStr("--tenancy", "?");
        short coreCount = Short.parseShort(arguments.getStr("--core-count", "0"));
        short threadsPerCore = Short.parseShort(arguments.getStr("--threads-per-core", "0"));

        return new InstanceConfiguration(instance,
                volume,
                fileSystem,
                tenancy,
                hosting,
                coreCount,
                threadsPerCore);
    }

    private static InstanceConfiguration getInstanceConfiguration(CmdArguments arguments) {
        String instance = arguments.getStr("--instance");
        String hosting = arguments.getStr("--hosting");
        String volume = arguments.getStr("--volume");
        String fileSystem = arguments.getStr("--filesystem");
        String tenancy = arguments.getStr("--tenancy");
        short coreCount = Short.parseShort(arguments.getStr("--core-count"));
        short threadsPerCore = Short.parseShort(arguments.getStr("--threads-per-core"));

        return new InstanceConfiguration(instance,
                volume,
                fileSystem,
                tenancy,
                hosting,
                coreCount,
                threadsPerCore);
    }

    private static BrokerConfiguration getBrokerConfigWithDefaults(CmdArguments arguments) {
        return new BrokerConfiguration(arguments.getStr("--technology", "?"),
                arguments.getStr("--version", "?"),
                getBrokers(arguments));
    }

    private static BrokerConfiguration getBrokerConfig(CmdArguments arguments) {
        return new BrokerConfiguration(arguments.getStr("--technology"),
                arguments.getStr("--version"),
                getBrokers(arguments));
    }

    private static List<Broker> getBrokers(CmdArguments arguments) {
        List<String> ipAndPorts = arguments.getListStr("--broker-hosts");

        String ip = ipAndPorts.get(0).split(":")[0];
        NamesGetter ng = new NamesGetter(ip, arguments.getStr("--broker-mgmt-port"), arguments.getStr("--broker-user"), arguments.getStr("--broker-password"));
        List<String> nodeNames = ng.getNodeNames();
        if(nodeNames.size() < ipAndPorts.size())
            throw new TopologyException("Less nodes are online than indicated by command line arguments");

        List<Broker> hosts = new ArrayList<>();
        for(int i=0; i<ipAndPorts.size(); i++) {
            Broker b = new Broker(ipAndPorts.get(i).split(":")[0],
                    ipAndPorts.get(i).split(":")[1],
                    nodeNames.get(i));

            hosts.add(b);
        }

        return hosts;
    }

    // for debugging when a thread kept the JVM going
    private static void printThreads(String node) {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for(Thread t : threadSet) {
            System.out.println(node + " @@ " + t.toString() + ". isDaemon: " + t.isDaemon() + " isAlive: " + t.isAlive() + " isInterrupted: " + t.isInterrupted() + " state: " + t.getState().toString());
            if(t.getStackTrace().length > 0) {
                for(StackTraceElement ste : t.getStackTrace()) {
                    System.out.println(node + " @@    " + ste);
                }
            }
        }
    }

    private static ConnectionSettings getConnectionSettings(CmdArguments cmdArguments, BrokerConfiguration brokerConfig) {
        ConnectionSettings connectionSettings = new ConnectionSettings();
        connectionSettings.setHosts(brokerConfig.getHosts());
        connectionSettings.setManagementPort(Integer.valueOf(cmdArguments.getStr("--broker-mgmt-port")));
        connectionSettings.setUser(cmdArguments.getStr("--broker-user"));
        connectionSettings.setPassword(cmdArguments.getStr("--broker-password"));
        connectionSettings.setNoTcpDelay(cmdArguments.getBoolean("--tcp-no-delay", true));
        connectionSettings.setPublisherConnectToNode(getConnectToNode(cmdArguments.getStr("--pub-connect-to-node", "roundrobin")));
        connectionSettings.setConsumerConnectToNode(getConnectToNode(cmdArguments.getStr("--con-connect-to-node", "roundrobin")));

        return connectionSettings;
    }

    private static ConnectToNode getConnectToNode(String value) {
        switch (value.toLowerCase()) {
            case "roundrobin": return ConnectToNode.RoundRobin;
            case "random": return ConnectToNode.Random;
            case "local": return ConnectToNode.Local;
            case "non-local": return ConnectToNode.NonLocal;
            default:
                throw new IllegalArgumentException("Only supports connect to node modes of: roundrobin, random, local, non-local");
        }
    }

    private static void waitFor(int ms) {
        try {
            int waited = 0;
            while(waited < ms) {
                Thread.sleep(10);
                waited += 10;
            }
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
