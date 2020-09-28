package com.rabbitmq.orchestrator.parsers;

import com.rabbitmq.orchestrator.InvalidInputException;
import com.rabbitmq.orchestrator.deploy.BaseSystem;
import com.rabbitmq.orchestrator.deploy.OutputData;
import com.rabbitmq.orchestrator.deploy.k8s.model.K8sEngine;
import com.rabbitmq.orchestrator.meta.EC2Meta;
import com.rabbitmq.orchestrator.meta.K8sMeta;
import com.rabbitmq.orchestrator.model.ActionType;
import com.rabbitmq.orchestrator.model.Playlist;
import com.rabbitmq.orchestrator.model.Workload;
import com.rabbitmq.orchestrator.model.actions.ActionTrigger;
import com.rabbitmq.orchestrator.model.actions.BrokerAction;
import com.rabbitmq.orchestrator.model.actions.TrafficControl;
import com.rabbitmq.orchestrator.model.clients.Check;
import com.rabbitmq.orchestrator.model.clients.ClientConfiguration;
import com.rabbitmq.orchestrator.model.clients.ClientConnectMode;
import com.rabbitmq.orchestrator.model.clients.LoadGenConfiguration;
import com.rabbitmq.orchestrator.model.hosts.Host;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class YamlParser extends Parser {
    Map<String, Object> fields;
    Map<String,Object> commonWorkload;
    Yaml yaml;
    File metaFileDir;
    File configurationRoot;
    ConfigurationParser configParser;

    public YamlParser(String metaFileDir, String configurationRoot) {
        super(new Yaml(), configurationRoot);
        this.metaFileDir = new File(metaFileDir);
        if(!this.metaFileDir.exists())
            throw new InvalidInputException("The metadata directory provided does not exist");

        this.configurationRoot = new File(configurationRoot);
        if(!this.configurationRoot.exists())
            throw new InvalidInputException("The configuration directory provided does not exist");

        this.configParser = new ConfigurationParser(yaml, configurationRoot);
    }

    public void loadPlaylist(String playlistFile) {
        fields = loadYamlFile(playlistFile);
        commonWorkload = getSubTree(fields, "common-workload");
    }

    public EC2Meta loadEc2Meta() {
        Map<String,Object> f = loadYamlFile(metaFileDir.getAbsolutePath() + "/ec2.yml");

        return new EC2Meta(
                getStrValue(f, "keypair"),
                getStrValue(f, "aws-user"),
                getStrValue(f, "loadgen-sg"),
                getStrValue(f, "broker-sg"),
                getListValue(f, "subnets"),
                getStrValue(f, "deploy-root-dir"),
                getStrValue(f, "run-root-dir"));
    }

    public K8sMeta loadGkeMeta() {
        Map<String,Object> f = loadYamlFile(metaFileDir.getAbsolutePath() + "/gke.yml");

        return new K8sMeta(
                getStrValue(f, "project"),
                getStrValue(f, "zones"),
                getStrValue(f, "k8s-version"),
                getStrValue(f, "manifests-dir"),
                getStrValue(f, "deploy-root-dir"),
                getStrValue(f, "run-root-dir"));
    }

    public K8sMeta loadEksMeta() {
        Map<String,Object> f = loadYamlFile(metaFileDir.getAbsolutePath() + "/eks.yml");

        return new K8sMeta(
                getStrValue(f, "aws-user"),
                getStrValue(f, "region"),
                getStrValue(f, "k8s-version"),
                getStrValue(f, "manifests-dir"),
                getStrValue(f, "deploy-root-dir"),
                getStrValue(f, "run-root-dir"));
    }

    public OutputData loadOutputData() {
        Map<String,Object> f = loadYamlFile(metaFileDir.getAbsolutePath() + "/output-data.yml");

        return new OutputData(
                getStrValue(f, "influx-subpath"),
                getStrValue(f, "influx-username"),
                getStrValue(f, "influx-password"),
                getStrValue(f, "influx-url"),
                getStrValue(f, "influx-db_name"),
                getStrValue(f, "postgres-jdbc-url"),
                getStrValue(f, "postgres-username"),
                getStrValue(f, "postgres-password"),
                getStrValue(f, "postgres-database"),
                getStrValue(f, "rabbitmq-username"),
                getStrValue(f, "rabbitmq-password"));
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

    public Playlist getPlaylist() {



        return null;
    }

    private Workload getCommonWorkload() {
        return new Workload(
                configParser.getConfiguration(Arrays.asList(commonWorkload)),
                getOptionalStrValue(commonWorkload, "topology.file", ""),
                toStringVal(getOptionalSubTree(commonWorkload, "topology.variables", new HashMap<>())),
                getOptionalStrValue(commonWorkload, "policies.file", ""),
                toStringVal(getOptionalSubTree(commonWorkload, "policies.variables", new HashMap<>())),
                getBrokerAction(commonWorkload),
                getOptionalStrValue(commonWorkload, "bg-topology-file", ""),
                getOptionalStrValue(commonWorkload, "bg-policies-file", ""),
                getOptionalIntValue(commonWorkload, "bg-delay-seconds", 0),
                getOptionalIntValue(commonWorkload, "bg-step-seconds", 0),
                getOptionalIntValue(commonWorkload, "bg-step-repeat", 0),
                getClientConfig(commonWorkload),
                getLoadGenConfiguration(commonWorkload),
                getOptionalIntValue(commonWorkload, "override-delay-seconds", 0),
                getOptionalIntValue(commonWorkload, "override-step-seconds", 0),
                getOptionalIntValue(commonWorkload, "override-step-repeat", 0),
                getListValue(commonWorkload, "override-broker-hosts")
        );
    }

    private BrokerAction getBrokerAction(Map<String,Object> source) {
        if(pathExists(source, "broker-actions.type")) {
            TrafficControl tc = null;
            if(pathExists(source, "broker-actions.traffic-control")) {
                tc = getTrafficControl(source);
            }

            return new BrokerAction(
                    ActionType.valueOf(getStrValue(source, "broker-actions.action-type").toUpperCase()),
                    ActionTrigger.valueOf(getStrValue(source, "broker-actions.trigger-type").toUpperCase()),
                    getIntValue(source, "broker-actions.trigger-value"),
                    tc
            );
        }

        return null;
    }

    private TrafficControl getTrafficControl(Map<String,Object> source) {
        return new TrafficControl(
                getBoolValue(source, "traffic-control.apply-to-client-traffic"),
                getBoolValue(source, "traffic-control.apply-to-all-brokers"),
                getOptionalIntValue(source, "traffic-control.delay-ms", 0),
                getOptionalIntValue(source, "traffic-control.delay-jtter-ms", 0),
                getOptionalValidatedValue(source, "delay-distribution", "none","none", "uniform", "normal", "pareto", "paretonormal"),
                getOptionalIntValue(source, "traffic-control.bandwidth", 0),
                getValidatedValue(source, "traffic-control.packet-loss-mode", "none", "gemodel", "random" ),
                getOptionalStrValue(source, "traffic-control.packet-loss-arg1", "0%"),
                getOptionalStrValue(source, "traffic-control.packet-loss-arg2", "0%"),
                getOptionalStrValue(source, "traffic-control.packet-loss-arg3", "0%"),
                getOptionalStrValue(source, "traffic-control.packet-loss-arg4", "0%")
        );
    }

    private ClientConfiguration getClientConfig(Map<String,Object> source) {
        return new ClientConfiguration(
                getOptionalBoolValue(source, "client-config.tcp-no-delay", false),
                toClientConnectMode(getOptionalValidatedValue(source, "client-config.publisher-connect-mode", "round-robin", "round-robin", "random", "leader", "non-leader")),
                toClientConnectMode(getOptionalValidatedValue(source, "client-config.consumer-connect-mode", "round-robin", "round-robin", "random", "leader", "non-leader")),
                getOptionalIntValue(source, "client-config.publisher-heartbeat-seconds", 10),
                getOptionalIntValue(source, "client-config.consumer-heartbeat-seconds", 10)
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

    private LoadGenConfiguration getLoadGenConfiguration(Map<String,Object> source) {
        return new LoadGenConfiguration(
                getValidatedValue(source, "loadgen-config", "benchmark", "model"),
                getOptionalIntValue(source, "grace-period-seconds", 60),
                getOptionalIntValue(source, "grace-period-seconds", 0),
                toChecks(getListValue(source, "checks"))
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

    private HostParser getParser(Host host, K8sEngine k8sEngine) {
        ConfigurationParser configParser = new ConfigurationParser(yaml, "");

        switch(host) {
            case EC2:
                return new EC2YamlParser(yaml, configurationRoot.getAbsolutePath() + "/systems/ec2", configParser);
            case K8S:
                switch(k8sEngine) {
                    case EKS:
                        return new K8sYamlParser(yaml, configurationRoot.getAbsolutePath() + "/systems/eks", Host.K8S, K8sEngine.EKS, configParser);
                    case GKE:
                        return new K8sYamlParser(yaml, configurationRoot.getAbsolutePath() + "/systems/gke", Host.K8S, K8sEngine.GKE, configParser);
                    default:
                        throw new InvalidInputException("K8sEngine not supported: " + k8sEngine);
                }
            default:
                throw new InvalidInputException("Host not supported: " + host);
        }
    }

    private  Playlist getPlaylistHC() {
//        List<Benchmark> assignments = new ArrayList<>();
//        Map<String, Workload> workloads = new HashMap<>();
//        workloads.put("s1", getWorkload());
//        //workloads.put("s2", getWorkload());
//        //workloads.put("s3", getWorkload());
//        assignments.add(new Benchmark(workloads, 1));
//        assignments.add(new Benchmark(workloads, 2));
//        assignments.add(new Benchmark(workloads, 3));
//        assignments.add(new Benchmark(workloads, 4));
//        assignments.add(new Benchmark(workloads, 5));
//
//        Playlist playlist = new Playlist();
//        playlist.setBenchmarks(assignments);
//
//        return playlist;
        return null;
    }

    private Workload getWorkload() {
//        Workload workload = new Workload();
//        workload.setClientConfiguration(new ClientConfiguration(false,
//                ClientConnectMode.RoundRobin,
//                ClientConnectMode.RoundRobin,
//                0,
//                0,
//                "benchmark",
//                0,
//                10,
//                Arrays.asList(Check.All)));
//
//        workload.setTopologyFile("point-to-point/point-to-point.json");
//        workload.setTopologyVariables(new HashMap<>());
//        workload.setPoliciesFile("mirrored-queue.json");
//        workload.setPoliciesVariables(new HashMap<>());
//
//        return workload;

        return null;
    }

    public List<BaseSystem> getSystems() {
        List<BaseSystem> systems = new ArrayList<>();
        for(Map<String, Object> systemInput : getSubTreeList(fields, "systems")) {
            Host host = Host.valueOf(getValidatedValue(systemInput, "host", "ec2", "gcp", "k8s").toUpperCase());
            if(host.equals(Host.K8S)) {
                K8sEngine k8sEngine = K8sEngine.valueOf(getValidatedValue(systemInput, "k8s-engine", "eks", "gke").toUpperCase());
                HostParser parser = getParser(host, k8sEngine);
                systems.add(parser.getSystem(systemInput, commonWorkload));
            }
            else {
                HostParser parser = getParser(host, K8sEngine.NONE);
                systems.add(parser.getSystem(systemInput, commonWorkload));
            }
        }

        return systems;
    }

//    public static String getValue(Map<String,Object> f, String field) {
//        if(!f.containsKey(field))
//            throw new InvalidInputException("'" + field + "' is required");
//
//        return f.get(field).toString();
//    }
//
//    public static String getValidatedValue(Map<String,Object> f, String field, String... permitted) {
//        if(!f.containsKey(field))
//            throw new InvalidInputException("'" + field + "' is required");
//
//        String value = f.get(field).toString();
//        if(isValid(permitted, value))
//            return value;
//
//        throw new InvalidInputException("'" + field + "' can only have values: " + String.join(",", permitted));
//    }
//
//    private static boolean isValid(String[] permitted, String value) {
//        boolean found = false;
//        for(String v : permitted) {
//            if (v.toLowerCase().equals(value.toLowerCase()))
//                found = true;
//        }
//
//        return found;
//    }
//
//    public static Integer getIntValue(Map<String,Object> f, String field) {
//        if(!f.containsKey(field))
//            throw new InvalidInputException("'" + field + "' is required");
//
//        return (Integer)f.get(field);
//    }
//
//    public static Integer getOptionalIntValue(Map<String,Object> f, String field, Integer defaultValue) {
//        if(!f.containsKey(field))
//            return defaultValue;
//
//        return (Integer)f.get(field);
//    }
//
//    public static  List<String> getListValue(Map<String,Object> f, String field) {
//        if(!f.containsKey(field))
//            throw new InvalidInputException("'" + field + "' is required");
//
//        return Arrays.stream(f.get(field).toString().split(",")).collect(Collectors.toList());
//    }
//
//    public static  Map<String,Object> getSubTree(Map<String,Object> f, String field) {
//        if(!f.containsKey(field))
//            throw new InvalidInputException("'" + field + "' is required");
//
//        return (Map<String,Object>)f.get(field);
//    }
//
//    public static  List<Map<String,Object>> getSubTreeList(Map<String,Object> f, String field) {
//        if(!f.containsKey(field))
//            throw new InvalidInputException("'" + field + "' is required");
//
//        return (List<Map<String,Object>>)f.get(field);
//    }
//
//    public static List<String> getStringList(Map<String,Object> f, String field) {
//        if(!f.containsKey(field))
//            throw new InvalidInputException("'" + field + "' is required");
//
//        return (List<String>)f.get(field);
//    }
//
//    public static Object getObjAtPath(Map<String,Object> f, String fieldPath, boolean isMandatory) {
//        Map<String, Object> currObject = f;
//        String[] fieldList = fieldPath.split("\\.");
//        for(int i=0; i<fieldList.length; i++) {
//            String field = fieldList[i];
//
//            if(i == fieldList.length-1) {
//                return currObject.get(field);
//            }
//            else if(currObject.containsKey(field))
//                currObject = (Map<String, Object>)currObject.get(field);
//            else
//                break;
//        }
//
//        if(isMandatory)
//            throw new InvalidInputException("Path '" + fieldPath + "' does not exist");
//
//        return null;
//    }
}
