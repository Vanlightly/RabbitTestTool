package com.rabbitmq.orchestrator.parsers;

import com.rabbitmq.orchestrator.InvalidInputException;
import com.rabbitmq.orchestrator.deploy.BaseSystem;
import com.rabbitmq.orchestrator.deploy.RabbitMQData;
import com.rabbitmq.orchestrator.deploy.ec2.model.*;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.stream.Collectors;

public class EC2YamlParser extends HostParser {
    ConfigurationParser configurationParser;

    public EC2YamlParser(Yaml yaml, String systemsRootDir, ConfigurationParser configurationParser) {
        super(yaml, systemsRootDir);
        this.configurationParser = configurationParser;
    }

    @Override
    public BaseSystem getSystem(Map<String, Object> input, Map<String, Object> common) {
        Map<String, String> baseAmis = loadBaseAmis();
        Map<String, EC2Instance> baseInstances = loadBaseInstances(baseAmis);
        Map<String, List<EC2VolumeConfig>> baseVolumeConfigs = loadBaseVolumeConfigs();

        String name = getStrValue(input, "name");

        Map<String, Object> systemBase = loadYamlFile(rootDir + "/" + getStrValue(input,"base"));
        Map<String, Object> overrides = getSubTree(input, "overrides");
        Map<String, Object> defaultsBase = loadYamlFile(rootDir + "/base/system-defaults.yml");
        List<Map<String,Object>> sources = Arrays.asList(common, overrides, systemBase, defaultsBase);

        EC2Hardware hardware = getHardware(baseInstances, baseVolumeConfigs, sources);
        EC2System system = new EC2System(name, hardware.getInstanceCount());
        system.setHardware(hardware);
        system.setRabbitmq(getRabbitMQ(sources));
        system.setRabbitmqConfig(configurationParser.getConfiguration(sources));
        system.setOs(getOs(sources));
        system.setFederationEnabled((Boolean)getObj("rabbitmq.federation-enabled", sources));

        return system;
    }

    private EC2Hardware getHardware(Map<String, EC2Instance> baseInstances,
                                    Map<String, List<EC2VolumeConfig>> baseVolumeConfigs,
                                    List<Map<String,Object>> sources) {
        EC2Instance loadGenInstance = null;
        String loadGenInstanceName = (String)getObj("hardware.loadgen.instance", sources);
        if(baseInstances.containsKey(loadGenInstanceName))
            loadGenInstance = baseInstances.get(loadGenInstanceName);
        else
            throw new InvalidInputException("No instances match name:" + loadGenInstance);

        EC2Instance rabbitmqInstance = null;
        String rabbitmqInstanceName = (String)getObj("hadrware.rabbitmq.instance", sources);
        if(baseInstances.containsKey(rabbitmqInstanceName))
            rabbitmqInstance = baseInstances.get(rabbitmqInstanceName);
        else
            throw new InvalidInputException("No instances match name:" + rabbitmqInstanceName);

        List<EC2VolumeConfig> volumeConfigs = null;
        String volumeConfigName = (String)getObj("hardware.rabbitmq.volume-config", sources);
        if(baseVolumeConfigs.containsKey(volumeConfigName))
            volumeConfigs = baseVolumeConfigs.get(volumeConfigName);
        else
            throw new InvalidInputException("No volume-configs match name:" + volumeConfigName);

        String tenancy = (String)getObj("hardware.rabbitmq.volume-config", sources);
        Integer instanceCount = (Integer)getObj("hardware.rabbitmq.count", sources);

        return new EC2Hardware(loadGenInstance,
                rabbitmqInstance,
                volumeConfigs,
                tenancy,
                instanceCount);
    }

    private EC2RabbitMQ getRabbitMQ(List<Map<String,Object>> sources) {
        return new EC2RabbitMQ(
                (String)getObj("rabbitmq.broker.version", sources),
                (String)getObj("rabbitmq.broker.generic-unix-url", sources),
                (String)getObj("rabbitmq.erlang.version", sources),
                (String)getObj("rabbitmq.erlang.deb-url", sources),
                (Boolean)getObj("rabbitmq.restart-brokers", sources));
    }

    private EC2OS getOs(List<Map<String,Object>> sources) {
        return new EC2OS(
                (Integer)getObj("os.fd-limit", sources),
                (String)getObj("os.filesystem", sources));
    }

    private Map<String, EC2Instance> loadBaseInstances(Map<String, String> baseAmis) {
        Map<String,Object> instances = loadYamlFile(rootDir + "/base/instances.yml");

        Map<String, EC2Instance> instanceMap = new HashMap<>();
        for(Map<String,Object> instance : getSubTreeList(instances, "instances")) {
            String name = getStrValue(instance, "name");
            instanceMap.put(name, new EC2Instance(
                    baseAmis.get(getStrValue(instance, "ami")),
                    getStrValue(instance, "type"),
                    getIntValue(instance, "cores"),
                    getIntValue(instance, "threads-per-core"),
                    getIntValue(instance, "memory-gb")
            ));
        }

        return instanceMap;
    }

    private Map<String, List<EC2VolumeConfig>> loadBaseVolumeConfigs() {
        Map<String,Object> instances = loadYamlFile(rootDir + "/base/volume-configs.yml");

        Map<String, List<EC2VolumeConfig>> volumeConfigMap = new HashMap<>();
        for(Map<String,Object> volumeRoot : getSubTreeList(instances, "volume-configs")) {
            String name = getStrValue(volumeRoot, "name");

            List<EC2VolumeConfig> volumeConfigs = new ArrayList<>();
            for(Map<String,Object> volume : getSubTreeList(volumeRoot, "volumes")) {
                volumeConfigs.add(new EC2VolumeConfig(
                        getStrValue(volume, "name"),
                        EC2VolumeType.valueOf(getValidatedValue(volume, "type", "gp2", "io1", "io2", "st1", "sc1", "local_nvme").toUpperCase()),
                        getIntValue(volume, "size-gb"),
                        getOptionalIntValue(volume, "iops-per-gb", 0),
                        getStrValue(volume, "mountpoint"),
                        getStringList(volume, "data").stream().map(x -> RabbitMQData.valueOf(x.toUpperCase())).collect(Collectors.toList())
                ));
            }

            volumeConfigMap.put(name, volumeConfigs);
        }

        return volumeConfigMap;
    }

    private Map<String, String> loadBaseAmis() {
        Map<String,Object> amis = loadYamlFile(rootDir + "/base/amis.yml");
        return toStringVal(getSubTree(amis, "amis"));
    }
}
