package com.jackvanlightly.rabbittesttool;

import com.jackvanlightly.rabbittesttool.actions.ActionSupervisor;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.clients.MessagePayload;
import com.jackvanlightly.rabbittesttool.clients.consumers.ConsumerGroup;
import com.jackvanlightly.rabbittesttool.clients.publishers.PublisherGroup;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.register.BenchmarkRegister;
import com.jackvanlightly.rabbittesttool.register.StepStatistics;
import com.jackvanlightly.rabbittesttool.statistics.Stats;
import com.jackvanlightly.rabbittesttool.topology.QueueGroup;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.topology.model.*;
import com.jackvanlightly.rabbittesttool.topology.TopologyGenerator;
import com.jackvanlightly.rabbittesttool.topology.model.consumers.ConsumerConfig;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.PublisherConfig;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.apache.commons.lang3.time.StopWatch;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class Orchestrator {
    private BenchmarkLogger logger;
    private BenchmarkRegister benchmarkRegister;
    private TopologyGenerator topologyGenerator;
    private QueueHosts queueHosts;
    private QueueHosts downstreamQueueHosts;
    private ExecutorService queueHostsExecutor;
    private ConnectionSettings connectionSettingsBase;
    private ConnectionSettings downstreamConnectionSettingsBase;
    private ActionSupervisor actionSupervisor;
    private ExecutorService actionSupervisorExecutor;
    private Stats stats;
    private MessageModel messageModel;
    private String mode;
    private AtomicBoolean jumpToCleanup;
    private AtomicBoolean complete;

    private List<QueueGroup> queueGroups;
    private List<PublisherGroup> publisherGroups;
    private List<ConsumerGroup> consumerGroups;

    public Orchestrator(TopologyGenerator topologyGenerator,
                        BenchmarkRegister benchmarkRegister,
                        ConnectionSettings connectionSettings,
                        ConnectionSettings downstreamConnectionSettings,
                        Stats stats,
                        MessageModel messageModel,
                        QueueHosts queueHosts,
                        QueueHosts downstreamQueueHosts,
                        ActionSupervisor actionSupervisor,
                        String mode) {
        this.logger = new BenchmarkLogger("ORCHESTRATOR");
        this.benchmarkRegister = benchmarkRegister;
        this.topologyGenerator = topologyGenerator;
        this.queueHosts = queueHosts;
        this.downstreamQueueHosts = downstreamQueueHosts;
        this.connectionSettingsBase = connectionSettings;
        this.downstreamConnectionSettingsBase = downstreamConnectionSettings;
        this.actionSupervisor = actionSupervisor;
        this.stats = stats;
        this.mode = mode;

        this.messageModel = messageModel;
        queueGroups = new ArrayList<>();
        publisherGroups = new ArrayList<>();
        consumerGroups = new ArrayList<>();

        complete = new AtomicBoolean();
        jumpToCleanup = new AtomicBoolean();

        queueHostsExecutor = Executors.newFixedThreadPool(2, new NamedThreadFactory("QueueHosts"));
        actionSupervisorExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("ActionsSupervisor"));
    }

    public boolean runBenchmark(String runId,
                                Topology topology,
                                BrokerConfiguration brokerConfiguration,
                                Duration gracePeriod,
                                Duration warmUp) {
        try {
            initialSetup(topology, brokerConfiguration);
            startActionSupervisor(topology);
            switch(topology.getTopologyType()) {
                case Fixed:
                    performFixedBenchmark(runId, topology, gracePeriod, warmUp);
                    break;
                case SingleVariable:
                    performSingleVariableBenchmark(runId, topology, gracePeriod, warmUp);
                    break;
                case MultiVariable:
                    performMultiVariableBenchmark(runId, topology, gracePeriod, warmUp);
                    break;
                default:
                    throw new RuntimeException("Non-supported topology type");
            }
            return true;
        } catch(Exception e) {
            logger.error("Failed running benchmark", e);
            return false;
        }
        finally {
            logger.info("Clean up started");
            cleanUp();
            logger.info("Clean up complete");
            complete.set(true);
        }
    }

    public void jumpToCleanup() {
        jumpToCleanup.set(true);
    }

    public AtomicBoolean isComplete() {
        return complete;
    }

    private void initialSetup(Topology topology, BrokerConfiguration brokerConfiguration) {
        if (topology.shouldDeclareArtefacts()) {
            int deletedCount = 0;
            for(VirtualHost vhost : topology.getVirtualHosts()) {
                boolean deleted = topologyGenerator.deleteVHost(vhost);
                if(deleted)
                    deletedCount++;
            }

            logger.info("Deleted " + deletedCount + " vhosts");
            if(deletedCount > 0) {
                logger.info("Waiting 5 seconds to give extra time for vhost deletion");
                waitFor(5000);
            }

            for(VirtualHost vhost : topology.getVirtualHosts()) {
                topologyGenerator.declareVHost(vhost);
            }

//            topologyGenerator.declareVHost(VirtualHost.getDefaultVHost());
        }

        logger.info("Waiting 2 seconds to give extra time for vhost creation");
        waitFor(2000);

        for(VirtualHost vhost : topology.getVirtualHosts()) {
            logger.info("Preparing vhost " + vhost.getName() + " on " + (vhost.isDownstream() ? "downstream" : "upstream"));

            if(topology.shouldDeclareArtefacts()) {
                topologyGenerator.declareExchanges(vhost);

                if(vhost.isDownstream() && topology.getFederationUpstream() != null) {
                    topologyGenerator.addUpstream(vhost,
                            topology.getFederationUpstream().getPrefetchCount(),
                            topology.getFederationUpstream().getReconnectDelaySeconds(),
                            topology.getFederationUpstream().getAckMode());
                }

                topologyGenerator.declarePolicies(vhost.getName(), topology.getPolicies(), vhost.isDownstream());
            }

            List<String> nodeNames = vhost.isDownstream() ? brokerConfiguration.getDownstreamNodeNames() : brokerConfiguration.getNodeNames();
            addQueueGroups(vhost, topology, nodeNames, topology.shouldDeclareArtefacts());
            addPublisherGroups(vhost, topology);
            addConsumerGroups(vhost, topology);
            stats.addClientGroups(publisherGroups, consumerGroups);
        }

        ClientUtils.waitFor(5000, jumpToCleanup);
        List<String> vhosts = topology.getVirtualHosts().stream().map(x -> x.getName()).collect(Collectors.toList());

        // get the list of stream queues to monitor per vhost
        Map<String, List<String>> streamQueuesToMonitor = new HashMap<>();
        for(QueueGroup qg : queueGroups) {
            if(qg.getStreamQueues().isEmpty())
                continue;

            if(!streamQueuesToMonitor.containsKey(qg.getVhostName()))
                streamQueuesToMonitor.put(qg.getVhostName(), new ArrayList<>());

            List<String> currentQueues = streamQueuesToMonitor.get(qg.getVhostName());
            currentQueues.addAll(qg.getStreamQueues());
            streamQueuesToMonitor.put(qg.getVhostName(), currentQueues);
        }

        queueHosts.updateQueueHosts(vhosts);
        queueHosts.updateStreamQueueHosts(streamQueuesToMonitor);
        queueHostsExecutor.submit(() -> queueHosts.monitorQueueHosts(vhosts, streamQueuesToMonitor));

        if(downstreamQueueHosts.clusterExists()) {
            downstreamQueueHosts.updateQueueHosts(vhosts);
            queueHosts.updateStreamQueueHosts(streamQueuesToMonitor);
            queueHostsExecutor.submit(() -> downstreamQueueHosts.monitorQueueHosts(vhosts, streamQueuesToMonitor));
        }
    }

    private void addQueueGroups(VirtualHost vhost, Topology topology, List<String> nodes, boolean declareQueues) {
        for(QueueConfig queueConfig : vhost.getQueues()) {
            QueueGroup queueGroup = new QueueGroup(vhost.getName(), queueConfig, nodes, topologyGenerator);

            if(topology.getTopologyType() == TopologyType.SingleVariable && topology.getVariableConfig().getDimension() == VariableDimension.Queues)
                queueGroup.createAllQueues(declareQueues, topology.getVariableConfig().getMaxScale().intValue());
            else if(topology.getTopologyType() == TopologyType.MultiVariable
                    && topology.getVariableConfig().hasMultiDimension(VariableDimension.Queues)) {
                queueGroup.createAllQueues(declareQueues, topology.getVariableConfig().getMaxScale(VariableDimension.Queues).intValue());
            }
            else {
                queueGroup.createInitialQueues(declareQueues);
            }

            queueGroups.add(queueGroup);
        }
    }

    private void addPublisherGroups(VirtualHost vhost, Topology topology) {
        for(PublisherConfig publisherConfig : vhost.getPublishers()) {
            ConnectionSettings connSettings = publisherConfig.isDownstream()
                    ? downstreamConnectionSettingsBase.getClone(vhost.getName())
                    : connectionSettingsBase.getClone(vhost.getName());

            QueueHosts publisherQueueHosts = publisherConfig.isDownstream()
                    ? downstreamQueueHosts : queueHosts;

            PublisherGroup publisherGroup = new PublisherGroup(connSettings,
                    publisherConfig,
                    vhost,
                    messageModel,
                    publisherQueueHosts);
            publisherGroup.createInitialPublishers();
            publisherGroups.add(publisherGroup);
        }
    }

    private void addConsumerGroups(VirtualHost vhost, Topology topology) {
        for(ConsumerConfig consumerConfig : vhost.getConsumers()) {
            ConnectionSettings connSettings = consumerConfig.isDownstream()
                    ? downstreamConnectionSettingsBase.getClone(vhost.getName())
                    : connectionSettingsBase.getClone(vhost.getName());

            QueueHosts consumerQueueHosts = consumerConfig.isDownstream()
                    ? downstreamQueueHosts : queueHosts;

            ConsumerGroup consumerGroup = new ConsumerGroup(connSettings,
                    consumerConfig,
                    vhost,
                    messageModel,
                    consumerQueueHosts);
            consumerGroup.createInitialConsumers();
            consumerGroups.add(consumerGroup);
        }
    }

    private void startActionSupervisor(Topology topology) {
        actionSupervisorExecutor.submit(() -> actionSupervisor.runActions(topology));
    }

    private void stopActionSupervisor() {
        actionSupervisor.signalStop();
    }

    private void setCountStats() {
        setConsumerCountStats();
        setPublisherCountStats();
        setQueueCountStats();
        setTargetPublisherRateStats();
        setPublisherInFlightLimitStats();
        setPublisherMaxBatchSizeStats();
        setPublisherMaxBatchSizeBytesStats();
        setPublisherMaxBatchWaitMsStats();
    }

    private void setConsumerCountStats() {
        if(!consumerGroups.isEmpty()) {
            int totalConsumers = consumerGroups.stream().map(x -> x.getConsumerCount()).reduce(0, Integer::sum);
            stats.setConsumerCount(totalConsumers);
        }
    }

    private void setPublisherCountStats() {
        if(!publisherGroups.isEmpty()) {
            int totalPublishers = publisherGroups.stream().map(x -> x.getPublisherCount()).reduce(0, Integer::sum);
            stats.setPublisherCount(totalPublishers);
        }
    }

    private void setQueueCountStats() {
        int totalQueues = queueGroups.stream().map(x -> x.getQueueCount()).reduce(0, Integer::sum);
        stats.setQueueCount(totalQueues);
    }

    private void setTargetPublisherRateStats() {
        if(!publisherGroups.isEmpty()) {
            int totalPublishers = publisherGroups.stream()
                    .map(x -> x.getPublisherCount())
                    .reduce(0, Integer::sum);

            int averageTargetRate = publisherGroups.stream()
                    .map(x -> x.getPublisherCount() * x.getPublishRatePerSecond())
                    .reduce(0, Integer::sum) / totalPublishers;
            stats.setTargetPublishRate(averageTargetRate);
        }
    }

    private void setPublisherInFlightLimitStats() {
        if(!publisherGroups.isEmpty()) {
            int totalPublishers = publisherGroups.stream()
                    .map(x -> x.getPublisherCount())
                    .reduce(0, Integer::sum);

            int averageInFlightLimit = publisherGroups.stream()
                    .map(x -> x.getPublisherCount() * x.getInFlightLimit())
                    .reduce(0, Integer::sum) / totalPublishers;
            stats.setPublisherInFlightLimit(averageInFlightLimit);
        }
    }

    private void setPublisherMaxBatchSizeStats() {
        if(!publisherGroups.isEmpty()) {
            int totalPublishers = publisherGroups.stream()
                    .map(x -> x.getPublisherCount())
                    .reduce(0, Integer::sum);

            int averageMaxBatchSize = publisherGroups.stream()
                    .map(x -> x.getPublisherCount() * x.getMaxBatchSize())
                    .reduce(0, Integer::sum) / totalPublishers;
            stats.setMaxBatchSize(averageMaxBatchSize);
        }
    }

    private void setPublisherMaxBatchSizeBytesStats() {
        if(!publisherGroups.isEmpty()) {
            int totalPublishers = publisherGroups.stream()
                    .map(x -> x.getPublisherCount())
                    .reduce(0, Integer::sum);

            int averageMaxBatchSizeBytes = publisherGroups.stream()
                    .map(x -> x.getPublisherCount() * x.getMaxBatchSizeBytes())
                    .reduce(0, Integer::sum) / totalPublishers;
            stats.setMaxBatchSizeBytes(averageMaxBatchSizeBytes);
        }
    }

    private void setPublisherMaxBatchWaitMsStats() {
        if(!publisherGroups.isEmpty()) {
            int totalPublishers = publisherGroups.stream()
                    .map(x -> x.getPublisherCount())
                    .reduce(0, Integer::sum);

            int averageMaxBatchWaitMs = publisherGroups.stream()
                    .map(x -> x.getPublisherCount() * x.getMaxBatchWaitMs())
                    .reduce(0, Integer::sum) / totalPublishers;
            stats.setMaxBatchWaitMs(averageMaxBatchWaitMs);
        }
    }

    private void performFixedBenchmark(String runId,
                                       Topology topology,
                                       Duration gracePeriod,
                                       Duration warmUp) {
        FixedConfig fixedConfig = topology.getFixedConfig();
        setCountStats();
        startAllClients(warmUp);
        executeFixedStep(runId, fixedConfig, topology);
        initiateCleanStop(gracePeriod);
    }

    private void executeFixedStep(String runId,
                             FixedConfig fixedConfig,
                             Topology topology) {
        int step = 1;
        while(step <= fixedConfig.getStepRepeat() && !jumpToCleanup.get()) {
            // wait for the ramp up time before recording and timing the run
            resetMessageSentCounts();
            waitFor(fixedConfig.getStepRampUpSeconds() * 1000);

            // start recording and log start
            stats.startRecordingStep();
            benchmarkRegister.logStepStart(runId, step, topology.getFixedConfig().getDurationSeconds(), null);

            StopWatch sw = new StopWatch();
            sw.start();

            // wait for the duration second to pass
            while (sw.getTime(TimeUnit.SECONDS) < fixedConfig.getDurationSeconds() && !jumpToCleanup.get()) {
                if(reachedStopCondition())
                    break;

                waitFor(1000);
                StepStatistics liveStepStats = stats.readCurrentStepStatistics(fixedConfig.getDurationSeconds());
                benchmarkRegister.logLiveStatistics(runId, step, liveStepStats);
            }

            // stop recording and log step results
            stats.stopRecordingStep();
            benchmarkRegister.logStepEnd(runId, step, stats.getStepStatistics(fixedConfig.getDurationSeconds()));

            step++;

            if(reachedStopCondition())
                return;
        }
    }

    private void performSingleVariableBenchmark(String runId,
                                                Topology topology,
                                                Duration gracePeriod,
                                                Duration warmUp) throws IOException {
        VariableConfig variableConfig = topology.getVariableConfig();

        // initialize dimenion values as step 1 values. Acts as an extra ramp up.
        if(variableConfig.getValueType() == ValueType.Value)
            setSingleDimensionStepValue(variableConfig, variableConfig.getDimension(), variableConfig.getValues().get(0));

        resetMessageSentCounts();
        setCountStats();
        startAllClients(warmUp);
        executeSingleVariableSteps(runId, variableConfig, topology);
        initiateCleanStop(gracePeriod);
    }

    private void executeSingleVariableSteps(String runId,
                                            VariableConfig variableConfig,
                                            Topology topology) throws IOException {
        int step = 1;

        // execute series of steps, potentially on repeat
        for(int r=0; r<variableConfig.getRepeatWholeSeriesCount(); r++) {

            // execute each step
            for (int i = 0; i < variableConfig.getStepCount(); i++) {
                int counter = 0;
                while (counter < variableConfig.getStepRepeat() && !jumpToCleanup.get()) {
                    // configure step dimension
                    setSingleDimensionStepValue(variableConfig,
                            variableConfig.getDimension(),
                            variableConfig.getValues().get(i));
                    resetMessageSentCounts();
                    setCountStats();

                    // wait for the ramp up time before recording and timing the step
                    waitFor(variableConfig.getStepRampUpSeconds() * 1000);
                    benchmarkRegister.logStepStart(runId, step, topology.getVariableConfig().getStepDurationSeconds(), variableConfig.getValues().get(i).toString());
                    stats.startRecordingStep();

                    StopWatch sw = new StopWatch();
                    sw.start();

                    // wait for step duration seconds to pass
                    while (sw.getTime(TimeUnit.SECONDS) < variableConfig.getStepDurationSeconds() && !jumpToCleanup.get()) {
                        if (reachedStopCondition())
                            break;

                        waitFor(1000);
                        StepStatistics liveStepStats = stats.readCurrentStepStatistics(variableConfig.getStepDurationSeconds());
                        benchmarkRegister.logLiveStatistics(runId, step, liveStepStats);
                    }

                    stats.stopRecordingStep();
                    benchmarkRegister.logStepEnd(runId, step, stats.getStepStatistics(variableConfig.getStepDurationSeconds()));

                    step++;
                    counter++;

                    if (reachedStopCondition())
                        return;
                }
            }
        }
    }

    private void executeSingleVariablePointSteps(String runId,
                                            VariableConfig variableConfig,
                                            Topology topology) throws IOException {
        int step = 1;

        // execute series of steps, potentially on repeat
        for(int r=0; r<variableConfig.getRepeatWholeSeriesCount(); r++) {

            // execute each step
            for (int i = 0; i < variableConfig.getStepCount(); i++) {
                // configure step dimension
                Double currentPoint = variableConfig.getValues().get(i);
                Double nextPoint = variableConfig.getValues().get(i);


                setSingleDimensionStepValue(variableConfig,
                        variableConfig.getDimension(),
                        variableConfig.getValues().get(i));
                resetMessageSentCounts();
                setCountStats();

                // wait for the ramp up time before recording and timing the step
                waitFor(variableConfig.getStepRampUpSeconds() * 1000);
                benchmarkRegister.logStepStart(runId, step, topology.getVariableConfig().getStepDurationSeconds(), variableConfig.getValues().get(i).toString());
                stats.startRecordingStep();

                StopWatch sw = new StopWatch();
                sw.start();

                // wait for step duration seconds to pass
                while (sw.getTime(TimeUnit.SECONDS) < variableConfig.getStepDurationSeconds() && !jumpToCleanup.get()) {
                    if (reachedStopCondition())
                        break;

                    waitFor(1000);
                    StepStatistics liveStepStats = stats.readCurrentStepStatistics(variableConfig.getStepDurationSeconds());
                    benchmarkRegister.logLiveStatistics(runId, step, liveStepStats);
                }

                stats.stopRecordingStep();
                benchmarkRegister.logStepEnd(runId, step, stats.getStepStatistics(variableConfig.getStepDurationSeconds()));

                step++;

                if (reachedStopCondition())
                    return;
            }
        }
    }

    private void performMultiVariableBenchmark(String runId,
                                               Topology topology,
                                               Duration gracePeriod,
                                               Duration warmUp) throws IOException {
        List<String> dimStr = Arrays.stream(topology.getVariableConfig().getMultiDimensions()).map(x -> String.valueOf(x)).collect(Collectors.toList());
        logger.info("Multi-variable with dimensions: " + String.join(" ", dimStr));

        // initialize dimenion values as step 1 values. Acts as an extra ramp up.
        VariableConfig variableConfig = topology.getVariableConfig();
        for(int vd=0; vd <variableConfig.getMultiDimensions().length; vd++) {
            setSingleDimensionStepValue(variableConfig,
                    variableConfig.getMultiDimensions()[vd],
                    variableConfig.getMultiValues().get(0)[vd]);
        }

        resetMessageSentCounts();
        setCountStats();
        startAllClients(warmUp);
        executeMultiVariableSteps(runId, variableConfig, topology);
        initiateCleanStop(gracePeriod);
    }

    private void executeMultiVariableSteps(String runId,
                                           VariableConfig variableConfig,
                                           Topology topology) throws IOException {
        int step = 1;

        // execute series of steps, potentially on repeat
        for(int r=0; r<variableConfig.getRepeatWholeSeriesCount(); r++) {

            // execute each step
            for (int i = 0; i < variableConfig.getStepCount(); i++) {

                int counter = 0;
                while (counter < variableConfig.getStepRepeat() && !jumpToCleanup.get()) {
                    String stepValues = "";
                    for (int vd = 0; vd < variableConfig.getMultiDimensions().length; vd++) {
                        setSingleDimensionStepValue(variableConfig,
                                variableConfig.getMultiDimensions()[vd],
                                variableConfig.getMultiValues().get(i)[vd]);

                        if (vd > 0)
                            stepValues += ",";
                        stepValues += variableConfig.getMultiValues().get(i)[vd].toString();
                    }
                    resetMessageSentCounts();
                    setCountStats();

                    // wait for the ramp up time before recording and timing the step
                    waitFor(variableConfig.getStepRampUpSeconds() * 1000);
                    benchmarkRegister.logStepStart(runId, step, topology.getVariableConfig().getStepDurationSeconds(), stepValues);
                    stats.startRecordingStep();

                    StopWatch sw = new StopWatch();
                    sw.start();

                    // wait for step duration seconds to pass
                    while (sw.getTime(TimeUnit.SECONDS) < variableConfig.getStepDurationSeconds() && !jumpToCleanup.get()) {
                        if (reachedStopCondition())
                            break;

                        waitFor(1000);
                        StepStatistics liveStepStats = stats.readCurrentStepStatistics(variableConfig.getStepDurationSeconds());
                        benchmarkRegister.logLiveStatistics(runId, step, liveStepStats);
                    }

                    stats.stopRecordingStep();
                    benchmarkRegister.logStepEnd(runId, step, stats.getStepStatistics(variableConfig.getStepDurationSeconds()));

                    step++;
                    counter++;

                    if (reachedStopCondition())
                        return;
                }
            }
        }
    }

    private void startAllClients(Duration warmUp) {
        for(PublisherGroup publisherGroup : publisherGroups)
            publisherGroup.performInitialPublish();

        for(ConsumerGroup consumerGroup : consumerGroups)
            consumerGroup.startInitialConsumers();

        // need to set the warm up rate before starting publishers in case they are not rated limited
        // this will avoid a short spike before warm up commences
        if(warmUp.toMillis() > 0) {
            for(PublisherGroup publisherGroup : publisherGroups)
                publisherGroup.setWarmUpModifier(0.1);
        }

        for(PublisherGroup publisherGroup : publisherGroups)
            publisherGroup.startInitialPublishers();

        if(warmUp.toMillis() > 0) {
            int rampPeriod = (int)(warmUp.toMillis() / 10);
            double modifier = 0.1;
            for (int i = 1; i <= 10; i++) {
                logger.info("Warm up step " + i + " at " + (modifier * i * 100) + "% of target rate");
                for(PublisherGroup publisherGroup : publisherGroups) {
                    publisherGroup.setWarmUpModifier(modifier * i);
                }

                ClientUtils.waitFor(rampPeriod);
            }

            // removes the warm up rate modifier and if is a non-rate limited publisher, turns off rate limiting
            for(PublisherGroup publisherGroup : publisherGroups) {
                publisherGroup.endWarmUp();
            }
        }
    }

    private void initiateCleanStop(Duration rollingGracePeriod) {
        logger.info("Signalling publishers to stop");

        // tell the publishers to stop publishing and stop
        for(PublisherGroup publisherGroup : publisherGroups)
            publisherGroup.stopAllPublishers();

        // remove the brakes from the consumers
        for(ConsumerGroup consumerGroup : consumerGroups)
            consumerGroup.setProcessingMs(0);

        // print out info on pending confirms
        int pending = publisherGroups.stream().map(x -> x.getPendingConfirmCount()).reduce(Integer::sum).get();
        logger.info("Abandoned " + pending + " publisher confirms in-flight");

        // tell the model to not expect further publishes
        messageModel.sendComplete();

        // start the grace period to allow consumers to catch up
        if(!rollingGracePeriod.equals(Duration.ZERO)) {
            logger.info("Started rolling grace period of " + rollingGracePeriod.getSeconds() + " seconds for consumers to catch up");

            int waitCounter = 1;
            while(messageModel.durationSinceLastReceipt().getSeconds() < rollingGracePeriod.getSeconds()) {
                waitFor(5000);
                long missingCount = messageModel.missingMessageCount();
                if(missingCount == 0) {
                    logger.info("All messages received");
                    break;
                }
                else {
                    long periodSince = messageModel.durationSinceLastReceipt().getSeconds();
                    logger.info(waitCounter + ") Waiting for " + missingCount + " messages to be received. Last received a message "
                            + periodSince + " seconds ago");
                }
                waitCounter++;
            }
            logger.info("Grace period complete");
        }

        logger.info("Signalling consumers to stop");
        for(ConsumerGroup consumerGroup : consumerGroups)
            consumerGroup.stopAllConsumers();

        queueHosts.stopMonitoring();
        queueHostsExecutor.shutdown();
    }

    private boolean reachedStopCondition() {
        if(mode.equals(Modes.Model)) {
            return messageModel.allMessagesReceived();
        }
        else {
            return false;
        }
    }

    private void setSingleDimensionStepValue(VariableConfig variableConfig, VariableDimension dimension, double value) throws IOException {
        switch(dimension) {
            case Consumers:
                nextConsumerStep(variableConfig, (int)value);
                break;
            case Publishers:
                nextPublisherStep(variableConfig, (int)value);
                break;
            case Queues:
                nextQueueStep(variableConfig, (int)value);
                break;
            case ConsumerAckInterval:
                nextConsumerAckIntervalStep(variableConfig, (int)value);
                break;
            case ConsumerAckIntervalMs:
                nextConsumerAckIntervalMsStep(variableConfig, (int)value);
                break;
            case ConsumerPrefetch:
                nextConsumerPrefetchStep(variableConfig, (int)value);
                break;
            case PublisherInFlightLimit:
                nextPublisherInFlightLimitStep(variableConfig, (int)value);
                break;
            case RoutingKeyIndex:
                nextRoutingKeyIndexStep(variableConfig, (int)value);
                break;
            case PublishRatePerPublisher:
                nextPublishRatePerSecondStep(variableConfig, value);
                break;
            case MessageHeaders:
                nextMessageHeadersStep(variableConfig, (int)value);
                break;
            case MessageSize:
                nextMessageSizeStep(variableConfig, (int)value);
                break;
            case ProcessingMs:
                nextProcessingMsStep(variableConfig, (int)value);
                break;
            default:
                logger.error("Dimension " + dimension + " has no step implemented");
                break;
        }
    }

    private void nextConsumerStep(VariableConfig variableConfig, int value) {
        logger.info("Next step in CONSUMERS variable dimension with value: " + value);
        for(ConsumerGroup consumerGroup : this.consumerGroups) {
            if(variableConfig.getGroup() == null) {
                while(consumerGroup.getConsumerCount() < value)
                    consumerGroup.addAndStartConsumer();
            }
            else if(consumerGroup.getGroup().equals(variableConfig.getGroup())) {
                while(consumerGroup.getConsumerCount() < value)
                    consumerGroup.addAndStartConsumer();
            }
        }
    }

    private void nextPublisherStep(VariableConfig variableConfig, int value) {
        logger.info("Next step in PUBLISHERS variable dimension with value: " + value);
        for(PublisherGroup publisherGroup : this.publisherGroups) {
            if(variableConfig.getGroup() == null) {
                while(publisherGroup.getPublisherCount() < value)
                    publisherGroup.addAndStartPublisher();
            }
            else if(publisherGroup.getGroup().equals(variableConfig.getGroup())) {
                while(publisherGroup.getPublisherCount() < value)
                    publisherGroup.addAndStartPublisher();
            }
        }
    }

    private void nextQueueStep(VariableConfig variableConfig, int value) {
        logger.info("Next step in QUEUES variable dimension with value: " + value);
        for(QueueGroup queueGroup : this.queueGroups) {
            if(variableConfig.getGroup() == null) {
                while(queueGroup.getQueueCount() < value)
                    addQueue(queueGroup);
            }
            else if(queueGroup.getGroup().equals(variableConfig.getGroup())) {
                while(queueGroup.getQueueCount() < value)
                    addQueue(queueGroup);
            }
        }
    }

    private void addQueue(QueueGroup queueGroup) {
        // add to the queue group which also creates the queue physically
        String queueName = queueGroup.addQueue();

        // broadcast the queue group change to all publisher groups
        for (PublisherGroup publisherGroup : publisherGroups)
            publisherGroup.addQueue(queueGroup.getGroup(), queueName);

        // broadcast the queue group change to all consumer groups
        for(ConsumerGroup consumerGroup : consumerGroups)
            consumerGroup.addQueue(queueGroup.getGroup(), queueName);
    }

    private void nextConsumerPrefetchStep(VariableConfig variableConfig, int value) throws IOException{
        logger.info("Next step in CONSUMER PREFETCH variable dimension with value: " + value);
        for(ConsumerGroup consumerGroup : this.consumerGroups) {
            if(variableConfig.getGroup() == null) {
                consumerGroup.setConsumerPrefetch((short)value);
            }
            else if(consumerGroup.getGroup().equals(variableConfig.getGroup())) {
                consumerGroup.setConsumerPrefetch((short)value);
            }
        }
    }

    private void nextConsumerAckIntervalStep(VariableConfig variableConfig, int value) {
        logger.info("Next step in CONSUMER ACKS variable dimension with value: " + value);
        for(ConsumerGroup consumerGroup : this.consumerGroups) {
            if(variableConfig.getGroup() == null) {
                consumerGroup.setAckInterval(value);
            }
            else if(consumerGroup.getGroup().equals(variableConfig.getGroup())) {
                consumerGroup.setAckInterval(value);
            }
        }
    }

    private void nextConsumerAckIntervalMsStep(VariableConfig variableConfig, int value) {
        logger.info("Next step in CONSUMER ACK MILLISECONDS variable dimension with value: " + value);
        for(ConsumerGroup consumerGroup : this.consumerGroups) {
            if(variableConfig.getGroup() == null) {
                consumerGroup.setAckIntervalMs(value);
            }
            else if(consumerGroup.getGroup().equals(variableConfig.getGroup())) {
                consumerGroup.setAckIntervalMs(value);
            }
        }
    }

    private void nextMessageHeadersStep(VariableConfig variableConfig, int value) {
        logger.info("Next step in HEADERS variable dimension with value: " + value);
        for(PublisherGroup publisherGroup : this.publisherGroups) {
            if(variableConfig.getGroup() == null)
                publisherGroup.setMessageHeaders(value);
            else if(publisherGroup.getGroup().equals(variableConfig.getGroup()))
                publisherGroup.setMessageHeaders(value);
        }
    }

    private void nextMessageSizeStep(VariableConfig variableConfig, int value) {
        logger.info("Next step in MESSAGE SIZE variable dimension with value: " + value);
        for(PublisherGroup publisherGroup : this.publisherGroups) {
            if(variableConfig.getGroup() == null)
                publisherGroup.setMessageSize(value);
            else if(publisherGroup.getGroup().equals(variableConfig.getGroup()))
                publisherGroup.setMessageSize(value);
        }
    }

    private void nextPublisherInFlightLimitStep(VariableConfig variableConfig, int value) {
        logger.info("Next step in IN FLIGHT LIMIT variable dimension with value: " + value);
        for(PublisherGroup publisherGroup : this.publisherGroups) {
            if(variableConfig.getGroup() == null)
                publisherGroup.setInFlightLimit(value);
            else if(publisherGroup.getGroup().equals(variableConfig.getGroup()))
                publisherGroup.setInFlightLimit(value);
        }
    }

    private void nextRoutingKeyIndexStep(VariableConfig variableConfig, int value) {
        logger.info("Next step in ROUTING KEY INDEX variable dimension with value: " + value);
        for(PublisherGroup publisherGroup : this.publisherGroups) {
            if(variableConfig.getGroup() == null)
                publisherGroup.setRoutingKeyIndex(value);
            else if(publisherGroup.getGroup().equals(variableConfig.getGroup()))
                publisherGroup.setRoutingKeyIndex(value);
        }
    }

    private void nextProcessingMsStep(VariableConfig variableConfig, int value) {
        logger.info("Next step in PROCESSING MS variable dimension with value: " + value);
        for(ConsumerGroup consumerGroup : this.consumerGroups) {
            if(variableConfig.getGroup() == null) {
                consumerGroup.setProcessingMs(value);
            }
            else if(consumerGroup.getGroup().equals(variableConfig.getGroup())) {
                consumerGroup.setProcessingMs(value);
            }
        }
    }

    private void nextPublishRatePerSecondStep(VariableConfig variableConfig, double value) {
        logger.info("Next step in PUBLISH RATE variable dimension with value: " + value);
        for(PublisherGroup publisherGroup : this.publisherGroups) {
            if(variableConfig.getGroup() == null) {
                if(variableConfig.getValueType().equals(ValueType.Value))
                    publisherGroup.setPublishRatePerSecond((int)value);
                else
                    publisherGroup.modifyPublishRatePerSecond(value);
            }
            else if(publisherGroup.getGroup().equals(variableConfig.getGroup())) {
                if (variableConfig.getValueType().equals(ValueType.Value))
                    publisherGroup.setPublishRatePerSecond((int) value);
                else
                    publisherGroup.modifyPublishRatePerSecond(value);
            }
        }
    }

    private void resetMessageSentCounts() {
        for(PublisherGroup publisherGroup : this.publisherGroups) {
            publisherGroup.resetSendCount();
        }
    }


    private void cleanUp() {
        stopActionSupervisor();

        waitFor(5000);

        logger.info("Shutting down thread pools");
        for(PublisherGroup publisherGroup : this.publisherGroups)
            publisherGroup.shutdown();

        for(ConsumerGroup consumerGroup : this.consumerGroups)
            consumerGroup.shutdown();

        logger.info("Waiting for thread pools to terminate");
        for(PublisherGroup publisherGroup : this.publisherGroups)
            publisherGroup.awaitTermination();

        for(ConsumerGroup consumerGroup : this.consumerGroups)
            consumerGroup.awaitTermination();
    }

    private void waitFor(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
