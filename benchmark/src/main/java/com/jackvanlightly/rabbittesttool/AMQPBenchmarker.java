package com.jackvanlightly.rabbittesttool;

import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.ConnectToNode;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.comparer.StatisticsComparer;
import com.jackvanlightly.rabbittesttool.metrics.InfluxMetrics;
import com.jackvanlightly.rabbittesttool.model.ConsumeInterval;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.model.Violation;
import com.jackvanlightly.rabbittesttool.model.ViolationType;
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
            case Modes.Benchmark:
                runBenchmark(arguments);
                break;
            case Modes.Model:
                runModelDrivenTest(arguments);
                break;
            case Modes.Comparison:
                runComparison(arguments);
                break;
            default:
                System.out.println("Invalid mode. Modes are: " + Modes.getModes());
                System.exit(1);
        }

        System.exit(0);
    }

    private static void printHelp(CmdArguments arguments) {
        if(arguments.hasKey("--mode")) {
            String mode = arguments.getStr("--mode");
            switch (mode) {
                case Modes.Benchmark:
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
        try {
            BenchmarkRegister benchmarkRegister = null;
            if (arguments.hasKey("--postgres-jdbc-url")) {
                benchmarkRegister = new PostgresRegister(
                        arguments.getStr("--postgres-jdbc-url"),
                        arguments.getStr("--postgres-user"),
                        arguments.getStr("--postgres-pwd"));
            } else {
                LOGGER.info("To compare results, you must pass postgres connection arguments");
                System.exit(2);
            }

            StatisticsComparer comparer = new StatisticsComparer(benchmarkRegister);

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
        catch(CmdArgumentException e) {
            LOGGER.error(e.getMessage());
            System.exit(1);
        }
        catch(Exception e) {
            LOGGER.error("Exited due to error.", e);
            System.exit(2);
        }
    }

    private static void runModelDrivenTest(CmdArguments arguments) {
        try {
            String runId = arguments.getStr("--run-id", UUID.randomUUID().toString());
            BrokerConfiguration brokerConfig = getBrokerConfig(arguments);
            BenchmarkRegister benchmarkRegister = null;
            InfluxMetrics metrics = new InfluxMetrics();

            if(arguments.hasMetrics())
                metrics.configure(arguments);

            if (arguments.hasRegisterStore()) {
                benchmarkRegister = new PostgresRegister(
                        arguments.getStr("--postgres-jdbc-url"),
                        arguments.getStr("--postgres-user"),
                        arguments.getStr("--postgres-pwd"),
                        brokerConfig.getNodeNames().get(0),
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
            boolean declareArtefacts = arguments.getBoolean("--declare", true);
            String benchmarkTags = arguments.getStr("--benchmark-tags", "");
            StepOverride stepOverride = getStepOverride(arguments);
            InstanceConfiguration instanceConfig = getInstanceConfigurationWithDefaults(arguments);
            ConnectionSettings connectionSettings = getConnectionSettings(arguments, brokerConfig);
            Duration gracePeriod = Duration.ofSeconds(arguments.getInt("--grace-period-sec"));
            int unavailabilitySeconds = arguments.getInt("--unavailability-sec");

            String checkArgs = arguments.getStr("--checks", "all").toLowerCase();
            boolean checkOrdering = checkArgs.contains("ordering") || checkArgs.contains("all");
            boolean checkDataLoss = checkArgs.contains("dataloss") || checkArgs.contains("all");
            boolean checkDuplicates = checkArgs.contains("duplicates") || checkArgs.contains("all");

            MessageModel messageModel = new MessageModel(true, unavailabilitySeconds, checkOrdering, checkDataLoss, checkDuplicates);

            ExecutorService modelExecutor = Executors.newSingleThreadExecutor();
            modelExecutor.execute(() -> messageModel.monitorProperties());

            String benchmarkId = performRun(Modes.Model,
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
                    benchmarkTags,
                    declareArtefacts);

            messageModel.stopMonitoring();
            modelExecutor.shutdown();

            List<Violation> violations = messageModel.getViolations();
            long orderingViolations = violations.stream().filter(x -> x.getViolationType() == ViolationType.Ordering).count();
            long dataLossViolations = violations.stream().filter(x -> x.getViolationType() == ViolationType.Missing).count();
            long duplicationViolations = violations.stream().filter(x -> x.getViolationType() == ViolationType.NonRedeliveredDuplicate).count();
            List<ConsumeInterval> consumeIntervals = messageModel.getConsumeIntervals();

            LOGGER.info("-------------------------------------------------------");
            LOGGER.info("---------- SUMMARY ------------------------------------");
            LOGGER.info("Run ID: " + runId + ", BenchmarkId: " + benchmarkId);
            LOGGER.info("Published Count: " + messageModel.getPublishedCount());
            LOGGER.info("Consumed Count: " + messageModel.getConsumedCount());
            LOGGER.info("Ordering Property Violations: " + orderingViolations);
            LOGGER.info("Data Loss Property Violations: " + dataLossViolations);
            LOGGER.info("Non-Redelivered Duplicate Property Violations: " + duplicationViolations);
            LOGGER.info("Unavailability Periods: " + consumeIntervals.size());
            LOGGER.info("-------------------------------------------------------");

            if(!consumeIntervals.isEmpty()) {
                LOGGER.info("Max Unavailability ms: " + consumeIntervals.stream()
                        .map(x -> x.getEndMessage().getReceiveTimestamp() - x.getStartMessage().getReceiveTimestamp())
                        .max(Long::compareTo)
                        .get());
            }
            benchmarkRegister.logViolations(benchmarkId, violations);
            benchmarkRegister.logConsumeIntervals(benchmarkId, consumeIntervals, unavailabilitySeconds, messageModel.getAvailability());

            if(messageModel.getPublishedCount() == 0 || messageModel.getConsumedCount() == 0)
                System.exit(3);
            else if(dataLossViolations == 0 && consumeIntervals.isEmpty())
                System.exit(0);
            else if(dataLossViolations > 0)
                System.exit(4);
            else if(consumeIntervals.isEmpty())
                System.exit(5);
            else
                System.exit(6);
        }
        catch(CmdArgumentException e) {
            LOGGER.error(e.getMessage());
            System.exit(1);
        }
        catch(Exception e) {
            LOGGER.error("Exited due to error.", e);
            System.exit(2);
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

    private static void runBenchmark(CmdArguments arguments) {
        try {
            BenchmarkRegister benchmarkRegister = null;
            InfluxMetrics metrics = new InfluxMetrics();

            BrokerConfiguration brokerConfig = getBrokerConfig(arguments);

            if(arguments.hasMetrics())
                metrics.configure(arguments);

            if (arguments.hasRegisterStore()) {
                benchmarkRegister = new PostgresRegister(
                        arguments.getStr("--postgres-jdbc-url"),
                        arguments.getStr("--postgres-user"),
                        arguments.getStr("--postgres-pwd"),
                        brokerConfig.getNodeNames().get(0),
                        arguments.getStr("--run-id"),
                        arguments.getStr("--run-tag"),
                        arguments.getStr("--config-tag"));
            }
            else {
                benchmarkRegister = new ConsoleRegister(System.out);
            }

            String benchmarkTags = arguments.getStr("--benchmark-tags","");
            String topologyPath = arguments.getStr("--topology");
            int ordinal = arguments.getInt("--run-ordinal", 1);
            boolean declareArtefacts = arguments.getBoolean("--declare", true);
            Map<String,String> topologyVariables = arguments.getTopologyVariables();
            String policiesPath = arguments.getStr("--policies", "none");
            Map<String,String> policyVariables = arguments.getPolicyVariables();
            StepOverride stepOverride = getStepOverride(arguments);
            InstanceConfiguration instanceConfig = getInstanceConfigurationWithDefaults(arguments);
            ConnectionSettings connectionSettings = getConnectionSettings(arguments, brokerConfig);

            performRun(Modes.Benchmark,
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
                    benchmarkTags,
                    declareArtefacts);
        }
        catch(CmdArgumentException e) {
            LOGGER.error(e.getMessage());
            System.exit(1);
        }
        catch(Exception e) {
            LOGGER.error("Exited due to error.", e);
            System.exit(2);
        }
    }

    private static String performRun(String mode,
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
                                       String benchmarkTags,
                                       boolean declareArtefacts) {
        String benchmarkId = UUID.randomUUID().toString();

        Stats stats = null;
        boolean loggedStart = false;

        try {
            TopologyLoader topologyLoader = new TopologyLoader();
            Topology topology = topologyLoader.loadTopology(topologyPath, policyPath, stepOverride, topologyVariables, policyVariables, declareArtefacts);

            boolean unsafe = topology.getVirtualHosts().stream().anyMatch(x ->
                                    x.getPublishers().stream().anyMatch(p -> !p.getPublisherMode().isUseConfirms())
                                    || x.getConsumers().stream().anyMatch(c -> !c.getAckMode().isManualAcks()));

            if(unsafe && mode.equals("model"))
                LOGGER.warn("!!!WARNING!!! Model mode with unsafe clients detected. There are publishers and/or consumers without confirms/acks. Model mode may report data loss");

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

            QueueHosts downstreamHosts = new QueueHosts(topologyGenerator);
            downstreamHosts.addDownstreamHosts(brokerConfig);

            Orchestrator orchestrator = new Orchestrator(topologyGenerator,
                    benchmarkRegister,
                    connectionSettings,
                    stats,
                    messageModel,
                    queueHosts,
                    downstreamHosts,
                    mode);


            benchmarkRegister.logBenchmarkStart(benchmarkId, ordinal, brokerConfig.getTechnology(), brokerConfig.getVersion(), instanceConfig, topology, argumentsStr, benchmarkTags);
            loggedStart = true;

            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    skipToCleanUp(orchestrator, mode);
                }
            });

            orchestrator.runBenchmark(benchmarkId, topology, brokerConfig, gracePeriod);

            waitFor(10000);

            if(topology.shouldDeclareArtefacts()) {
                for (VirtualHost vhost : topology.getVirtualHosts()) {
                    LOGGER.info("Deleting vhost: " + vhost);
                    topologyGenerator.deleteVHost(vhost);
                }
            }
        }
        finally {
            if(loggedStart)
                benchmarkRegister.logBenchmarkEnd(benchmarkId);

            metrics.close();

            if(stats != null)
                stats.close();
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

    private static BrokerConfiguration getBrokerConfigWithDefaults(CmdArguments arguments) {
        return new BrokerConfiguration(arguments.getStr("--technology", "RabbitMQ"),
                arguments.getStr("--version", "?"),
                getBrokers(arguments),
                getDownstreamBrokers(arguments));
    }

    private static BrokerConfiguration getBrokerConfig(CmdArguments arguments) {
        return new BrokerConfiguration(arguments.getStr("--technology"),
                arguments.getStr("--version"),
                getBrokers(arguments),
                getDownstreamBrokers(arguments));
    }

    private static List<Broker> getBrokers(CmdArguments arguments) {
        return getBrokers(arguments, "--broker-hosts");
    }

    private static List<Broker> getDownstreamBrokers(CmdArguments arguments) {
        if(arguments.hasKey("--downstream-broker-hosts"))
            return getBrokers(arguments, "--downstream-broker-hosts");
        else
            return new ArrayList<>();
    }

    private static List<Broker> getBrokers(CmdArguments arguments, String brokerHostsArgName) {
        List<String> ipAndPorts = arguments.getListStr(brokerHostsArgName);

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
                throw new CmdArgumentException("Only supports connect to node modes of: roundrobin, random, local, non-local");
        }
    }

    private static void skipToCleanUp(Orchestrator orchestrator, String mode) {
//        try {
            if(orchestrator.isComplete()) {
                return;
            }
            else {
                orchestrator.jumpToCleanup();
                System.out.println("Jumping to cleanup ...");
//                while (!orchestrator.isComplete())
//                    Thread.sleep(1000);
//
//                if(mode.equals("model")) {
//                    LOGGER.info("Shutting down in 30 seconds ...");
//                    Thread.sleep(30000);
//                }
            }
//        } catch (InterruptedException e) {
//            LOGGER.info("INTERRUPTED! ...");
//            Thread.currentThread().interrupt();
//            e.printStackTrace();
//        }
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
