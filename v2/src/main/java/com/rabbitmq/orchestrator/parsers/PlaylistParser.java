package com.rabbitmq.orchestrator.parsers;

import com.rabbitmq.orchestrator.InvalidInputException;
import com.rabbitmq.orchestrator.deploy.BaseSystem;
import com.rabbitmq.orchestrator.deploy.OutputData;
import com.rabbitmq.orchestrator.deploy.k8s.model.K8sEngine;
import com.rabbitmq.orchestrator.meta.EC2Meta;
import com.rabbitmq.orchestrator.meta.K8sMeta;
import com.rabbitmq.orchestrator.model.*;
import com.rabbitmq.orchestrator.model.actions.ActionTrigger;
import com.rabbitmq.orchestrator.model.actions.BrokerAction;
import com.rabbitmq.orchestrator.model.actions.TrafficControl;
import com.rabbitmq.orchestrator.model.clients.Check;
import com.rabbitmq.orchestrator.model.clients.ClientConfiguration;
import com.rabbitmq.orchestrator.model.clients.ClientConnectMode;
import com.rabbitmq.orchestrator.model.clients.LoadGenConfiguration;
import com.rabbitmq.orchestrator.model.hosts.Host;
import com.rabbitmq.orchestrator.run.RabbitMQConfiguration;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class PlaylistParser extends Parser {
    Map<String, Object> playlistRoot;
    Yaml yaml;
    File metaFileDir;
    File configurationRoot;
    ConfigurationParser configParser;

    static String[] PermittedClientConnectModes = {"round-robin", "round-robin", "random", "leader", "non-leader"};
    static String[] PermittedDelayDistributions = {"none", "uniform", "normal", "pareto", "paretonormal"};
    static String[] PermittedLossModes = {"none", "random", "gemodel"};
    static String[] PermittedModes = { "benchmark", "model" };
    static String[] PermittedHosts = {"ec2", "gcp", "k8s"};
    static String[] PermittedK8sEngines = {"eks", "gke", "none"};

    Playlist playlist;

    public PlaylistParser(String metaFileDir, String configurationRoot) {
        super(new Yaml(), configurationRoot);
        this.metaFileDir = new File(metaFileDir);
        if(!this.metaFileDir.exists())
            throw new InvalidInputException("The metadata directory provided does not exist");

        this.configurationRoot = new File(configurationRoot);
        if(!this.configurationRoot.exists())
            throw new InvalidInputException("The configuration directory provided does not exist");

        this.configParser = new ConfigurationParser(super.yaml, configurationRoot);
    }

    public void loadPlaylist(String playlistFile) {
        playlistRoot = loadYamlFile(playlistFile);
        int parallel = getIntValue("run", playlistRoot);
        if(parallel > 5)
            throw new InvalidInputException("Cannot deploy/run more than 5 copies of each system");

        Map<String,Object> commonSource = getWorkloadSource("common-workload", playlistRoot);
        List<BaseSystem> systems = loadSystems(parallel, commonSource);
        List<Benchmark> benchmarks = loadBenchmarks(parallel,
                systems.stream().map(x -> x.getName()).collect(Collectors.toList()),
                commonSource);

        playlist = new Playlist(systems, benchmarks);
    }

    public Playlist getPlaylist() {
        return playlist;
    }

    public EC2Meta loadEc2Meta() {
        Map<String,Object> source = loadYamlFile(metaFileDir.getAbsolutePath() + "/ec2.yml");

        return new EC2Meta(
                getStrValue("keypair", source),
                getStrValue("aws-user", source),
                getStrValue("loadgen-sg", source),
                getStrValue("broker-sg", source),
                getListValue("subnets", source),
                getStrValue("deploy-root-dir", source),
                getStrValue("run-root-dir", source));
    }

    public K8sMeta loadGkeMeta() {
        Map<String,Object> source = loadYamlFile(metaFileDir.getAbsolutePath() + "/gke.yml");

        return new K8sMeta(
                getStrValue("project", source),
                getStrValue("zones", source),
                getStrValue("k8s-version", source),
                getStrValue("manifests-dir", source),
                getStrValue("deploy-root-dir", source),
                getStrValue("run-root-dir", source));
    }

    public K8sMeta loadEksMeta() {
        Map<String,Object> source = loadYamlFile(metaFileDir.getAbsolutePath() + "/eks.yml");

        return new K8sMeta(
                getStrValue("aws-user", source),
                getStrValue("region", source),
                getStrValue("k8s-version", source),
                getStrValue("manifests-dir", source),
                getStrValue("deploy-root-dir", source),
                getStrValue("run-root-dir", source));
    }

    public OutputData loadOutputData() {
        Map<String,Object> source = loadYamlFile(metaFileDir.getAbsolutePath() + "/output-data.yml");

        return new OutputData(
                getStrValue("influx-subpath", source),
                getStrValue("influx-username", source),
                getStrValue("influx-password", source),
                getStrValue("influx-url", source),
                getStrValue("influx-database", source),
                getStrValue("postgres-jdbc-url", source),
                getStrValue("postgres-username", source),
                getStrValue("postgres-password", source),
                getStrValue("postgres-database", source),
                getStrValue("rabbitmq-username", source),
                getStrValue("rabbitmq-password", source),
                getStrValue("logs-storage-dir", source));
    }

    public Map<String, Object> loadYamlFile(String filePath) {
        File pf = new File(filePath);
        if(!pf.exists())
            throw new InvalidInputException("File " + filePath + " provided does not exist");

        try {
            InputStream pfStream = new FileInputStream(pf);
            yaml = new Yaml();
            return yaml.load(pfStream);
        }
        catch(IOException e) {
            throw new InvalidInputException("Bad yaml file. ", e);
        }
    }

    private Map<String,Object> getWorkloadSource(String fieldPath, Map<String,Object> source) {
        Map<String,Object> workloadSource = getSubTree(fieldPath, source);
        if(pathExists("file", workloadSource)) {
            Map<String,Object> fileSource = loadYamlFile(rootDir + "/workloads/" + getStrValue("file", workloadSource));
            return getSubTree("workload", fileSource);
        }
        else {
            return workloadSource;
        }
    }

    private List<Benchmark> loadBenchmarks(int parallel,
                                           List<String> systems,
                                           Map<String,Object> commonSource) {
        Map<String,Object> clientConfigDefaults = loadYamlFile(configurationRoot + "/workloads/defaults/client-config-defaults.yml");
        Map<String,Object> loadgenConfigDefaults = loadYamlFile(configurationRoot + "/workloads/defaults/loadgen-config-defaults.yml");
        Map<String,Object> brokerActionDefaults = loadYamlFile(configurationRoot + "/workloads/defaults/broker-actions-defaults.yml");

        return getBenchmarks(parallel,
                systems,
                commonSource,
                clientConfigDefaults,
                loadgenConfigDefaults,
                brokerActionDefaults);
    }

    private List<Benchmark> getBenchmarks(int parallel,
                                          List<String> systems,
                                          Map<String,Object> commonSource,
                                          Map<String,Object> clientConfigDefaults,
                                          Map<String,Object> loadgenConfigDefaults,
                                          Map<String,Object> brokerActionDefaults) {
        List<Benchmark> benchmarks = new ArrayList<>();

        int ordinal = 1;
        for(Map<String,Object> benchmarkSource : getSubTreeList("benchmarks", playlistRoot)) {
            Map<String,Workload> assignments = new HashMap<>();

            for(Map<String,Object> workloadAssignmentSource : getSubTreeList("benchmark", benchmarkSource)) {
                Map<String,Object> workloadSource = getWorkloadSource("workload", workloadAssignmentSource);
                Workload workload = getWorkload(workloadSource, commonSource, clientConfigDefaults, loadgenConfigDefaults, brokerActionDefaults);
                List<String> assignedSystems = null;
                boolean withSuffix = true;
                if(pathExists("systems", workloadAssignmentSource)) {
                    assignedSystems = getStringList("systems", workloadAssignmentSource);
                    withSuffix = false;
                }
                else
                    assignedSystems = systems;

                for(String system : assignedSystems) {
                    for(int i=0; i<parallel; i++) {
                        String systemName = withSuffix ? system : system + BaseSystem.SystemSuffixes[i];
                        if(systems.contains(systemName))
                            assignments.put(systemName, workload);
                        else
                            throw new InvalidInputException("Bad system-workload assignment. System does not exist: " + system + " ("+ systemName +")");
                    }
                }
            }

            benchmarks.add(new Benchmark(assignments, ordinal));
            ordinal++;
        }

        return benchmarks;
    }

    private Workload getWorkload(Map<String,Object> source,
                                 Map<String,Object> commonSource,
                                 Map<String,Object> clientConfigDefaults,
                                 Map<String,Object> loadgenConfigDefaults,
                                 Map<String,Object> brokerActionDefaults) {
        LoadGenConfiguration loadGenConfiguration;
        if(isModelMode(source, commonSource))
            loadGenConfiguration = getModelLoadGenConfiguration(source, commonSource, getSubTree("model", loadgenConfigDefaults));
        else
            loadGenConfiguration = getBenchmarkLoadGenConfiguration(source, commonSource, getSubTree("benchmark", loadgenConfigDefaults));

        RabbitMQConfiguration rabbitMQConfiguration = null;
        if(pathExists("rabbitmq.config", source))
            rabbitMQConfiguration = configParser.getConfiguration(source, commonSource);

        return new Workload(
                rabbitMQConfiguration,
                getRabbitLoad("main", source, commonSource),
                getRabbitLoad("background", source, commonSource),
                getBrokerAction(source, commonSource, brokerActionDefaults),
                getClientConfig(source, commonSource, clientConfigDefaults),
                loadGenConfiguration);
                //getListValue(source, "override-broker-hosts")
    }

    private RabbitLoad getRabbitLoad(String loadField, Map<String,Object>... sources) {
        String fieldPrefix = loadField + ".";
        if(!pathExists(loadField, sources))
            return null;

        return new RabbitLoad(
                getStrValue(fieldPrefix + "topology.file", sources),
                mergeStringMap(getSubTreesOfEachSource(fieldPrefix + "topology.variables", sources)),
                getOptionalStrValue(fieldPrefix + "policies.file", "", sources),
                mergeStringMap(getSubTreesOfEachSource(fieldPrefix + "policies.variables", sources)),
                getOptionalIntValue(fieldPrefix + "delay-seconds", 0, sources),
                getOptionalIntValue(fieldPrefix + "step-seconds", 0, sources),
                getOptionalIntValue(fieldPrefix + "step-repeat", 0, sources),
                getOptionalIntValue(fieldPrefix + "step-msg-limit", 0, sources));
    }

    private BrokerAction getBrokerAction(Map<String,Object>... sources) {
        if(pathExists("broker-actions.action-type", sources)) {
            TrafficControl tc = null;
            if(pathExists("broker-actions.traffic-control", sources)) {
                tc = getTrafficControl(sources);
            }

            return new BrokerAction(
                    ActionType.valueOf(getStrValue("broker-actions.action-type", sources).toUpperCase().replace("-", "_")),
                    ActionTrigger.valueOf(getStrValue("broker-actions.trigger-type", sources).toUpperCase()),
                    getIntValue("broker-actions.trigger-value", sources),
                    tc
            );
        }

        return null;
    }

    private TrafficControl getTrafficControl(Map<String,Object>... sources) {
        return new TrafficControl(
                getBoolValue("broker-actions.traffic-control.apply-to-client-traffic", sources),
                getBoolValue("broker-actions.traffic-control.apply-to-all-brokers", sources),
                getIntValue("broker-actions.traffic-control.delay-ms", sources),
                getIntValue("broker-actions.traffic-control.delay-jtter-ms", sources),
                getValidatedValue("broker-actions.traffic-control.delay-distribution", PermittedDelayDistributions, sources),
                getIntValue("broker-actions.traffic-control.bandwidth", sources),
                getValidatedValue("broker-actions.traffic-control.packet-loss-mode", PermittedLossModes, sources),
                getStrValue("broker-actions.traffic-control.packet-loss-arg1", sources),
                getStrValue("broker-actions.traffic-control.packet-loss-arg2", sources),
                getStrValue("broker-actions.traffic-control.packet-loss-arg3", sources),
                getStrValue("broker-actions.traffic-control.packet-loss-arg4", sources)
        );
    }

    private ClientConfiguration getClientConfig(Map<String,Object>... sources) {
        return new ClientConfiguration(
                getOptionalBoolValue("client-config.tcp-no-delay", false, sources),
                toClientConnectMode(getOptionalValidatedValue("client-config.publisher-connect-mode", "round-robin", PermittedClientConnectModes, sources)),
                toClientConnectMode(getOptionalValidatedValue("client-config.consumer-connect-mode", "round-robin", PermittedClientConnectModes, sources)),
                getOptionalIntValue("client-config.publisher-heartbeat-seconds", 10, sources),
                getOptionalIntValue("client-config.consumer-heartbeat-seconds", 10, sources)
        );
    }

    private ClientConnectMode toClientConnectMode(String value) {
        switch (value.toLowerCase()) {
            case "round-robin": return ClientConnectMode.RoundRobin;
            case "random": return ClientConnectMode.Random;
            case "leader": return ClientConnectMode.Leader;
            case "non-leader": return ClientConnectMode.NonLeader;
            default: throw new InvalidInputException(value + " is not a valid connect mode");
        }
    }

    private boolean isModelMode(Map<String,Object>... sources) {
        return getValidatedValue("loadgen-config.mode", PermittedModes, sources).equals("model");
    }

    private LoadGenConfiguration getBenchmarkLoadGenConfiguration(Map<String,Object>... sources) {
        return new LoadGenConfiguration(
                getValidatedValue("loadgen-config.mode", PermittedModes, sources),
                getIntValue( "loadgen-config.warm-up-seconds", sources)
        );
    }

    private LoadGenConfiguration getModelLoadGenConfiguration(Map<String,Object>... sources) {
        return new LoadGenConfiguration(
                getValidatedValue("loadgen-config.mode", PermittedModes, sources),
                getIntValue("loadgen-config.grace-period-seconds", sources),
                getIntValue( "loadgen-config.warm-up-seconds", sources),
                toChecks(getListValue("loadgen-config.checks", sources))
        );
    }

    private List<Check> toChecks(List<String> values) {
        List<Check> checks = new ArrayList<>();

        for(String value : values) {
            switch (value.toLowerCase()) {
                case "dataloss":
                    checks.add(Check.DataLoss);
                    break;
                case "ordering":
                    checks.add(Check.Ordering);
                    break;
                case "duplicates":
                    checks.add(Check.Duplicates);
                    break;
                case "connectivity":
                    checks.add(Check.Connectivity);
                    break;
                case "consume-gaps":
                    checks.add(Check.ConsumeGaps);
                    break;
                case "all":
                    checks.add(Check.All);
                    break;
                default:
                    throw new InvalidInputException(value + " is not a valid check");
            }
        }

        return checks;
    }

    private SystemParser getParser(Host host, K8sEngine k8sEngine) {
        switch(host) {
            case EC2:
                return new EC2SystemParser(yaml, configurationRoot.getAbsolutePath() + "/systems/ec2", configParser);
            case K8S:
                switch(k8sEngine) {
                    case EKS:
                        return new K8sSystemParser(yaml, configurationRoot.getAbsolutePath() + "/systems/eks", Host.K8S, K8sEngine.EKS, configParser);
                    case GKE:
                        return new K8sSystemParser(yaml, configurationRoot.getAbsolutePath() + "/systems/gke", Host.K8S, K8sEngine.GKE, configParser);
                    default:
                        throw new InvalidInputException("K8sEngine not supported: " + k8sEngine);
                }
            default:
                throw new InvalidInputException("Host not supported: " + host);
        }
    }

    private List<BaseSystem> loadSystems(int parallel, Map<String,Object> commonSource) {
        List<BaseSystem> systems = new ArrayList<>();
        for(Map<String, Object> systemSource : getSubTreeList("systems", playlistRoot)) {
            Host host = Host.valueOf(getValidatedValue("host", PermittedHosts, systemSource).toUpperCase());
            if(host.equals(Host.K8S)) {
                K8sEngine k8sEngine = K8sEngine.valueOf(getValidatedValue("k8s-engine", PermittedK8sEngines, systemSource).toUpperCase());
                SystemParser parser = getParser(host, k8sEngine);
                for(int i=0; i<parallel; i++)
                    systems.add(parser.getSystem(systemSource, commonSource, i));
            }
            else {
                SystemParser parser = getParser(host, K8sEngine.NONE);
                for(int i=0; i<parallel; i++)
                    systems.add(parser.getSystem(systemSource, commonSource, i));
            }
        }

        return systems;
    }

}
