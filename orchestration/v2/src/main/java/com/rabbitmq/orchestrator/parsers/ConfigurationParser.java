package com.rabbitmq.orchestrator.parsers;

import com.rabbitmq.orchestrator.run.RabbitMQConfiguration;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigurationParser extends Parser {
    public ConfigurationParser(Yaml yaml, String rootDir) {
        super(yaml, rootDir);
    }

    public RabbitMQConfiguration getConfiguration(Map<String,Object>... sources) {
        if(!pathExists("rabbitmq.config", sources))
            return null;

        Map<String,Object>[] sourcesMod = new HashMap[sources.length];
        for(int i=0; i<sources.length; i++) {
            if(pathExists("rabbitmq.config.file", sources[i])) {
                Map<String,Object> fileSource = loadYamlFile(rootDir + "/rabbitmq-config/" + getStrValue("rabbitmq.config.file", sources[i]));
                sourcesMod[i] = fileSource;
            }
            else {
                sourcesMod[i] = sources[i];
            }
        }

        return new RabbitMQConfiguration(
                mergeStringMap(getSubTreesOfEachSource("rabbitmq.config.standard", sourcesMod)),
                mergeStringMap(getSubTreesOfEachSource("rabbitmq.config.advanced_rabbit", sourcesMod)),
                mergeStringMap(getSubTreesOfEachSource("rabbitmq.config.advanced_ra", sourcesMod)),
                mergeStringMap(getSubTreesOfEachSource("rabbitmq.config.advanced_aten", sourcesMod)),
                mergeStringMap(getSubTreesOfEachSource("rabbitmq.config.env", sourcesMod)),
                getOptionalStringList("rabbitmq.config.plugins", new ArrayList<>(), sourcesMod));
    }
}
