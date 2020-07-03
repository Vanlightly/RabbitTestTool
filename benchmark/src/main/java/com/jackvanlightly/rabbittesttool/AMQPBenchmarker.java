package com.jackvanlightly.rabbittesttool;

import com.jackvanlightly.rabbittesttool.actions.ActionSupervisor;
import com.jackvanlightly.rabbittesttool.clients.ConnectToNode;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.comparer.StatisticsComparer;
import com.jackvanlightly.rabbittesttool.metrics.InfluxMetrics;
import com.jackvanlightly.rabbittesttool.model.*;
import com.jackvanlightly.rabbittesttool.register.BenchmarkRegister;
import com.jackvanlightly.rabbittesttool.register.ConsoleRegister;
import com.jackvanlightly.rabbittesttool.register.PostgresRegister;
import com.jackvanlightly.rabbittesttool.statistics.Stats;
import com.jackvanlightly.rabbittesttool.topology.*;
import com.jackvanlightly.rabbittesttool.topology.model.StepOverride;
import com.jackvanlightly.rabbittesttool.topology.model.Topology;
import com.jackvanlightly.rabbittesttool.topology.model.VirtualHost;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

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

        printStage("TEST INITIATING");
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

            if(arguments.hasKey("--run-id2")) {
                comparer.generateReport(arguments.getStr("--report-dir"),
                        arguments.getStr("--run-id1"),
                        arguments.getStr("--technology1", "rabbitmq"),
                        arguments.getStr("--version1"),
                        arguments.getStr("--config-tag1"),
                        arguments.getStr("--run-id2"),
                        arguments.getStr("--technology2", "rabbitmq"),
                        arguments.getStr("--version2"),
                        arguments.getStr("--config-tag2"),
                        arguments.getStr("--desc-vars"));
            }
            else {
                comparer.generateReport(arguments.getStr("--report-dir"),
                        arguments.getStr("--run-id1"),
                        arguments.getStr("--technology1", "rabbitmq"),
                        arguments.getStr("--version1"),
                        arguments.getStr("--config-tag1"),
                        arguments.getStr("--desc-vars"));
            }
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
        String principleBroker = "";
        try {
            String runId = arguments.getStr("--run-id", UUID.randomUUID().toString());
            BrokerConfiguration brokerConfig = getBrokerConfig(arguments);
            principleBroker = brokerConfig.getHosts().get(0).getNodeName();
            BenchmarkLogger.SetBrokerName(principleBroker);
            BenchmarkLogger mainLogger = new BenchmarkLogger("MAIN");

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
                        arguments.getStr("--config-tag", "?"),
                        arguments.getBoolean("--print-live-stats", true),
                        Duration.ofSeconds(arguments.getInt("--print-live-stats-interval-seconds", 45)));
            }
            else {
                boolean printLiveStats = arguments.getBoolean("--print-live-stats", true);
                String outputDir = arguments.getStr("--output-dir", "tmp");
                benchmarkRegister = new ConsoleRegister(System.out, printLiveStats, outputDir);
            }

            Duration gracePeriod = Duration.ofSeconds(arguments.getInt("--grace-period-sec"));
            int unavailabilitySeconds = arguments.getInt("--unavailability-sec", 30);

            String checkArgs = arguments.getStr("--checks", "dataloss,duplicates,connectivity").toLowerCase();
            boolean checkOrdering = checkArgs.contains("ordering") || checkArgs.contains("all");
            boolean checkDataLoss = checkArgs.contains("dataloss") || checkArgs.contains("all");
            boolean checkDuplicates = checkArgs.contains("duplicates") || checkArgs.contains("all");
            boolean checkConnectivity = checkArgs.contains("connectivity") || checkArgs.contains("all");
            boolean checkConsumeGaps = checkArgs.contains("consumption") || checkArgs.contains("all");
            boolean includeRedelivered = arguments.getBoolean("--include-redelivered", false);

            Duration houseKeepingInterval = Duration.ofSeconds(arguments.getInt("--house-keeping-sec", 30));
            Duration messageLossThresholdDuration = Duration.ofSeconds(arguments.getInt("--message-loss-threshold-sec", 900));
            int messageLossThresholdMsgs = arguments.getInt("--message-loss-threshold-msgs", 10000);
            boolean zeroExitCode = arguments.getBoolean("--zero-exit-code", false);

            boolean logLastMsg = arguments.getBoolean("--print-last-msg", false);
            boolean logCompaction = arguments.getBoolean("--print-compaction", false);
            boolean logGaps = arguments.getBoolean("--print-gaps", false);


            MessageModel messageModel = new PartitionedModel(benchmarkRegister,
                    unavailabilitySeconds,
                    messageLossThresholdDuration,
                    messageLossThresholdMsgs,
                    houseKeepingInterval,
                    checkOrdering,
                    checkDataLoss,
                    checkDuplicates,
                    checkConnectivity,
                    checkConsumeGaps,
                    includeRedelivered,
                    logLastMsg,
                    logCompaction,
                    logGaps);

            ExecutorService modelExecutor = Executors.newCachedThreadPool(new NamedThreadFactory("MessageModel"));
            messageModel.monitorProperties(modelExecutor);

            String benchmarkId = performRun(Modes.Model,
                    arguments,
                    benchmarkRegister,
                    metrics,
                    brokerConfig,
                    arguments.hasMetrics(),
                    messageModel,
                    gracePeriod,
                    mainLogger);

            messageModel.stopMonitoring();
            modelExecutor.shutdown();

            List<Violation> violations = messageModel.getViolations();
            long orderingViolations = violations.stream()
                    .filter(x -> x.getViolationType() == ViolationType.Ordering)
                    .map(x -> x.getMessagePayload() != null ? 1 : x.getSpan().size())
                    .reduce(0L, Long::sum);
            long redeliveredOrderingViolations = violations.stream()
                    .filter(x -> x.getViolationType() == ViolationType.RedeliveredOrdering)
                    .map(x -> x.getMessagePayload() != null ? 1 : x.getSpan().size())
                    .reduce(0L, Long::sum);
            long dataLossViolations = violations.stream()
                    .filter(x -> x.getViolationType() == ViolationType.Missing)
                    .map(x -> x.getMessagePayload() != null ? 1 : x.getSpan().size())
                    .reduce(0L, Long::sum);
            long duplicationViolations = violations.stream()
                    .filter(x -> x.getViolationType() == ViolationType.NonRedeliveredDuplicate)
                    .map(x -> x.getMessagePayload() != null ? 1 : x.getSpan().size())
                    .reduce(0L, Long::sum);
            long redeliveredDuplicationViolations = violations.stream()
                    .filter(x -> x.getViolationType() == ViolationType.RedeliveredDuplicate)
                    .map(x -> x.getMessagePayload() != null ? 1 : x.getSpan().size())
                    .reduce(0L, Long::sum);


            List<ConsumeInterval> consumeIntervals = messageModel.getConsumeIntervals();
            List<DisconnectedInterval> disconnectedIntervals = messageModel.getDisconnectedIntervals();

            mainLogger.info("-------------------------------------------------------");
            mainLogger.info("---------- SUMMARY ------------------------------------");
            mainLogger.info("Run ID: " + runId + ", BenchmarkId: " + benchmarkId);
            mainLogger.info("Published Count: " + messageModel.getFinalPublishedCount());
            mainLogger.info("Consumed Count: " + messageModel.getFinalConsumedCount());
            mainLogger.info("Unconsumed Remainder Count: " + messageModel.getUnconsumedRemainderCount());
            mainLogger.info("Redelivered Message Count: " + messageModel.getFinalRedeliveredCount());

            if(checkOrdering)
                mainLogger.info("Ordering Property Violations: " + orderingViolations);
            else
                mainLogger.info("No Ordering Property Violation Checks");

            if(checkDataLoss)
                mainLogger.info("Data Loss Property Violations: " + dataLossViolations);
            else
                mainLogger.info("No Data Loss Property Checks");

            if(checkDuplicates)
                mainLogger.info("Non-Redelivered Duplicate Property Violations: " + duplicationViolations);
            else
                mainLogger.info("No Duplicate Property Checks");

            if(checkConnectivity) {
                mainLogger.info("Connection availability: " + messageModel.getConnectionAvailability());
                mainLogger.info("Disconnection periods: " + disconnectedIntervals.size());

                if(!disconnectedIntervals.isEmpty()) {
                    mainLogger.info("Max disconnection ms: " + disconnectedIntervals.stream()
                            .map(x -> x.getDuration().toMillis())
                            .max(Long::compareTo)
                            .get());
                }
            }
            else
                mainLogger.info("Connectivity not measured");

            if(checkConsumeGaps) {
                mainLogger.info("Consume availability: " + messageModel.getConsumeAvailability());
                mainLogger.info("No consume periods: " + consumeIntervals.size());

                if(!consumeIntervals.isEmpty()) {
                    mainLogger.info("Max consume gap ms: " + consumeIntervals.stream()
                            .map(x -> x.getEndMessage().getReceiveTimestamp() - x.getStartMessage().getReceiveTimestamp())
                            .max(Long::compareTo)
                            .get());
                }
            }
            else
                mainLogger.info("Consumption gaps not measured");



            if(benchmarkRegister.getClass().equals(ConsoleRegister.class)) {
                mainLogger.info("Violations recorded to: " + ((ConsoleRegister) benchmarkRegister).getViolationsFile());
                mainLogger.info("No consume periods recorded to: " + ((ConsoleRegister) benchmarkRegister).getConsumeIntervalsFile());
                mainLogger.info("Disconnection periods recorded to: " + ((ConsoleRegister) benchmarkRegister).getDisconnectedIntervalsFile());
            }


            if(dataLossViolations > 0 && messageModel.getUnconsumedRemainderCount() > 0) {
                mainLogger.info("!!!!!!!!!");
                mainLogger.info("WARNING: Leaving unconsumed messages can cause false positive data loss violations." + System.lineSeparator()
                        + " False positives can occur when all the following conditions are met:" + System.lineSeparator()
                        + " 1. Brokers became unavailable and never returned to availability." + System.lineSeparator()
                        + " 2. Multiple consumers per queue cause imperfect message ordering." + System.lineSeparator()
                        + "There can be gaps in the sequence at this unavailability cut off point that will be interpretted as message loss.");
                mainLogger.info("!!!!!!!!!");
            }

            if(includeRedelivered) {
                mainLogger.info("-------------------------------------------------------");
                mainLogger.info("---------- REDELIVERED MESSAGES INCLUDED IN CHECKS ----");
                mainLogger.info("Note that RabbitMQ provides no ordering or duplication guarantees with redelivered messages");
                mainLogger.info("Redelivered duplicates: " + redeliveredDuplicationViolations);
                mainLogger.info("Redelivered ordering issues: " + redeliveredOrderingViolations);
            }


            mainLogger.info("-------------------------------------------------------");
            mainLogger.info("---------- STREAM SUMMARY -----------------------------");
            Map<Integer, FinalSeqNos> finalSeqNos = messageModel.getFinalSeqNos();
            for(Integer stream : finalSeqNos.keySet().stream().sorted().collect(Collectors.toList())) {
                FinalSeqNos seqNos = finalSeqNos.get(stream);
                mainLogger.info(MessageFormat.format("Stream: {0,number,#}"
                                + ", First Published: {1,number,#}, First Consumed: {2,number,#}, First Lost: {3,number,#}"
                                + ", Last Published: {4,number,#}, Last Consumed: {5,number,#}, Last Lost: {6,number,#}",
                        stream, seqNos.getFirstPublished(), seqNos.getFirstConsumed(), seqNos.getFirstLost(),
                        seqNos.getLastPublished(), seqNos.getLastConsumed(), seqNos.getLastLost()));
            }

            mainLogger.info("-------------------------------------------------------");

            if(!zeroExitCode) {
                if (messageModel.getFinalPublishedCount() == 0 || messageModel.getFinalConsumedCount() == 0)
                    System.exit(3);
                else if (dataLossViolations == 0 && consumeIntervals.isEmpty())
                    System.exit(0);
                else if (dataLossViolations > 0)
                    System.exit(4);
                else if (consumeIntervals.isEmpty())
                    System.exit(5);
                else
                    System.exit(6);
            }
        }
        catch(CmdArgumentException e) {
            LOGGER.error(principleBroker + " : " + e.getMessage());
            System.exit(1);
        }
        catch(Exception e) {
            LOGGER.error(principleBroker + " : " + "Exited due to error.", e);
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
        String principleBroker = "";
        try {
            BenchmarkRegister benchmarkRegister = null;
            InfluxMetrics metrics = new InfluxMetrics();

            BrokerConfiguration brokerConfig = getBrokerConfig(arguments);
            principleBroker = brokerConfig.getHosts().get(0).getNodeName();
            BenchmarkLogger.SetBrokerName(principleBroker);
            BenchmarkLogger mainLogger = new BenchmarkLogger("MAIN");

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
                        arguments.getStr("--config-tag"),
                        arguments.getBoolean("--print-live-stats", true),
                        Duration.ofSeconds(arguments.getInt("--print-live-stats-interval-seconds", 45)));
            }
            else {
                boolean printLiveStats = arguments.getBoolean("--print-live-stats", true);
                benchmarkRegister = new ConsoleRegister(System.out, printLiveStats);
            }

            performRun(Modes.Benchmark,
                    arguments,
                    benchmarkRegister,
                    metrics,
                    brokerConfig,
                    arguments.hasMetrics(),
                    new SimpleMessageModel(false),
                    Duration.ZERO,
                    mainLogger);
        }
        catch(CmdArgumentException e) {
            LOGGER.error(principleBroker + " : " + e.getMessage());
            System.exit(1);
        }
        catch(Exception e) {
            LOGGER.error(principleBroker + " : " + "Exited due to error.", e);
            System.exit(2);
        }
    }

    private static String performRun(String mode,
                                       CmdArguments arguments,
                                       BenchmarkRegister benchmarkRegister,
                                       InfluxMetrics metrics,
                                       BrokerConfiguration brokerConfig,
                                       boolean publishRealTimeMetrics,
                                       MessageModel messageModel,
                                       Duration gracePeriod,
                                       BenchmarkLogger logger) {

        Duration warmUp = Duration.ofSeconds(arguments.getInt("--warm-up-seconds", 0));
        int attemptLimit = arguments.getInt("--attempts", 1);
        String benchmarkTags = arguments.getStr("--benchmark-tags","");
        String topologyPath = arguments.getStr("--topology");
        int ordinal = arguments.getInt("--run-ordinal", 1);
        boolean declareArtefacts = arguments.getBoolean("--declare", true);
        Map<String,String> topologyVariables = arguments.getTopologyVariables();
        String policyPath = arguments.getStr("--policies", "none");
        Map<String,String> policyVariables = arguments.getPolicyVariables();
        StepOverride stepOverride = getStepOverride(arguments);
        InstanceConfiguration instanceConfig = getInstanceConfiguration(arguments);
        String argumentsStr = arguments.getArgsStr(",");
        ConnectionSettings connectionSettings = getConnectionSettings(arguments, brokerConfig.getHosts(), false);
        ConnectionSettings downstreamConnectionSettings = getConnectionSettings(arguments, brokerConfig.getDownstreamHosts(), false);

        String description = getDescription(arguments, topologyVariables, policyVariables);

        int attempts = 0;
        boolean success = false;
        String benchmarkId = "";

        while(success == false && attempts <= attemptLimit) {
            attempts++;
            if(attempts > 1)
                waitFor(10000);

            benchmarkId = UUID.randomUUID().toString();
            messageModel.setBenchmarkId(benchmarkId);

            Stats stats = null;
            boolean loggedStart = false;

            try {
                TopologyLoader topologyLoader = new TopologyLoader();
                Topology topology = topologyLoader.loadTopology(topologyPath, policyPath, stepOverride, topologyVariables, policyVariables, declareArtefacts, description);

                boolean unsafe = topology.getVirtualHosts().stream().anyMatch(x ->
                        x.getPublishers().stream().anyMatch(p -> !p.getPublisherMode().isUseConfirms())
                                || x.getConsumers().stream().anyMatch(c -> !c.getAckMode().isManualAcks()));

                if (unsafe && mode.equals("model"))
                    logger.warn("!!!WARNING!!! Model mode with unsafe clients detected. There are publishers and/or consumers without confirms/acks. Model mode may report data loss");

                TopologyGenerator topologyGenerator = new TopologyGenerator(connectionSettings, brokerConfig);

                int sampleIntervalMs = 10000;

                if (publishRealTimeMetrics) {
                    stats = new Stats(sampleIntervalMs,
                            brokerConfig,
                            metrics.getRegistry(),
                            "amqp_");
                } else {
                    stats = new Stats(sampleIntervalMs, brokerConfig);
                }

                Broker b = brokerConfig.getHosts().get(0);
                QueueHosts queueHosts = new QueueHosts(topologyGenerator, connectionSettings, b.getIp(), b.getStreamPort());
                queueHosts.addHosts(brokerConfig);


                String dsIp = "";
                int dsPort = 0;
                if(!brokerConfig.getDownstreamHosts().isEmpty()) {
                    Broker downstreamBroker = brokerConfig.getDownstreamHosts().get(0);
                    dsIp = downstreamBroker.getIp();
                    dsPort = downstreamBroker.getStreamPort();
                }
                QueueHosts downstreamHosts = new QueueHosts(topologyGenerator,
                        downstreamConnectionSettings,
                        dsIp,
                        dsPort);
                downstreamHosts.addDownstreamHosts(brokerConfig);

                ActionSupervisor actionSupervisor = new ActionSupervisor(connectionSettings,
                        downstreamConnectionSettings,
                        queueHosts,
                        downstreamHosts,
                        topologyGenerator);

                Orchestrator orchestrator = new Orchestrator(topologyGenerator,
                        benchmarkRegister,
                        connectionSettings,
                        downstreamConnectionSettings,
                        stats,
                        messageModel,
                        queueHosts,
                        downstreamHosts,
                        actionSupervisor,
                        mode);


                benchmarkRegister.logBenchmarkStart(benchmarkId, ordinal, brokerConfig.getTechnology(), brokerConfig.getVersion(), instanceConfig, topology, argumentsStr, benchmarkTags);
                loggedStart = true;

                // this strategy prevents a second Ctrl+C from terminating
                // also since making grace period a rolling period, triggering an early shutdown is less useful
//            Runtime.getRuntime().addShutdownHook(new Thread() {
//                public void run() {
//                    skipToCleanUp(orchestrator, mode);
//                }
//            });

                printStage("TEST STARTED");
                success = orchestrator.runBenchmark(benchmarkId, topology, brokerConfig, gracePeriod, warmUp);
                if(!success) {
                    if (attempts < attemptLimit)
                        logger.info("Benchmark failed, will retry. On node " + String.join(",", brokerConfig.getNodeNames()));
                    else
                        logger.info("Benchmark failed, no more retries. On node " + String.join(",", brokerConfig.getNodeNames()));
                }

                printStage("TEST CLEANUP STARTED");

                waitFor(10000);

                if (topology.shouldDeclareArtefacts()) {
                    List<VirtualHost> vhosts = topology.getVirtualHosts();
//                    vhosts.add(VirtualHost.getDefaultVHost());

                    for (VirtualHost vhost : topology.getVirtualHosts()) {
                        logger.info("Deleting vhost: " + vhost.getName());
                        try {
                            topologyGenerator.deleteVHost(vhost);
                        }
                        catch(Exception vhostEx) {
                            logger.error("Could not delete vhost " + vhost.getName() + " on clean up", vhostEx);
                        }
                    }
                }
            } finally {
                if (loggedStart)
                    benchmarkRegister.logBenchmarkEnd(benchmarkId);

                metrics.close();

                if (stats != null)
                    stats.close();
            }
        }

        logger.info("Benchmark complete on node " + String.join(",", brokerConfig.getNodeNames()));
        //printThreads(node);

        return benchmarkId;
    }

    private static String getDescription(CmdArguments arguments,
                                  Map<String,String> topologyVariables,
                                  Map<String,String> policyVariables) {
        String descVars = arguments.getStr("--desc-vars", "");
        String description = arguments.getStr("--description", "");
        if(!descVars.isEmpty() && description.isEmpty()) {
            String[] descVarsArr = descVars.split(",");
            for(String descVar : descVarsArr) {
                String first = descVar.split(".")[0];
                String second = descVar.split(".")[1];

                if(first.equals("--tvar")) {
                    if(topologyVariables.containsKey(second))
                        description += second + "=" + topologyVariables.get(second)+",";
                } else if(first.equals("--pvar")) {
                    if(policyVariables.containsKey(second))
                        description += second + "=" + policyVariables.get(second)+",";
                }
            }
        }

        return description;
    }

    private static InstanceConfiguration getInstanceConfiguration(CmdArguments arguments) {
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

    private static BrokerConfiguration getBrokerConfig(CmdArguments arguments) {
        return new BrokerConfiguration(arguments.getStr("--technology", "rabbitmq"),
                arguments.getStr("--version"),
                getBrokers(arguments),
                getDownstreamBrokers(arguments));
    }

    private static List<Broker> getBrokers(CmdArguments arguments) {
        return getBrokers(arguments, "--broker-hosts", "--stream-ports");
    }

    private static List<Broker> getDownstreamBrokers(CmdArguments arguments) {
        if(arguments.hasKey("--downstream-broker-hosts"))
            return getBrokers(arguments, "--downstream-broker-hosts", "--downstream-stream-ports");
        else
            return new ArrayList<>();
    }

    private static List<Broker> getBrokers(CmdArguments arguments, String brokerHostsArgName, String streamPortsArgName) {
        List<String> ipAndPorts = arguments.getListStr(brokerHostsArgName);

        String defaultStreamPorts = "";
        for(int i=0; i<ipAndPorts.size(); i++) {
            defaultStreamPorts += "5555";

            if(i==0)
                defaultStreamPorts += ",";
        }
        List<String> streamPorts = arguments.getListStr(streamPortsArgName, defaultStreamPorts);

        String ip = ipAndPorts.get(0).split(":")[0];
        NamesGetter ng = new NamesGetter(ip, arguments.getStr("--broker-mgmt-port"), arguments.getStr("--broker-user"), arguments.getStr("--broker-password"));
        List<String> nodeNames = ng.getNodeNames();
        if(nodeNames.size() < ipAndPorts.size())
            throw new TopologyException("Less nodes are online than indicated by command line arguments");

        List<Broker> hosts = new ArrayList<>();
        for(int i=0; i<ipAndPorts.size(); i++) {
            Broker b = new Broker(ipAndPorts.get(i).split(":")[0],
                    ipAndPorts.get(i).split(":")[1],
                    nodeNames.get(i),
                    streamPorts.size() == ipAndPorts.size() ? streamPorts.get(i) : "5555");

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

    private static ConnectionSettings getConnectionSettings(CmdArguments cmdArguments,
                                                            List<Broker> hosts,
                                                            boolean isDownstream) {
        ConnectionSettings connectionSettings = new ConnectionSettings();
        connectionSettings.setHosts(hosts);
        connectionSettings.setManagementPort(Integer.valueOf(cmdArguments.getStr("--broker-mgmt-port")));
        connectionSettings.setUser(cmdArguments.getStr("--broker-user"));
        connectionSettings.setPassword(cmdArguments.getStr("--broker-password"));
        connectionSettings.setNoTcpDelay(cmdArguments.getBoolean("--tcp-no-delay", true));
        connectionSettings.setPublisherConnectToNode(getConnectToNode(cmdArguments.getStr("--pub-connect-to-node", "roundrobin")));
        connectionSettings.setConsumerConnectToNode(getConnectToNode(cmdArguments.getStr("--con-connect-to-node", "roundrobin")));
        connectionSettings.setDownstream(isDownstream);

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

//    private static void skipToCleanUp(Orchestrator orchestrator, String mode) {
//        try {
//            if(orchestrator.isComplete()) {
//                return;
//            }
//            else {
//                orchestrator.jumpToCleanup();
//                System.out.println("Jumping to cleanup ...");
//                while (!orchestrator.isComplete())
//                    Thread.sleep(1000);
//
//                if(mode.equals("model")) {
//                    LOGGER.info("Shutting down in 30 seconds ...");
//                    Thread.sleep(30000);
//                }
//            }
//        } catch (InterruptedException e) {
//            LOGGER.info("INTERRUPTED! ...");
//            Thread.currentThread().interrupt();
//            e.printStackTrace();
//        }
//    }

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

    private static void printStage(String stage) {
        LOGGER.info("-------------------------------------------------");
        LOGGER.info(stage);
        LOGGER.info("-------------------------------------------------");
    }
}
