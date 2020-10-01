package com.rabbitmq.orchestrator.parsers;

import com.rabbitmq.orchestrator.InvalidInputException;
import com.rabbitmq.orchestrator.deploy.BaseSystem;
import com.rabbitmq.orchestrator.deploy.RabbitMQData;
import com.rabbitmq.orchestrator.deploy.ec2.model.*;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.stream.Collectors;

public class EC2SystemParser extends SystemParser {
    ConfigurationParser configurationParser;
    static String[] PermittedVolumeTypes = {"gp2", "io1", "io2", "st1", "sc1", "local_nvme"};

    public EC2SystemParser(Yaml yaml, String systemsRootDir, ConfigurationParser configurationParser) {
        super(yaml, systemsRootDir);
        this.configurationParser = configurationParser;
    }

    @Override
    public BaseSystem getSystem(Map<String, Object> source, Map<String, Object> commonSource, int parallelOrdinal) {
        Map<String, String> baseAmis = loadBaseAmis();
        Map<String, EC2Instance> baseInstances = loadBaseInstances(baseAmis);
        Map<String, List<EC2VolumeConfig>> baseVolumeConfigs = loadBaseVolumeConfigs();

        String name = getStrValue("name", source) + BaseSystem.SystemSuffixes[parallelOrdinal];

        Map<String, Object> systemBase = loadYamlFile(rootDir + "/" + getStrValue("file", source));
        Map<String, Object> overrides = getSubTree("overrides", source);
        Map<String, Object> defaultsBase = loadYamlFile(rootDir + "/base/defaults.yml");

        EC2Hardware hardware = getHardware(baseInstances, baseVolumeConfigs, commonSource, overrides, systemBase, defaultsBase);
        EC2System system = new EC2System(name, hardware.getInstanceCount());
        system.setHardware(hardware);
        system.setRabbitmq(getRabbitMQ(commonSource, overrides, systemBase, defaultsBase));
        system.setRabbitmqConfig(configurationParser.getConfiguration(commonSource, overrides, systemBase, defaultsBase));
        system.setOs(getOs(commonSource, overrides, systemBase, defaultsBase));
        system.setFederationEnabled((Boolean)getObj("rabbitmq.federation-enabled", commonSource, overrides, systemBase, defaultsBase));

        return system;
    }

    private EC2Hardware getHardware(Map<String, EC2Instance> baseInstances,
                                    Map<String, List<EC2VolumeConfig>> baseVolumeConfigs,
                                    Map<String,Object>... sources) {
        EC2Instance loadGenInstance = null;
        String loadGenInstanceName = (String)getObj("hardware.loadgen.instance", sources);
        if(baseInstances.containsKey(loadGenInstanceName))
            loadGenInstance = baseInstances.get(loadGenInstanceName);
        else
            throw new InvalidInputException("No instances match name:" + loadGenInstance);

        EC2Instance rabbitmqInstance = null;
        String rabbitmqInstanceName = (String)getObj("hardware.rabbitmq.instance", sources);
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

        String tenancy = (String)getObj("hardware.rabbitmq.tenancy", sources);
        Integer instanceCount = (Integer)getObj("hardware.rabbitmq.count", sources);

        return new EC2Hardware(loadGenInstance,
                rabbitmqInstance,
                volumeConfigs,
                tenancy,
                instanceCount);
    }

    private EC2RabbitMQ getRabbitMQ(Map<String,Object>... sources) {
        return new EC2RabbitMQ(
                (String)getObj("rabbitmq.broker.version", sources),
                (String)getObj("rabbitmq.broker.generic-unix-url", sources),
                (String)getObj("rabbitmq.erlang.version", sources),
                (String)getObj("rabbitmq.erlang.deb-url", sources),
                (Boolean)getObj("rabbitmq.restart-brokers", sources));
    }

    private EC2OS getOs(Map<String,Object>... sources) {
        return new EC2OS(
                (Integer)getObj("os.fd-limit", sources),
                (String)getObj("os.filesystem", sources));
    }

    private Map<String, EC2Instance> loadBaseInstances(Map<String, String> baseAmis) {
        Map<String,Object> instancesSource = loadYamlFile(rootDir + "/base/instances.yml");

        Map<String, EC2Instance> instanceMap = new HashMap<>();
        for(Map<String,Object> instance : getSubTreeList("instances", instancesSource)) {
            String name = getStrValue("name", instance);
            instanceMap.put(name, new EC2Instance(
                    baseAmis.get(getStrValue("ami", instance)),
                    getStrValue("type", instance),
                    getIntValue("cores", instance),
                    getIntValue("threads-per-core", instance),
                    getIntValue("memory-gb", instance)
            ));
        }

        return instanceMap;
    }

    private Map<String, List<EC2VolumeConfig>> loadBaseVolumeConfigs() {
        Map<String,Object> volumesSource = loadYamlFile(rootDir + "/base/volume-configs.yml");

        Map<String, List<EC2VolumeConfig>> volumeConfigMap = new HashMap<>();
        for(Map<String,Object> volumeRoot : getSubTreeList("volume-configs", volumesSource)) {
            String name = getStrValue("name", volumeRoot);

            List<EC2VolumeConfig> volumeConfigs = new ArrayList<>();
            for(Map<String,Object> volume : getSubTreeList("volumes", volumeRoot)) {
                volumeConfigs.add(new EC2VolumeConfig(
                        getStrValue("name", volume),
                        EC2VolumeType.valueOf(getValidatedValue("type", PermittedVolumeTypes, volume).toUpperCase()),
                        getIntValue("size-gb", volume),
                        getOptionalIntValue("iops-per-gb", 0, volume),
                        getStrValue("mountpoint", volume),
                        getStringList("data", volume).stream().map(x -> RabbitMQData.valueOf(x.toUpperCase())).collect(Collectors.toList())
                ));
            }

            volumeConfigMap.put(name, volumeConfigs);
        }

        return volumeConfigMap;
    }

    private Map<String, String> loadBaseAmis() {
        Map<String,Object> amiSource = loadYamlFile(rootDir + "/base/amis.yml");
        return toStringVal(getSubTree("amis", amiSource));
    }
}
