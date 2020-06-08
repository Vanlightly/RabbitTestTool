package com.jackvanlightly.rabbittesttool.topology;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.topology.model.actions.*;
import com.jackvanlightly.rabbittesttool.topology.model.consumers.AckMode;
import com.jackvanlightly.rabbittesttool.topology.model.consumers.ConsumerConfig;
import com.jackvanlightly.rabbittesttool.topology.model.*;
import com.jackvanlightly.rabbittesttool.topology.model.publishers.*;
import com.rabbitmq.stream.ByteCapacity;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

public class TopologyLoader {

    private BenchmarkLogger logger;
//    private Set<String> intFields = new HashSet<>(Arrays.asList("scale", "inFlightLimit", "consumerPrefetch", "ackInterval",
//            "priority", "stepDurationSeconds", "durationSeconds", "rampUpSeconds", "msgsPerSecondPerPublisher", "messageSize",
//            "headersPerMessage", "messageCount", "thresholdSeconds", "initialPublish"));
//    private Set<String> boolFields = new HashSet<>(Arrays.asList("useConfirms", "manualAcks"));
    private Pattern variablePattern;

    public TopologyLoader() {
        variablePattern = Pattern.compile("\\s*(\\{\\{\\s*var.(\\w+)\\s*\\}\\})\\w*");
    }

    public Topology loadTopology(String topologyPath,
                                 String policyPath,
                                 StepOverride stepOverride,
                                 Map<String,String> suppliedTopologyVariables,
                                 Map<String,String> suppliedPolicyVariables,
                                 boolean declareArtefacts,
                                 String suppliedDescription) {
        logger = new BenchmarkLogger("TOPOLOGY_LOADER");
        logger.info("Loading topology from: " + topologyPath);
        JSONObject topologyJson = loadJson(topologyPath);
        Map<String,String> topologyVariableDefaults = getVariableDefaults(topologyJson);
        makeVariableReplacements(topologyJson, suppliedTopologyVariables, topologyVariableDefaults);

        Topology topology = new Topology();
        topology.setDeclareArtefacts(declareArtefacts);
        topology.setTopologyJson(topologyJson.toString());

        Path path = Paths.get(topologyPath);
        Path fileName = path.getFileName();
        topology.setTopologyName(fileName.toString());

        topology.setBenchmarkType(getBenchmarkType(getMandatoryStrValue(topologyJson, "benchmarkType")));
        topology.setTopologyType(getTopologyType(getMandatoryStrValue(topologyJson, "topologyType")));
        topology.setDescription(getDescription(suppliedDescription, suppliedTopologyVariables, topologyVariableDefaults, suppliedPolicyVariables, suppliedPolicyVariables));

        topology.setVirtualHosts(loadAllVirtualHosts(topologyJson.getJSONArray("topologyGroups"),
                topology.getBenchmarkType(),
                stepOverride));

        if(!topologyJson.has("dimensions"))
            throw new TopologyException("No 'dimensions' object defined");

        JSONObject dimensionJson = topologyJson.getJSONObject("dimensions");
        switch(topology.getTopologyType()) {
            case Fixed:
                if(dimensionJson.has("fixedDimensions"))
                    topology.setFixedConfig(getFixedConfig(dimensionJson.getJSONObject("fixedDimensions"), stepOverride));
                else
                    throw new TopologyException("Fixed topology mode requires a 'fixedDimensions' object");
                break;
            case SingleVariable:
                if(dimensionJson.has("singleDimension")) {
                    topology.setVariableConfig(getSingleVariableConfig(dimensionJson.getJSONObject("singleDimension"), stepOverride));
                }
                else
                    throw new TopologyException("Single dimension topology mode requires a 'singleDimension' object");
                break;
            case MultiVariable:
                if(dimensionJson.has("multipleDimensions"))
                    topology.setVariableConfig(getMultiVariableConfig(dimensionJson.getJSONObject("multipleDimensions"), stepOverride));
                else
                    throw new TopologyException("Multiple dimension topology mode requires a 'multipleDimensions' object");
                break;
        }

        if(!policyPath.endsWith("none")) {
            addPolicies(topology, policyPath, suppliedPolicyVariables);
        }

        return topology;
    }

    private void addPolicies(Topology topology, String policyPath, Map<String,String> suppliedPolicyVariables) {
        JSONObject policiesJson = loadJson(policyPath);
        Map<String,String> policyVariableDefaults = getVariableDefaults(policiesJson);
        makeVariableReplacements(policiesJson, suppliedPolicyVariables, policyVariableDefaults);
        topology.setPoliciesJson(policiesJson.toString());

        topology.setFederationUpstream(getFederationUpstream(policiesJson));

        List<Policy> policies = getPolicies(policiesJson);
        List<Policy> finalPolicies = new ArrayList<>();
        for(Policy policy : policies) {
            List<Property> finalProps = new ArrayList<>();
            List<Property> props = removeIncompatibleProps(policy.getProperties());
            for(Property prop : props) {
                if(isQuorumQueueProperty(prop)) {
                    addPropertyToQueue(topology, prop, policy.getPattern(), policy.isDownstream());
                }
                else if(isStreamQueueProperty(prop)) {
                    addPropertyToQueue(topology, prop, policy.getPattern(), policy.isDownstream());
                }
                else {
                    finalProps.add(prop);
                }
            }
            if(!finalProps.isEmpty()) {
                policy.setProperties(finalProps);
                finalPolicies.add(policy);
            }
        }

        topology.setPolicies(finalPolicies);
    }

    private List<Property> removeIncompatibleProps(List<Property> properties) {
        if(properties.stream().anyMatch(x -> x.getKey().equals("ha-mode") && x.getValue().equals("all"))) {
            properties = properties.stream()
                    .filter(x -> !x.getKey().equals("ha-params"))
                    .collect(Collectors.toList());
        }

        return properties;
    }

    private boolean isQuorumQueueProperty(Property prop) {
        if(prop.getKey().equals("x-queue-type") && prop.getValue().equals("quorum"))
            return true;
        else if(prop.getKey().equals("x-quorum-initial-group-size"))
            return true;
        else if(prop.getKey().equals("x-max-in-memory-length"))
            return true;
        else if(prop.getKey().equals("x-max-in-memory-bytes"))
            return true;

        return false;
    }

    private boolean isStreamQueueProperty(Property prop) {
        if(prop.getKey().equals("x-queue-type") && prop.getValue().equals("stream"))
            return true;

        return false;
    }

    private void addPropertyToQueue(Topology topology, Property prop, String pattern, boolean isDownstream) {
        for(VirtualHost vhost : topology.getVirtualHosts()) {
            for(QueueConfig qc : vhost.getQueues()) {
                if(qc.isDownstream() == isDownstream) {
                    if (pattern.equals("") || qc.getGroup().matches(pattern))
                        qc.getProperties().add(prop);
                }
            }
        }
    }

    private JSONObject loadJson(String path) {
        try {

            File f = new File(path);
            if (f.exists()) {
                try(InputStream is = new FileInputStream(path)) {
                    String jsonTxt = IOUtils.toString(is, "UTF-8");
                    return new JSONObject(jsonTxt);
                }
            }
            else {
                throw new TopologyException("Could not find the requested json file: " + path);
            }
        }
        catch(Exception e) {
            throw new TopologyException("Failed loading the requested json file", e);
        }
    }

    private List<VirtualHost> loadAllVirtualHosts(JSONArray vhostsArray,
                                                  BenchmarkType benchmarkType,
                                                  StepOverride stepOverride) {
        List<VirtualHost> virtualHosts = new ArrayList<>();

        for(int i=0; i<vhostsArray.length(); i++) {
            List<VirtualHost> vhosts = loadVirtualHost(vhostsArray.getJSONObject(i), benchmarkType, stepOverride);
            virtualHosts.addAll(vhosts);
        }

        return virtualHosts;
    }

    private List<VirtualHost> loadVirtualHost(JSONObject vhostJson,
                                              BenchmarkType benchmarkType,
                                              StepOverride stepOverride) {
        List<VirtualHost> virtualHosts = new ArrayList<>();
        int scale = getOptionalIntValue(vhostJson, "scale", 1);
        ScaleType scaleType = getScaleType(vhostJson);

        if(scaleType == ScaleType.MultipleVhost) {
            for (int i = 1; i <= scale; i++) {
                String vhostName = "";
                if (scale > 1)
                    vhostName = getMandatoryStrValue(vhostJson, "name") + StringUtils.leftPad(String.valueOf(i), 5, "0");
                else
                    vhostName = getMandatoryStrValue(vhostJson, "name");

                if (vhostName.contains("_"))
                    throw new TopologyException("Group names cannot contain an underscore");

                boolean isDownstream = getOptionalStrValue(vhostJson, "federation", "upstream")
                        .toLowerCase().equals("downstream");

                VirtualHost vhost = new VirtualHost();
                vhost.setName(vhostName);
                vhost.setDownstream(isDownstream);

                String info = isDownstream ? " on downstream" : "";

                if (vhostJson.has("exchanges"))
                    vhost.setExchanges(loadExchanges(vhostName, vhostJson.getJSONArray("exchanges"), isDownstream));
                else
                    logger.info("No exchanges defined for group " + vhostName + info);

                if (vhostJson.has("queues"))
                    vhost.setQueues(loadQueueGroupConfigs(vhostName, vhostJson.getJSONArray("queues"), isDownstream));
                else
                    logger.info("No queue defined for group " + vhostName + info);

                if (vhostJson.has("publishers"))
                    vhost.setPublishers(loadPublisherGroupConfigs(vhostName, vhostJson.getJSONArray("publishers"),
                            vhost.getQueues(),
                            benchmarkType,
                            stepOverride,
                            isDownstream));
                else
                    logger.info("No publishers defined for group " + vhostName + info);

                if (vhostJson.has("consumers"))
                    vhost.setConsumers(loadConsumerGroupConfigs(vhostName, vhostJson.getJSONArray("consumers"), vhost.getQueues(), isDownstream));
                else
                    logger.info("No consumers defined for group " + vhostName + info);

                virtualHosts.add(vhost);
            }
        }
        else {
            String vhostName = getMandatoryStrValue(vhostJson, "name");
            if (vhostName.contains("_"))
                throw new TopologyException("Virtual host names cannot contain an underscore");

            boolean isDownstream = getOptionalStrValue(vhostJson, "federation", "upstream")
                    .toLowerCase().equals("downstream");

            String info = isDownstream ? " on downstream" : "";
            List<ExchangeConfig> exchangeConfigs = new ArrayList<>();
            if (vhostJson.has("exchanges"))
                exchangeConfigs = loadExchanges(vhostName, vhostJson.getJSONArray("exchanges"), isDownstream);
            else
                logger.info("No exchanges defined for group " + vhostName + info);

            List<QueueConfig> queueConfigs = new ArrayList<>();
            if (vhostJson.has("queues"))
                queueConfigs = loadQueueGroupConfigs(vhostName, vhostJson.getJSONArray("queues"), isDownstream);
            else
                logger.info("No queues defined for group " + vhostName + info);

            List<PublisherConfig> publisherConfigs = new ArrayList<>();
            if (vhostJson.has("publishers"))
                publisherConfigs = loadPublisherGroupConfigs(vhostName, vhostJson.getJSONArray("publishers"),
                        queueConfigs,
                        benchmarkType,
                        stepOverride,
                        isDownstream);
            else
                logger.info("No publishers defined for group " + vhostName + info);

            List<ConsumerConfig> consumerConfigs = new ArrayList<>();
            if (vhostJson.has("consumers"))
                consumerConfigs = loadConsumerGroupConfigs(vhostName, vhostJson.getJSONArray("consumers"), queueConfigs, isDownstream);
            else
                logger.info("No consumers defined group " + vhostName + info);

            List<PublisherConfig> finalPublisherConfigs = new ArrayList<>();
            List<ExchangeConfig> finalExchangeConfigs = new ArrayList<>();
            List<QueueConfig> finalQueueConfigs = new ArrayList<>();
            List<ConsumerConfig> finalConsumerConfigs = new ArrayList<>();

            for (int scaleNumber = 1; scaleNumber <= scale; scaleNumber++) {
                for(ExchangeConfig ec : exchangeConfigs) {
                    finalExchangeConfigs.add(ec.clone(scaleNumber));
                }

                for(PublisherConfig pc : publisherConfigs) {
                    finalPublisherConfigs.add(pc.clone(scaleNumber));
                }

                for(QueueConfig qc : queueConfigs) {
                    finalQueueConfigs.add(qc.clone(scaleNumber));
                }

                for(ConsumerConfig cc : consumerConfigs) {
                    finalConsumerConfigs.add(cc.clone(scaleNumber));
                }
            }

            VirtualHost vhost = new VirtualHost();
            vhost.setName(vhostName);
            vhost.setDownstream(isDownstream);
            vhost.setExchanges(finalExchangeConfigs);
            vhost.setPublishers(finalPublisherConfigs);
            vhost.setQueues(finalQueueConfigs);
            vhost.setConsumers(finalConsumerConfigs);

            virtualHosts.add(vhost);
        }

        return virtualHosts;
    }

    public List<PublisherConfig> loadPublisherGroupConfigs(String vhostName,
                                                           JSONArray pgJsonArr,
                                                           List<QueueConfig> queueConfigs,
                                                           BenchmarkType benchmarkType,
                                                           StepOverride stepOverride,
                                                           boolean isDownstream) {
        List<PublisherConfig> pgConfigs = new ArrayList<>();

        for (int i = 0; i < pgJsonArr.length(); i++) {
            JSONObject pgJson = pgJsonArr.getJSONObject(i);
            int scale = getMandatoryIntValue(pgJson, "scale");

            if(scale > 0) {
                PublisherConfig pgConfig = new PublisherConfig();
                pgConfig.setGroupPrefix(getMandatoryStrValue(pgJson, "prefix"));
                pgConfig.setGroup(getMandatoryStrValue(pgJson, "prefix"));
                pgConfig.setVhostName(vhostName);
                pgConfig.setDownstream(isDownstream);
                pgConfig.setScale(scale);
                pgConfig.setDeliveryMode(getDeliveryMode(getOptionalStrValue(pgJson, "deliveryMode", "persistent")));
                pgConfig.setHeadersPerMessage(getOptionalIntValue(pgJson, "headersPerMessage", 0));
                pgConfig.setFrameMax(getOptionalIntValue(pgJson, "frameMax", 0));
                pgConfig.setMessageLimit(stepOverride.getMessageLimit());
                pgConfig.setInitialPublish((getOptionalIntValue(pgJson, "initialPublish", 0)));

                if (pgJson.has("availableHeaders")) {
                    JSONArray headersArr = pgJson.getJSONArray("availableHeaders");
                    pgConfig.setAvailableHeaders(getMessageHeaders(headersArr));
                }

                if (pgConfig.getHeadersPerMessage() > pgConfig.getAvailableHeaders().size())
                    throw new TopologyException("headersPerMessage value higher than number of availableHeaders");
                else if (pgConfig.getHeadersPerMessage() > 0 && pgConfig.getAvailableHeaders().isEmpty())
                    throw new TopologyException("headersPerMessage greater than 0 but no availableHeaders have been specified");

                pgConfig.setStreams(getOptionalIntValue(pgJson, "streams", 1));

                if (stepOverride.hasMessageSize())
                    pgConfig.setMessageSize(stepOverride.getMessageSize());
                else
                    pgConfig.setMessageSize(getOptionalIntValue(pgJson, "messageSize", 16));

                if (stepOverride.hasMsgsPerSecondPerPublisher())
                    pgConfig.setPublishRatePerSecond(stepOverride.getMsgsPerSecondPerPublisher());
                else
                    pgConfig.setPublishRatePerSecond(getOptionalIntValue(pgJson, "msgsPerSecondPerPublisher", 0));

                if (benchmarkType == BenchmarkType.Latency && pgConfig.getPublishRatePerSecond() == 0)
                    throw new TopologyException("You must set a msgsPerSecondPerPublisher value when defining a latency based test");

                if (pgJson.has("sendToQueuePrefix")) {
                    JSONObject scgJson = pgJson.getJSONObject("sendToQueuePrefix");
                    SendToQueueGroup scg = SendToQueueGroup.withGroup(
                            getMandatoryStrValue(scgJson, "queuePrefix"),
                            getQueueGroupMode(getMandatoryStrValue(scgJson, "mode")),
                            queueConfigs);
                    pgConfig.setSendToQueueGroup(scg);
                } else if (pgJson.has("sendToExchange")) {
                    JSONObject steJson = pgJson.getJSONObject("sendToExchange");
                    RoutingKeyMode rkm = getRoutingKeyMode(getMandatoryStrValue(steJson, "routingKeyMode"));
                    String exchange = getMandatoryStrValue(steJson, "exchange");

                    switch (rkm) {
                        case FixedValue:
                            pgConfig.setSendToExchange(SendToExchange.withRoutingKey(exchange, getMandatoryStrValue(steJson, "routingKey")));
                            break;
                        case MultiValue:
                            pgConfig.setSendToExchange(SendToExchange.withRoutingKeys(exchange, getMandatoryStrArray(steJson, "routingKeys")));
                            break;
                        case None:
                            pgConfig.setSendToExchange(SendToExchange.withNoRoutingKey(exchange));
                            break;
                        case StreamKey:
                            pgConfig.setSendToExchange(SendToExchange.withStreamRoutingKey(exchange));
                            break;
                        case Random:
                            pgConfig.setSendToExchange(SendToExchange.withRandomRoutingKey(exchange));
                            break;
                        case RoutingKeyIndex:
                            pgConfig.setSendToExchange(SendToExchange.withRoutingKeyIndex(exchange, getMandatoryStrArray(steJson, "routingKeys")));
                            break;
                        default:
                            throw new TopologyException("RoutingKeyMode " + rkm + " not currently supported");
                    }

                }

                PublisherMode pm = new PublisherMode();
                if (pgJson.has("publishMode")) {
                    JSONObject pmJson = pgJson.getJSONObject("publishMode");
                    pm.setUseConfirms(getMandatoryBoolValue(pmJson, "useConfirms"));
                    pm.setProtocol(getProtocol(getOptionalStrValue(pmJson, "protocol", "amqp091")));
                    pm.setMaxBatchSize(getOptionalIntValue(pmJson, "maxBatchSize", 1));
                    pm.setMaxBatchSizeBytes(getOptionalIntValue(pmJson, "maxBatchSizeBytes", 1000));
                    pm.setMaxBatchWaitMs(getOptionalIntValue(pmJson, "maxBatchWaitMs", 1000));

                    if (pm.isUseConfirms())
                        pm.setInFlightLimit(getMandatoryIntValue(pmJson, "inFlightLimit"));
                }
                pgConfig.setPublisherMode(pm);

                pgConfigs.add(pgConfig);
            }
        }

        return pgConfigs;
    }

    private List<MessageHeader> getMessageHeaders(JSONArray headersArr) {
        List<MessageHeader> headers = new ArrayList<>();
        for(int h=0; h<headersArr.length(); h++) {
            JSONObject hJson = headersArr.getJSONObject(h);
            String type = getMandatoryStrValue(hJson, "type").toLowerCase();
            if(type.equals("string")) {
                headers.add(new MessageHeader(getMandatoryStrValue(hJson, "key"),
                        getMandatoryStrValue(hJson, "value")));
            }
            else if(type.equals("int")) {
                headers.add(new MessageHeader(getMandatoryStrValue(hJson, "key"),
                        getMandatoryIntValue(hJson, "value")));
            }
            else
                throw new TopologyException("Only string and int are supported header types");
        }

        return headers;
    }

    public List<ConsumerConfig> loadConsumerGroupConfigs(String vhostName,
                                                         JSONArray cgJsonArr,
                                                         List<QueueConfig> queueConfigs,
                                                         boolean isDownstream) {
        List<ConsumerConfig> cgConfigs = new ArrayList<>();

        for (int i = 0; i < cgJsonArr.length(); i++) {
            JSONObject cgJson = cgJsonArr.getJSONObject(i);
            int scale = getMandatoryIntValue(cgJson, "scale");
            if(scale > 0) {
                ConsumerConfig cgConfig = new ConsumerConfig();
                cgConfig.setGroup(getMandatoryStrValue(cgJson, "prefix"));
                cgConfig.setVhostName(vhostName);
                cgConfig.setQueueGroup(getMandatoryStrValue(cgJson, "queuePrefix"), queueConfigs);
                cgConfig.setScale(scale);
                cgConfig.setProtocol(getProtocol(getOptionalStrValue(cgJson,"protocol", "amqp091")));
                cgConfig.setFrameMax(getOptionalIntValue(cgJson, "frameMax", 0));
                cgConfig.setProcessingMs(getOptionalIntValue(cgJson, "processingMs", 0));
                cgConfig.setDownstream(isDownstream);

                if (cgJson.has("ackMode")) {
                    JSONObject ackModeJson = cgJson.getJSONObject("ackMode");
                    boolean manualAcks = getMandatoryBoolValue(ackModeJson, "manualAcks");
                    if (manualAcks) {
                        cgConfig.setAckMode(AckMode.withManualAcks(
                                (short)getMandatoryIntValue(ackModeJson, "consumerPrefetch"),
                                getMandatoryIntValue(ackModeJson, "ackInterval"),
                                getOptionalIntValue(ackModeJson, "ackIntervalMs", 1000)
                        ));
                    } else {
                        cgConfig.setAckMode(AckMode.withNoAck());
                    }
                } else {
                    cgConfig.setAckMode(AckMode.withNoAck());
                }

                cgConfigs.add(cgConfig);
            }
        }

        return cgConfigs;
    }

    public List<QueueConfig> loadQueueGroupConfigs(String vhostName, JSONArray qgJsonArr, boolean isDownstream) {
        List<QueueConfig> queueConfigs = new ArrayList<>();

        for (int i = 0; i < qgJsonArr.length(); i++) {
            JSONObject qJson = qgJsonArr.getJSONObject(i);
            int scale = getMandatoryIntValue(qJson, "scale");
            if(scale > 0) {
                QueueConfig qConfig = new QueueConfig();
                qConfig.setGroup(getMandatoryStrValue(qJson, "prefix"));
                qConfig.setVhostName(vhostName);
                qConfig.setDownstream(isDownstream);
                qConfig.setScale(scale);
                qConfig.setQueueType(getQueueType(qJson));
                qConfig.setRetentionSize(ByteCapacity.MB(getOptionalLongValue(qJson, "retentionSizeMb", 20000)));
                qConfig.setSegmentSize(ByteCapacity.MB(getOptionalLongValue(qJson, "segmentSizeMb", 500)));

                if (qJson.has("properties"))
                    qConfig.setProperties(getProperties(qJson.getJSONArray("properties")));

                if (qJson.has("bindings"))
                    qConfig.setBindings(getBindings(qJson.getJSONArray("bindings"), qConfig.getGroup()));

                if(qJson.has("shovel")) {
                    qConfig.setShovelConfig(loadShovelConfig(qJson));
                }

                qConfig.setActionList(getQueueActionList(qJson, qConfig.getInitialQueues()));

                queueConfigs.add(qConfig);
            }
        }

        return queueConfigs;
    }

    private ActionListConfig getQueueActionList(JSONObject qJson, List<String> queueNames) {
        QueueActionListConfig actionListConfig = new QueueActionListConfig(queueNames);
        if(qJson.has("actions")) {
            JSONObject configJson = qJson.getJSONObject("actions");
            actionListConfig.setActionListExecution(getActionListExecuteCount(configJson));
            actionListConfig.setExecuteMode(getActionListExecuteMode(configJson));
            actionListConfig.setActionListDelay(getActionDelay(configJson));

            JSONArray actionsArr = configJson.getJSONArray("orderedActions");
            for (int i = 0; i < actionsArr.length(); i++) {
                JSONObject actionJson = actionsArr.getJSONObject(i);
                String actionType = actionJson.getString("type").toLowerCase();
                ActionDelay actionDelay = getActionDelay(actionJson);
                ActionConfig actionConfig = null;
                switch (actionType) {
                    case "fill":
                        actionConfig = new QueueFillActionConfig(actionDelay,
                                getMandatoryIntValue(actionJson, "messageSize"),
                                getMandatoryIntValue(actionJson, "messageCount"),
                                getOptionalIntValue(actionJson, "publishRate", 0));
                        break;
                    case "drain":
                        actionConfig = new QueueDrainActionConfig(actionDelay,
                                getMandatoryIntValue(actionJson, "thresholdSeconds"));
                        break;
                    case "purge":
                        actionConfig = new QueuePurgeActionConfig(actionDelay);
                        break;
                    default:
                        throw new TopologyException("Invalid queue action type. Allowed: fill and drain");
                }

                actionListConfig.getActions().add(actionConfig);
            }
        }

        return actionListConfig;
    }

    private ActionDelay getActionDelay(JSONObject json) {
        ActionDelay actionDelay = new ActionDelay();

        String startDelayValue = getOptionalStrValue(json, "startDelaySeconds", "");
        if(startDelayValue.isEmpty()) {
            actionDelay.setActionStartDelayType(ActionStartDelayType.None);
        }
        else if(startDelayValue.contains("-")) {
            actionDelay.setActionStartDelayType(ActionStartDelayType.Random);
            String[] parts = startDelayValue.split("-");
            actionDelay.setActionLowerStartValue(Integer.valueOf(parts[0]));
            actionDelay.setActionUpperStartValue(Integer.valueOf(parts[1]));
        }
        else {
            actionDelay.setActionStartDelayType(ActionStartDelayType.Fixed);
            actionDelay.setActionUpperStartValue(Integer.valueOf(startDelayValue));
        }

        return actionDelay;
    }

    private ScaleType getScaleType(JSONObject configJson) {
        if(!configJson.has("scaleType"))
            return ScaleType.MultipleVhost;

        String value = configJson.getString("scaleType").toLowerCase();
        switch (value) {
            case "single-vhost": return ScaleType.SingleVhost;
            case "multiple-vhost": return ScaleType.MultipleVhost;
            default: throw new TopologyException("Invalid value for 'scaleType' field. Allowed values: single-vhost, multiple-vhost");
        }
    }

    private ActionListExecution getActionListExecuteCount(JSONObject configJson) {
        String value = configJson.getString("executeCount").toLowerCase();
        switch (value) {
            case "execute-once": return ActionListExecution.ExecuteOnce;
            case "repeat-forever": return ActionListExecution.RepeatForever;
            default: throw new TopologyException("Invalid value for 'executeCount' field. Allowed values: execute-once, repeat-forever");
        }
    }

    private ExecuteMode getActionListExecuteMode(JSONObject configJson) {
        String value = configJson.getString("executeMode").toLowerCase();
        switch (value) {
            case "synchronized": return ExecuteMode.Synchronized;
            case "all-at-once": return ExecuteMode.IndependentAllAtOnce;
            case "staggered": return ExecuteMode.IndependentStaggered;
            default: throw new TopologyException("Invalid value for 'executeMode' field. Allowed values: synchronized, all-at-once, staggered");
        }
    }

    private QueueType getQueueType(JSONObject configJson) {
        String value = getOptionalStrValue(configJson, "queueType", "standard");
        switch (value) {
            case "standard": return QueueType.Standard;
            case "stream": return QueueType.Stream;
            default: throw new TopologyException("Invalid value for 'queueType' field. Allowed values: standard, stream");
        }
    }

    public List<ExchangeConfig> loadExchanges(String vhostName, JSONArray exJsonArr, boolean isDownstream) {
        List<ExchangeConfig> exchangeConfigs = new ArrayList<>();

        for(int i=0; i<exJsonArr.length(); i++) {
            ExchangeConfig exConfig = new ExchangeConfig();
            JSONObject exJson = exJsonArr.getJSONObject(i);

            exConfig.setName(getMandatoryStrValue(exJson, "name"));
            exConfig.setVhostName(vhostName);
            exConfig.setDownstream(isDownstream);
            exConfig.setExchangeType(getExchangeType(getMandatoryStrValue(exJson, "type")));

            if(exJson.has("bindings"))
                exConfig.setBindings(getBindings(exJson.getJSONArray("bindings"), exConfig.getName()));

            if(exJson.has("shovel")) {
                exConfig.setShovelConfig(loadShovelConfig(exJson));
            }

            exchangeConfigs.add(exConfig);
        }

        return exchangeConfigs;
    }

    private ShovelConfig loadShovelConfig(JSONObject json) {
        JSONObject sJson = json.getJSONObject("shovel");
        ShovelConfig shovel = new ShovelConfig();

        shovel.setSrcIsDownstream(getMandatoryStrValue(sJson, "srcBroker").toLowerCase().equals("downstream"));

        if(getMandatoryStrValue(sJson, "srcType").toLowerCase().equals("queue"))
            shovel.setSrcTargetType(ShovelTarget.Queue);
        else
            shovel.setSrcTargetType(ShovelTarget.Exchange);

        shovel.setSrcTargetName(getMandatoryStrValue(sJson, "srcName"));
        shovel.setAckMode(getMandatoryStrValue(sJson,"ackMode"));
        shovel.setPrefetch(getMandatoryIntValue(sJson, "prefetch"));
        shovel.setReconnectDelaySeconds(getOptionalIntValue(sJson, "reconnectDelaySeconds", 5));

        return shovel;
    }

    private FixedConfig getFixedConfig(JSONObject fixedJson, StepOverride stepOverride) {
        FixedConfig fixedConfig = new FixedConfig();
        fixedConfig.setStepOverride(stepOverride);

        fixedConfig.setDurationSeconds(getMandatoryIntValue(fixedJson, "durationSeconds"));
        fixedConfig.setStepRampUpSeconds(getMandatoryIntValue(fixedJson, "rampUpSeconds"));

        return fixedConfig;
    }

    private VariableConfig getSingleVariableConfig(JSONObject varJson, StepOverride stepOverride) {
        VariableConfig variableConfig = new VariableConfig();
        variableConfig.setStepOverride(stepOverride);
        variableConfig.setDimension(getVariableDimension(getMandatoryStrValue(varJson, "dimension")));
        variableConfig.setValues(getMandatoryDoubleArray(varJson, "values"));
        variableConfig.setStepDurationSeconds(getMandatoryIntValue(varJson, "stepDurationSeconds"));
        variableConfig.setStepRampUpSeconds(getMandatoryIntValue(varJson, "rampUpSeconds"));
        variableConfig.setValueType(getValueType(getOptionalStrValue(varJson, "valueType", "Value")));
        variableConfig.setRepeatWholeSeriesCount(getOptionalIntValue(varJson, "repeatSeries", 1));

        if(varJson.has("applyToPrefix"))
            variableConfig.setGroup(getMandatoryStrValue(varJson, "applyToPrefix"));

        return variableConfig;
    }

    private VariableConfig getMultiVariableConfig(JSONObject varJson, StepOverride stepOverride) {
        VariableConfig variableConfig = new VariableConfig();
        variableConfig.setStepOverride(stepOverride);

        JSONArray vdArr = varJson.getJSONArray("dimensions");
        VariableDimension[] dimensions = new VariableDimension[vdArr.length()];
        for(int i=0; i<dimensions.length; i++)
            dimensions[i] = getVariableDimension(vdArr.getString(i));

        variableConfig.setMultiDimensions(dimensions);

        JSONArray mvArr = varJson.getJSONArray("multiValues");
        List<Double[]> multiValues = new ArrayList<>();
        for(int i=0; i<mvArr.length(); i++) {
            Double[] values = new Double[dimensions.length];
            JSONArray rowArr = mvArr.getJSONArray(i);
            for(int j=0; j<dimensions.length; j++) {
                values[j] = rowArr.getDouble(j);
            }
            multiValues.add(values);
        }

        variableConfig.setMultiValues(multiValues);
        variableConfig.setRepeatWholeSeriesCount(getOptionalIntValue(varJson, "repeatSeries", 1));
        variableConfig.setStepDurationSeconds(getMandatoryIntValue(varJson, "stepDurationSeconds"));
        variableConfig.setStepRampUpSeconds(getMandatoryIntValue(varJson, "rampUpSeconds"));

        if(varJson.has("applyToPrefix"))
            logger.warn("applyToPrefix ignored in multi-dimensional topologies");

        return variableConfig;
    }

    private String getMandatoryStrValue(JSONObject json, String jsonPath) {
        if(json.has(jsonPath)) {
            return getStrValue(json, jsonPath);
        }

        throw new TopologyException("Missing required field: " + jsonPath);
    }

    private String getStrValue(JSONObject json, String jsonPath) {
        return json.getString(jsonPath);
    }

    private String getOptionalStrValue(JSONObject json, String jsonPath, String defaultValue) {
        if(json.has(jsonPath))
            return getStrValue(json, jsonPath);
        else
            return defaultValue;
    }

    private int getMandatoryIntValue(JSONObject json, String jsonPath) {
        if(json.has(jsonPath)) {
            return json.getInt(jsonPath);
        }

        throw new TopologyException("Missing required field: " + jsonPath);
    }

    private int getOptionalIntValue(JSONObject json, String jsonPath, int defaultValue) {
        if(json.has(jsonPath)) {
            return json.getInt(jsonPath);
        }
        else
            return defaultValue;
    }

    private long getOptionalLongValue(JSONObject json, String jsonPath, int defaultValue) {
        if(json.has(jsonPath)) {
            return json.getInt(jsonPath);
        }
        else
            return defaultValue;
    }

    private boolean getMandatoryBoolValue(JSONObject json, String jsonPath) {
        if(json.has(jsonPath)) {
            return json.getBoolean(jsonPath);
        }

        throw new TopologyException("Missing required field: " + jsonPath);
    }

    private boolean getOptionalBoolValue(JSONObject json, String jsonPath, boolean defaultValue) {
        if(json.has(jsonPath)) {
            return json.getBoolean(jsonPath);
        }
        else
            return defaultValue;
    }

    private List<Double> getMandatoryDoubleArray(JSONObject json, String jsonPath) {
        if(json.has(jsonPath)) {
            List<Double> intList = new ArrayList<>();
            JSONArray arr = json.getJSONArray(jsonPath);
            for(int i=0; i<arr.length(); i++)
                intList.add(arr.getDouble(i));

            return intList;
        }

        throw new TopologyException("Missing required field: " + jsonPath);
    }

    private String[] getMandatoryStrArray(JSONObject json, String jsonPath) {
        if(json.has(jsonPath)) {
            JSONArray arr = json.getJSONArray(jsonPath);
            String[] strList = new String[arr.length()];
            for(int i=0; i<arr.length(); i++)
                strList[i] = arr.getString(i);

            return strList;
        }

        throw new TopologyException("Missing required field: " + jsonPath);
    }

    private String[] getOptionalStrArray(JSONObject json, String jsonPath, String[] defaultValue) {
        if(json.has(jsonPath)) {
            JSONArray arr = json.getJSONArray(jsonPath);
            String[] strList = new String[arr.length()];
            for(int i=0; i<arr.length(); i++)
                strList[i] = arr.getString(i);

            return strList;
        }
        else {
            return defaultValue;
        }
    }

    private List<BindingConfig> getBindings(JSONArray bJsonArr, String owner) {
        List<BindingConfig> bConfigs = new ArrayList<>();

        for(int b=0; b<bJsonArr.length(); b++) {
            JSONObject bJson = bJsonArr.getJSONObject(b);

            BindingConfig bConfig = new BindingConfig();
            bConfig.setFrom(getMandatoryStrValue(bJson, "from"));

            List<String> bkList = new ArrayList<>();
            String[] bindingKeys = getOptionalStrArray(bJson, "bindingKeys", new String[0]);
            for(String bindingKey : bindingKeys) {
                if (bindingKey != null && bindingKey.equals("self"))
                    bkList.add(owner);
                else
                    bkList.add(bindingKey);
            }

            bConfig.setBindingKeys(bkList);

            bConfig.setBindingKeysPerQueue(getOptionalIntValue(bJson, "bindingKeysPerQueue", 1));

            if(bJson.has("properties")) {
                JSONArray propJsonArr = bJson.getJSONArray("properties");
                bConfig.setProperties(getProperties(propJsonArr));
            }

            bConfigs.add(bConfig);
        }

        return bConfigs;
    }

    private List<Property> getProperties(JSONArray propJsonArr) {
        List<Property> properties = new ArrayList<>();

        for(int p=0; p<propJsonArr.length(); p++) {
            JSONObject propJson = propJsonArr.getJSONObject(p);

            Property prop = null;
            String type = getOptionalStrValue(propJson, "type", "string").toLowerCase();
            switch (type) {
                case "string":
                    prop = new Property(
                            getMandatoryStrValue(propJson, "key"),
                            getMandatoryStrValue(propJson, "value"));
                    break;
                case "int":
                    prop = new Property(
                            getMandatoryStrValue(propJson, "key"),
                            getMandatoryIntValue(propJson, "value"));
                    break;
                default:
                    throw new TopologyException("Only string and int values are currently supported for properties");
            }

            properties.add(prop);
        }

        return properties;
    }

    private Protocol getProtocol(String value) {
        switch(value.toLowerCase()) {
            case "amqp091": return Protocol.AMQP091;
            case "stream": return Protocol.STREAM;
            default:
                throw new TopologyException("Only 'amqp091' and 'stream' are valid values for 'protocol'");
        }
    }

    private BenchmarkType getBenchmarkType(String value) {
        switch(value.toLowerCase()) {
            case "throughput": return BenchmarkType.Throughput;
            case "latency": return BenchmarkType.Latency;
            case "stress": return BenchmarkType.Stress;
            default:
                throw new TopologyException("Only 'Throughput', 'Latency' and 'Stress' are valid values for benchmarkType");
        }
    }

    private DeliveryMode getDeliveryMode(String value) {
        switch(value.toLowerCase()) {
            case "persistent": return DeliveryMode.Persistent;
            case "transient": return DeliveryMode.Transient;
            default:
                throw new TopologyException("Only 'Persistent' and 'Transient' are valid values for deliveryMode");
        }
    }

    private QueueGroupMode getQueueGroupMode(String value) {
        switch(value.toLowerCase()) {
            case "random": return QueueGroupMode.Random;
            case "counterpart": return QueueGroupMode.Counterpart;
            default:
                throw new TopologyException("Only 'random' and 'counterpart' are valid values for 'sendToQueuePrefix.mode'");
        }
    }

    private RoutingKeyMode getRoutingKeyMode(String value) {
        switch(value.toLowerCase()) {
            case "none": return RoutingKeyMode.None;
            case "random": return RoutingKeyMode.Random;
            case "streamkey": return RoutingKeyMode.StreamKey;
            case "fixedvalue": return RoutingKeyMode.FixedValue;
            case "multivalue": return RoutingKeyMode.MultiValue;
            case "index": return RoutingKeyMode.RoutingKeyIndex;
            default:
                throw new TopologyException("Only 'None', 'Random', 'StreamKey', 'FixedValue', 'MultiValue' and 'Index' are valid values for 'sendToExchange.routingKeyMode'");
        }
    }

    private ExchangeType getExchangeType(String value) {
        switch(value.toLowerCase()) {
            case "fanout": return ExchangeType.Fanout;
            case "direct": return ExchangeType.Direct;
            case "topic": return ExchangeType.Topic;
            case "header": return ExchangeType.Headers;
            case "headers": return ExchangeType.Headers;
            case "consistenthash": return ExchangeType.ConsistentHash;
            case "modulushash": return ExchangeType.ModulusHash;
            default:
                throw new TopologyException("Only 'fanout', 'direct', 'topic', 'headers', 'consistenthash' and 'modulushash' are valid values for 'exchanges.type'");
        }
    }

    private TopologyType getTopologyType(String value) {
        switch(value.toLowerCase()) {
            case "fixed":
            case "fixeddimension":
            case "fixeddimensions":
                return TopologyType.Fixed;
            case "singledimension": return TopologyType.SingleVariable;
            case "multipledimension":
            case "multipledimensions":
                return TopologyType.MultiVariable;
            default:
                throw new TopologyException("Only 'Fixed', 'SingleDimension' and 'MultipleDimensions' are valid values for 'topologyType'");
        }
    }

    private VariableDimension getVariableDimension(String value) {
        switch(value.toLowerCase()) {
            case "publishers": return VariableDimension.Publishers;
            case "consumers": return VariableDimension.Consumers;
            case "queues": return VariableDimension.Queues;
            case "prefetch": return VariableDimension.ConsumerPrefetch;
            case "ackinterval": return VariableDimension.ConsumerAckInterval;
            case "ackintervalms": return VariableDimension.ConsumerAckIntervalMs;
            case "headerspermessage": return VariableDimension.MessageHeaders;
            case "messagesize": return VariableDimension.MessageSize;
            case "publishrate": return VariableDimension.PublishRatePerPublisher;
            case "inflightlimit": return VariableDimension.PublisherInFlightLimit;
            case "routingkeyindex": return VariableDimension.RoutingKeyIndex;
            case "processingms": return VariableDimension.ProcessingMs;
            default:
                throw new TopologyException("Invalid value: " + value + ". Only 'Publishers', 'Consumers', 'Queues', 'Prefetch', 'AckInterval', 'HeadersPerMessage', 'Messagesize', 'PublisherRate', 'ProcessingMs' and 'InFlightLimit' are valid values for 'dimension'");
        }
    }

    private ValueType getValueType(String value) {
        switch(value.toLowerCase()) {
            case "value": return ValueType.Value;
            case "multiply": return ValueType.Multiply;
            default:
                throw new TopologyException("Only 'Value' and 'Multiply' are valid values for valueType");
        }
    }

    private FederationUpstream getFederationUpstream(JSONObject policiesJson) {
        if(policiesJson.has("federation")) {
            JSONObject fedJson = policiesJson.getJSONObject("federation");
            FederationUpstream upstream = new FederationUpstream(
                    getOptionalIntValue(fedJson, "fed-prefetch-count", 10000),
                    getOptionalIntValue(fedJson, "fed-reconnect-delay-seconds", 5),
                    getOptionalStrValue(fedJson, "fed-ack-mode", "on-confirm")
            );

            return upstream;
        }

        return null;
    }

    private List<Policy> getPolicies(JSONObject policiesJson) {
        JSONArray policiesArray = policiesJson.getJSONArray("policies");
        List<Policy> policies = new ArrayList<>();

        for(int i=0; i<policiesArray.length(); i++) {
            policies.add(getPolicy(policiesArray.getJSONObject(i)));
        }

        return policies;
    }

    private Policy getPolicy(JSONObject policyJson) {
        List<Property> properties = getProperties(policyJson.getJSONArray("properties"));
        boolean isDownstream = getOptionalStrValue(policyJson, "federation", "upstream")
                .toLowerCase()
                .equals("downstream");

        return new Policy(
                getMandatoryStrValue(policyJson, "name"),
                getMandatoryStrValue(policyJson, "pattern"),
                getMandatoryStrValue(policyJson, "applyTo"),
                getMandatoryIntValue(policyJson, "priority"),
                properties,
                isDownstream);
    }

    private Map<String, String> getVariableDefaults(JSONObject json) {
        Map<String, String> vd = new HashMap<>();

        if(json.has("variables")) {
            JSONArray variables = json.getJSONArray("variables");
            for(int i=0; i<variables.length(); i++) {
                JSONObject varJson = variables.getJSONObject(i);
                vd.put(varJson.getString("name"), varJson.getString("default"));
            }
        }

        return vd;
    }

    private String getDescription(String suppliedDescription,
                                  Map<String, String> suppliedTopologyVariables,
                                  Map<String, String> defaultsTopologyVariables,
                                  Map<String, String> suppliedPolicyVariables,
                                  Map<String, String> defaultsPolicyVariables) {
        String topologyVars = variablesToString(suppliedTopologyVariables, defaultsTopologyVariables);
        String policyVars = variablesToString(suppliedPolicyVariables, defaultsPolicyVariables);

        String description = "";
        if(suppliedDescription != null && !suppliedDescription.isEmpty())
            description += suppliedDescription;

        if(!topologyVars.isEmpty()) {
            if(description.isEmpty())
                description = topologyVars;
            else
                description +=  " " + topologyVars;
        }

        if(!policyVars.isEmpty()) {
            if(description.isEmpty())
                description = policyVars;
            else
                description +=  " " + policyVars;
        }

        return description;
    }

    private String variablesToString(Map<String, String> suppliedTopologyVariables, Map<String, String> defaultsTopologyVariables) {
        StringBuilder sb = new StringBuilder();

        for(Map.Entry<String,String> entry : defaultsTopologyVariables.entrySet()) {
            if(suppliedTopologyVariables.containsKey(entry.getKey()))
                sb.append(entry.getKey() + "=" + suppliedTopologyVariables.get(entry.getKey())+",");
            else
                sb.append(entry.getKey() + "=" + entry.getValue()+",");
        }

        return sb.toString();
    }

    private JSONObject makeVariableReplacements(JSONObject json, Map<String, String> variables, Map<String,String> variableDefaults) {
        json.keys().forEachRemaining(key -> {
            handleField(json, key, variables, variableDefaults);
        });

        return json;
    }

    public void handleField(JSONObject json, String key, Map<String, String> variables, Map<String,String> variableDefaults) {
        Object fieldValue = json.get(key);
        if (fieldValue instanceof JSONArray) {
            handleJSONArray((JSONArray) fieldValue, variables, variableDefaults);
        } else if (fieldValue instanceof JSONObject) {
            handleJSONObject((JSONObject) fieldValue, variables, variableDefaults);
        } else {
            replaceValue(json, key, variables, variableDefaults);
        }
    }

    public void handleJSONObject(JSONObject jsonObject, Map<String, String> variables, Map<String,String> variableDefaults) {
        Iterator<String> jsonObjectIterator = jsonObject.keys();
        jsonObjectIterator.forEachRemaining(key -> {
            handleField(jsonObject, key, variables, variableDefaults);
        });
    }

    public void handleJSONArray(JSONArray jsonArray, Map<String, String> variables, Map<String,String> variableDefaults) {
        for(int i=0; i<jsonArray.length(); i++) {
            Object element = jsonArray.get(i);

            if(element instanceof JSONObject)
                handleJSONObject((JSONObject) element, variables, variableDefaults);
            else if(element instanceof JSONArray)
                handleJSONArray((JSONArray) element, variables, variableDefaults);
            else {
                String fieldValue = (String)element;
                String variableValue = getVariableValue(fieldValue, variables, variableDefaults);
                if(StringUtils.isNumeric(variableValue)) {
                    int value = Integer.valueOf(variableValue);
                    jsonArray.put(i, value);
                }
                else {
                    jsonArray.put(i, variableValue);
                }
            }
        }
    }

    public void replaceValue(JSONObject json, String key, Map<String, String> variables, Map<String,String> variableDefaults) {
        if(json.get(key).getClass().equals(Integer.class)) {
            int value = getIntValue(json, key, variables, variableDefaults);
            json.put(key, value);
        }
        else if(json.get(key).getClass().equals(Boolean.class)) {
            boolean value = getBoolValue(json, key, variables, variableDefaults);
            json.put(key, value);
        }
        else {
            String value = getVariableValue(json.getString(key), variables, variableDefaults);
            json.put(key, value);
        }
    }

    private String getVariableValue(String value, Map<String, String> variables, Map<String,String> variableDefaults) {
        Matcher m = variablePattern.matcher(value);
        if(m.matches()) {
            String variableText = m.group(1);
            String variableName = m.group(2);
            String variableValue = "";
            if (variables.containsKey(variableName)) {
                variableValue = variables.get(variableName);
            } else if (variableDefaults.containsKey(variableName)) {
                variableValue = variableDefaults.get(variableName);
            } else
                throw new TopologyException("Variable " + variableName + " was not supplied and has no default value");

            return value.replace(variableText, variableValue);
        }
        else {
            return value;
        }
    }

    private int getIntValue(JSONObject json, String jsonPath, Map<String, String> variables, Map<String,String> variableDefaults) {
        Object jsonObj = json.get(jsonPath);
        if(jsonObj instanceof Integer) {
            return json.getInt(jsonPath);
        }
        else {
            return Integer.valueOf(getVariableValue(json.getString(jsonPath), variables, variableDefaults));
        }
    }

    private boolean getBoolValue(JSONObject json, String jsonPath, Map<String, String> variables, Map<String,String> variableDefaults) {
        Object jsonObj = json.get(jsonPath);
        if(jsonObj instanceof Boolean) {
            return json.getBoolean(jsonPath);
        }
        else {
            return Boolean.valueOf(getVariableValue(json.getString(jsonPath), variables, variableDefaults));
        }
    }
}
