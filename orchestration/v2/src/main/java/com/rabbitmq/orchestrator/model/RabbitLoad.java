package com.rabbitmq.orchestrator.model;

import java.util.Map;

public class RabbitLoad {
    String topologyFile;
    Map<String,String> topologyVariables;
    String policiesFile;
    Map<String,String> policyVariables;

    int delaySeconds;
    int stepSeconds;
    int stepRepeat;
    int stepMsgLimit;

    public RabbitLoad(String topologyFile, Map<String, String> topologyVariables, String policiesFile, Map<String, String> policyVariables, int delaySeconds, int stepSeconds, int stepRepeat, int stepMsgLimit) {
        this.topologyFile = topologyFile;
        this.topologyVariables = topologyVariables;
        this.policiesFile = policiesFile;
        this.policyVariables = policyVariables;
        this.delaySeconds = delaySeconds;
        this.stepSeconds = stepSeconds;
        this.stepRepeat = stepRepeat;
        this.stepMsgLimit = stepMsgLimit;
    }

    public String getTopologyFile() {
        return topologyFile;
    }

    public Map<String, String> getTopologyVariables() {
        return topologyVariables;
    }

    public String getPoliciesFile() {
        return policiesFile;
    }

    public Map<String, String> getPolicyVariables() {
        return policyVariables;
    }

    public int getDelaySeconds() {
        return delaySeconds;
    }

    public int getStepSeconds() {
        return stepSeconds;
    }

    public int getStepRepeat() {
        return stepRepeat;
    }

    public int getStepMsgLimit() {
        return stepMsgLimit;
    }

    public String getTopologyVariablesStr() {
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<String, String> variable : topologyVariables.entrySet())
            sb.append("--tvar." + variable.getKey() + " " + variable.getValue() + " ");

        return sb.toString();
    }

    public String getPoliciesVariablesStr() {
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<String, String> variable : policyVariables.entrySet())
            sb.append("--pvar." + variable.getKey() + " " + variable.getValue() + " ");

        return sb.toString();
    }
}
