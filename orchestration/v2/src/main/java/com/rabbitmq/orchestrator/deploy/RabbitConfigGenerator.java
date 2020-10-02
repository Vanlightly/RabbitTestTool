package com.rabbitmq.orchestrator.deploy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RabbitConfigGenerator {
    public static String generateStandardConfig(Map<String,String> standardConfig, String separator) {
        StringBuilder sc = new StringBuilder();
        int i=0;
        for(Map.Entry<String,String> entry : standardConfig.entrySet()) {
            if(i > 0)
                sc.append(separator);
            sc.append(entry.getKey());
            sc.append(" = ");
            sc.append(entry.getValue());
            i++;
        }
        return sc.toString();
    }

    public static List<String> generateStandardConfigList(Map<String,String> standardConfig) {
        List<String> configs = new ArrayList<>();
        for(Map.Entry<String,String> entry : standardConfig.entrySet())
            configs.add(entry.getKey() +" = " + entry.getValue());

        return configs;
    }

    public static String generateAdvancedConfig(Map<String, String> advanced) {
        StringBuilder sb = new StringBuilder();
        int i=0;
        for(Map.Entry<String, String> entry : advanced.entrySet()) {
            if(i > 0)
                sb.append(",");
            sb.append("{");
            sb.append(entry.getKey());
            sb.append(",");
            sb.append(entry.getValue());
            sb.append("}");

            i++;
        }

        return sb.toString();
    }

    public static String generateEnvConfig(Map<String,String> envConfig) {
        StringBuilder sc = new StringBuilder();
        for(Map.Entry<String,String> entry : envConfig.entrySet()) {
            sc.append(entry.getKey().toUpperCase());
            sc.append("=");
            sc.append(entry.getValue());
            sc.append(System.lineSeparator());
        }
        return sc.toString();
    }

    public static String generateEnvConfig(Map<String,String> envConfig, String separator) {
        StringBuilder sc = new StringBuilder();
        int i=0;
        for(Map.Entry<String,String> entry : envConfig.entrySet()) {
            if(i > 0)
                sc.append(separator);
            sc.append(entry.getKey().toUpperCase());
            sc.append("=");
            sc.append(entry.getValue());
            i++;
        }
        return sc.toString();
    }
}
