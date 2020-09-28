package com.rabbitmq.orchestrator.run;

import java.util.List;
import java.util.Map;

public class RabbitMQConfiguration {
    Map<String,String> standard;
    Map<String,String> advancedRabbit;
    Map<String,String> advancedRa;
    Map<String,String> advancedAten;
    List<String> plugins;
    Map<String,String> envConfig;

    public RabbitMQConfiguration(Map<String, String> standard,
                                 Map<String, String> advancedRabbit,
                                 Map<String, String> advancedRa,
                                 Map<String, String> advancedAten,
                                 Map<String,String> envConfig,
                                 List<String> plugins) {
        this.standard = standard;
        this.advancedRabbit = advancedRabbit;
        this.advancedRa = advancedRa;
        this.advancedAten = advancedAten;
        this.envConfig = envConfig;
        this.plugins = plugins;
    }

    public Map<String, String> getStandard() {
        return standard;
    }

    public void setStandard(Map<String, String> standard) {
        this.standard = standard;
    }

    public Map<String, String> getAdvancedRabbit() {
        return advancedRabbit;
    }

    public void setAdvancedRabbit(Map<String, String> advancedRabbit) {
        this.advancedRabbit = advancedRabbit;
    }

    public Map<String, String> getAdvancedRa() {
        return advancedRa;
    }

    public void setAdvancedRa(Map<String, String> advancedRa) {
        this.advancedRa = advancedRa;
    }

    public Map<String, String> getAdvancedAten() {
        return advancedAten;
    }

    public void setAdvancedAten(Map<String, String> advancedAten) {
        this.advancedAten = advancedAten;
    }

    public Map<String, String> getEnvConfig() {
        return envConfig;
    }

    public void setEnvConfig(Map<String, String> envConfig) {
        this.envConfig = envConfig;
    }

    public List<String> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<String> plugins) {
        this.plugins = plugins;
    }
}
