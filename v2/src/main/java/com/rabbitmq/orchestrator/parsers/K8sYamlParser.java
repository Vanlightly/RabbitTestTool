package com.rabbitmq.orchestrator.parsers;

import com.rabbitmq.orchestrator.InvalidInputException;
import com.rabbitmq.orchestrator.deploy.BaseSystem;
import com.rabbitmq.orchestrator.deploy.k8s.model.*;
import com.rabbitmq.orchestrator.model.hosts.Host;
import org.yaml.snakeyaml.Yaml;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class K8sYamlParser extends HostParser {
    K8sEngine k8sEngine;
    Host host;
    ConfigurationParser configurationParser;

    public K8sYamlParser(Yaml yaml,
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
    public BaseSystem getSystem(Map<String, Object> input, Map<String,Object> common) {
        Map<String, K8sInstance> baseInstances = loadBaseInstances();
        Map<String, K8sVolumeConfig> baseVolumeConfigs = loadBaseVolumeConfigs();

        String name = getStrValue(input, "name");

        Map<String, Object> systemBase = loadYamlFile(rootDir + "/" + getStrValue(input,"base"));
        Map<String, Object> overrides = getSubTree(input, "overrides");
        Map<String, Object> defaultsBase = loadYamlFile(rootDir + "/base/system-defaults.yml");
        List<Map<String,Object>> sources = Arrays.asList(common, overrides, systemBase, defaultsBase);

        K8sHardware hardware = getHardware(baseInstances, baseVolumeConfigs, sources);
        K8sSystem system = new K8sSystem(name,
                host,
                k8sEngine,
                hardware,
                getRabbitMQ(sources),
                configurationParser.getConfiguration(sources));

        return system;
    }

    private K8sHardware getHardware(Map<String, K8sInstance> baseInstances,
                                    Map<String, K8sVolumeConfig> baseVolumeConfigs,
                                    List<Map<String,Object>> sources) {
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

    private K8sRabbitMQ getRabbitMQ(List<Map<String,Object>> sources) {
        return new K8sRabbitMQ(
                (String)getObj("rabbitmq.broker.version", sources),
                (String)getObj("rabbitmq.broker.image", sources),
                false);
    }

    private Map<String, K8sInstance> loadBaseInstances() {
        Map<String,Object> instances = loadYamlFile(rootDir + "/base/instances.yml");

        Map<String, K8sInstance> instanceMap = new HashMap<>();
        for(Map<String,Object> instance : getSubTreeList(instances, "instances")) {
            String name = getStrValue(instance, "name");
            instanceMap.put(name, new K8sInstance(
                    getStrValue(instance, "type"),
                    getIntValue(instance, "vcpus"),
                    getIntValue(instance, "memory-gb")
            ));
        }

        return instanceMap;
    }

    private Map<String, K8sVolumeConfig> loadBaseVolumeConfigs() {
        Map<String,Object> instances = loadYamlFile(rootDir + "/base/volume-configs.yml");

        Map<String, K8sVolumeConfig> volumeConfigMap = new HashMap<>();
        for(Map<String,Object> volumeRoot : getSubTreeList(instances, "volume-configs")) {
            String name = getStrValue(volumeRoot, "name");
            K8sVolumeConfig volumeConfig = new K8sVolumeConfig(
                    getStrValue(volumeRoot, "name"),
                    K8sVolumeType.valueOf(getValidatedValue(volumeRoot, "type", "gp2", "io1", "io2", "st1", "sc1").toUpperCase()),
                    getIntValue(volumeRoot, "size-gb"));
            volumeConfigMap.put(name, volumeConfig);
        }

        return volumeConfigMap;
    }


}
