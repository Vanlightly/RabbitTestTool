package com.rabbitmq.orchestrator.parsers;

import com.rabbitmq.orchestrator.InvalidInputException;
import com.rabbitmq.orchestrator.deploy.BaseSystem;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class SystemParser extends Parser {
    public SystemParser(Yaml yaml, String systemsRootDir) {
        super(yaml, systemsRootDir);
    }

    public abstract BaseSystem getSystem(Map<String,Object> input, Map<String,Object> common, int parallelOrdinal);
}
