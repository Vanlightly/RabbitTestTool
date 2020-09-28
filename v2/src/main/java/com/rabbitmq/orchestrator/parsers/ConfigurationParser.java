package com.rabbitmq.orchestrator.parsers;

import com.rabbitmq.orchestrator.run.RabbitMQConfiguration;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

public class ConfigurationParser extends Parser {
    public ConfigurationParser(Yaml yaml, String rootDir) {
        super(yaml, rootDir);
    }

    public RabbitMQConfiguration getConfiguration(List<Map<String,Object>> sources) {
        return new RabbitMQConfiguration(
                toStringVal((Map<String,Object>)getObj("rabbitmq.config.standard", sources)),
                toStringVal((Map<String,Object>)getObj("rabbitmq.config.advanced_rabbit", sources)),
                toStringVal((Map<String,Object>)getObj("rabbitmq.config.advanced_ra", sources)),
                toStringVal((Map<String,Object>)getObj("rabbitmq.config.advanced_aten", sources)),
                toStringVal((Map<String,Object>)getObj("rabbitmq.config.env", sources)),
                (List<String>)getObj("rabbitmq.config.plugins", sources));
    }
}
