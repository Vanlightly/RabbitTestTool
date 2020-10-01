package com.rabbitmq.orchestrator.parsers;

import com.rabbitmq.orchestrator.InvalidInputException;
import com.rabbitmq.orchestrator.deploy.BaseSystem;
import com.rabbitmq.orchestrator.deploy.k8s.model.*;
import com.rabbitmq.orchestrator.model.hosts.Host;
import org.yaml.snakeyaml.Yaml;

import java.util.HashMap;
import java.util.Map;

public class K8sSystemParser extends SystemParser {
    K8sEngine k8sEngine;
    Host host;
    ConfigurationParser configurationParser;
    static String[] PermittedVolumeTypes = {"gp2", "io1", "io2", "st1", "sc1", "ssd"};

    public K8sSystemParser(Yaml yaml,
                           String systemsRootDir,
                           Host host,
                           K8sEngine k8sEngine,
                           ConfigurationParser configurationParser) {
        super(yaml, systemsRootDir);
        this.k8sEngine = k8sEngine;
        this.host = host;
        this.configurationParser = configurationParser;
    }

    @Override
    public BaseSystem getSystem(Map<String, Object> source, Map<String,Object> commonSource, int parallelOrdinal) {
        Map<String, K8sInstance> baseInstances = loadBaseInstances();
        Map<String, K8sVolumeConfig> baseVolumeConfigs = loadBaseVolumeConfigs();

        String name = getStrValue("name", source) + BaseSystem.SystemSuffixes[parallelOrdinal];

        Map<String, Object> systemBase = loadYamlFile(rootDir + "/" + getStrValue("file", source));
        Map<String, Object> overrides = getSubTree("overrides", source);
        Map<String, Object> defaultsBase = loadYamlFile(rootDir + "/base/defaults.yml");

        K8sHardware hardware = getHardware(baseInstances, baseVolumeConfigs, commonSource, overrides, systemBase, defaultsBase);
        K8sSystem system = new K8sSystem(name,
                host,
                k8sEngine,
                hardware,
                getRabbitMQ(commonSource, overrides, systemBase, defaultsBase),
                configurationParser.getConfiguration(commonSource, overrides, systemBase, defaultsBase));

        return system;
    }

    private K8sHardware getHardware(Map<String, K8sInstance> baseInstances,
                                    Map<String, K8sVolumeConfig> baseVolumeConfigs,
                                    Map<String,Object>... sources) {
        K8sInstance instance = null;
        String instanceName = (String)getObj("hardware.instance", sources);
        if(baseInstances.containsKey(instanceName))
            instance = baseInstances.get(instanceName);
        else
            throw new InvalidInputException("No instances match name:" + instanceName);

        K8sVolumeConfig volumeConfig = null;
        String volumeConfigName = (String)getObj("hardware.volume-config", sources);
        if(baseVolumeConfigs.containsKey(volumeConfigName))
            volumeConfig = baseVolumeConfigs.get(volumeConfigName);
        else
            throw new InvalidInputException("No volume-configs match name:" + volumeConfigName);

        Integer instanceCount = (Integer)getObj("hardware.count", sources);

        return new K8sHardware(instance, instanceCount, volumeConfig);
    }

    private K8sRabbitMQ getRabbitMQ(Map<String,Object>... sources) {
        return new K8sRabbitMQ(
                (String)getObj("rabbitmq.broker.version", sources),
                (String)getObj("rabbitmq.broker.image", sources),
                (Boolean)getObj("rabbitmq.restart-brokers", sources));
    }

    private Map<String, K8sInstance> loadBaseInstances() {
        Map<String,Object> instancesSource = loadYamlFile(rootDir + "/base/instances.yml");

        Map<String, K8sInstance> instanceMap = new HashMap<>();
        for(Map<String,Object> instance : getSubTreeList("instances", instancesSource)) {
            String name = getStrValue("name", instance);
            instanceMap.put(name, new K8sInstance(
                    getStrValue("type", instance),
                    getIntValue("vcpus", instance),
                    getIntValue("memory-gb", instance)
            ));
        }

        return instanceMap;
    }

    private Map<String, K8sVolumeConfig> loadBaseVolumeConfigs() {
        Map<String,Object> volumesSource = loadYamlFile(rootDir + "/base/volume-configs.yml");

        Map<String, K8sVolumeConfig> volumeConfigMap = new HashMap<>();
        for(Map<String,Object> volumeRoot : getSubTreeList("volume-configs", volumesSource)) {
            String name = getStrValue("name", volumeRoot);
            K8sVolumeConfig volumeConfig = new K8sVolumeConfig(
                    getStrValue("name", volumeRoot),
                    K8sVolumeType.valueOf(getValidatedValue("type", PermittedVolumeTypes, volumeRoot).toUpperCase()),
                    getIntValue("size-gb", volumeRoot));
            volumeConfigMap.put(name, volumeConfig);
        }

        return volumeConfigMap;
    }


}
