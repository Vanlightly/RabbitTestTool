package com.jackvanlightly.rabbittesttool;

import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.comparer.StatisticsComparer;
import com.jackvanlightly.rabbittesttool.metrics.InfluxMetrics;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.model.Violation;
import com.jackvanlightly.rabbittesttool.register.BenchmarkRegister;
import com.jackvanlightly.rabbittesttool.register.ConsoleRegister;
import com.jackvanlightly.rabbittesttool.register.PostgresRegister;
import com.jackvanlightly.rabbittesttool.statistics.Stats;
import com.jackvanlightly.rabbittesttool.topology.TopologyGenerator;
import com.jackvanlightly.rabbittesttool.topology.TopologyLoader;
import com.jackvanlightly.rabbittesttool.topology.model.StepOverride;
import com.jackvanlightly.rabbittesttool.topology.model.Topology;
import com.jackvanlightly.rabbittesttool.topology.model.VirtualHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AMQPBenchmarker {
    private static final Logger LOGGER = LoggerFactory.getLogger(AMQPBenchmarker.class);

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
            case Modes.RecoveryTime:
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
                case Modes.RecoveryTime:
                    CmdArguments.printRecoveryTimeHelp(System.out);
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
            BenchmarkRegister benchmarkRegister = null;
            InfluxMetrics metrics = new InfluxMetrics();

            if(arguments.hasMetrics())
                metrics.configure(arguments);

            benchmarkRegister = new ConsoleRegister(System.out);

            String topologyPath = arguments.getStr("--topology");
            String policiesPath = arguments.getStr("--policies", "none");
            StepOverride stepOverride = getStepOverride(arguments);
            BrokerConfiguration brokerConfig = getBrokerConfigWithDefaults(arguments);
            InstanceConfiguration instanceConfig = getInstanceConfigurationWithDefaults(arguments);
            ConnectionSettings connectionSettings = getConnectionSettings(arguments);
            Duration gracePeriod = Duration.ofSeconds(arguments.getInt("--grace-period-sec"));
            MessageModel messageModel = new MessageModel(true);

            ExecutorService modelExecutor = Executors.newSingleThreadExecutor();
            modelExecutor.execute(() -> messageModel.monitorProperties());

            runBenchmark(topologyPath,
                    policiesPath,
                    stepOverride,
                    benchmarkRegister,
                    metrics,
                    brokerConfig,
                    instanceConfig,
                    connectionSettings,
                    arguments.hasMetrics(),
                    messageModel,
                    gracePeriod);

            messageModel.stopMonitoring();
            modelExecutor.shutdown();

            List<Violation> violations = messageModel.getViolations();

            if(violations.isEmpty()) {
                System.out.println("No property violations detected");
            }
            else {
                System.out.println("Property violations detected!");
                for (Violation violation : violations) {
                    System.out.println(MessageFormat.format("Type: {0}, Stream: {1}, SeqNo: {2}, Timestamp {3}",
                            violation.getViolationType(),
                            violation.getMessagePayload().getStream(),
                            violation.getMessagePayload().getSequenceNumber(),
                            violation.getMessagePayload().getTimestamp()));
                }
            }

        }
        catch(Exception e) {
            LOGGER.error("Failed preparing local benchmark", e);
        }
    }

    private static void runRecoveryTimeTest(CmdArguments arguments) {
        try {
            BenchmarkRegister benchmarkRegister = null;
            InfluxMetrics metrics = new InfluxMetrics();

            if(arguments.hasMetrics())
                metrics.configure(arguments);

            benchmarkRegister = new ConsoleRegister(System.out);

            String topologyPath = arguments.getStr("--topology");
            String policiesPath = arguments.getStr("--policies", "none");
            StepOverride stepOverride = getStepOverride(arguments);
            BrokerConfiguration brokerConfig = getBrokerConfigWithDefaults(arguments);
            InstanceConfiguration instanceConfig = getInstanceConfigurationWithDefaults(arguments);
            ConnectionSettings connectionSettings = getConnectionSettings(arguments);
            Duration gracePeriod = Duration.ofSeconds(0);
            MessageModel messageModel = new MessageModel(true);

            ExecutorService modelExecutor = Executors.newSingleThreadExecutor();
            modelExecutor.execute(() -> messageModel.monitorProperties());

            runBenchmark(topologyPath,
                    policiesPath,
                    stepOverride,
                    benchmarkRegister,
                    metrics,
                    brokerConfig,
                    instanceConfig,
                    connectionSettings,
                    arguments.hasMetrics(),
                    messageModel,
                    gracePeriod);

            messageModel.stopMonitoring();
            modelExecutor.shutdown();

            System.out.println(MessageFormat.format("Messages sent: {0}, Recovery Time Seconds: {1}",
                    messageModel.getSentCount(),
                    messageModel.getMaxReceiveInterval()*1000000));
        }
        catch(Exception e) {
            LOGGER.error("Failed running recovery time test", e);
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
            String policiesPath = arguments.getStr("--policies", "none");
            StepOverride stepOverride = getStepOverride(arguments);
            BrokerConfiguration brokerConfig = getBrokerConfigWithDefaults(arguments);
            InstanceConfiguration instanceConfig = getInstanceConfigurationWithDefaults(arguments);
            ConnectionSettings connectionSettings = getConnectionSettings(arguments);

            runBenchmark(topologyPath,
                    policiesPath,
                    stepOverride,
                    benchmarkRegister,
                    metrics,
                    brokerConfig,
                    instanceConfig,
                    connectionSettings,
                    arguments.hasMetrics(),
                    new MessageModel(false),
                    Duration.ZERO);
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


                String topologyPath = arguments.getStr("--topology");
                String policiesPath = arguments.getStr("--policies", "none");
                StepOverride stepOverride = getStepOverride(arguments);
                BrokerConfiguration brokerConfig = getBrokerConfig(arguments);
                InstanceConfiguration instanceConfig = getInstanceConfiguration(arguments);
                ConnectionSettings connectionSettings = getConnectionSettings(arguments);

                runBenchmark(topologyPath,
                        policiesPath,
                        stepOverride,
                        benchmarkRegister,
                        metrics,
                        brokerConfig,
                        instanceConfig,
                        connectionSettings,
                        arguments.hasMetrics(),
                        new MessageModel(false),
                        Duration.ZERO);
            } else {
                LOGGER.error("No Postgres connection details were provided, a logged benchmark require postgres");
            }
        }
        catch(Exception e) {
            LOGGER.error("Failed preparing logged benchmark", e);
        }
    }

    private static void runBenchmark(String topologyPath,
                                     String policyPath,
                                     StepOverride stepOverride,
                                     BenchmarkRegister benchmarkRegister,
                                     InfluxMetrics metrics,
                                     BrokerConfiguration brokerConfig,
                                     InstanceConfiguration instanceConfig,
                                     ConnectionSettings connectionSettings,
                                     boolean publishRealTimeMetrics,
                                     MessageModel messageModel,
                                     Duration gracePeriod) {
        String benchmarkId = UUID.randomUUID().toString();

        Stats stats = null;
        boolean loggedStart = false;

        try {
            TopologyLoader topologyLoader = new TopologyLoader();
            Topology topology = topologyLoader.loadTopology(topologyPath, policyPath, stepOverride);

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

            Orchestrator orchestrator = new Orchestrator(topologyGenerator,
                    benchmarkRegister,
                    connectionSettings,
                    stats,
                    messageModel);


            benchmarkRegister.logBenchmarkStart(benchmarkId, brokerConfig.getTechnology(), brokerConfig.getVersion(), instanceConfig, topology);
            loggedStart = true;
            orchestrator.runBenchmark(benchmarkId, topology, brokerConfig.getNodes(), gracePeriod);

            // ensure all auto-recovering connections can recover before nuking the vhosts
            // to prevent unkillable threads
            waitFor(30000);

            if(topology.shouldDeclareArtefacts()) {
                for (VirtualHost vhost : topology.getVirtualHosts())
                    topologyGenerator.deleteVHost(vhost);
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
        LOGGER.info("Benchmark complete on node " + String.join(",", brokerConfig.getNodes()));
        //printThreads(node);
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
                arguments.getListStr("--nodes", "rabbit@rabbitmq1"));
    }

    private static BrokerConfiguration getBrokerConfig(CmdArguments arguments) {
        return new BrokerConfiguration(arguments.getStr("--technology"),
                arguments.getStr("--version"),
                arguments.getListStr("--nodes"));
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

    private static ConnectionSettings getConnectionSettings(CmdArguments cmdArguments) {
        ConnectionSettings connectionSettings = new ConnectionSettings();
        connectionSettings.setHosts(Arrays.asList(cmdArguments.getStr("--broker-hosts").split(",")));
        connectionSettings.setManagementPort(Integer.valueOf(cmdArguments.getStr("--broker-mgmt-port")));
        connectionSettings.setUser(cmdArguments.getStr("--broker-user"));
        connectionSettings.setPassword(cmdArguments.getStr("--broker-password"));
        connectionSettings.setNoTcpDelay(cmdArguments.getBoolean("--tcp-no-delay", true));
        connectionSettings.setTryConnectToLocalBroker(cmdArguments.getBoolean("--try-connect-local", false));

        return connectionSettings;
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
