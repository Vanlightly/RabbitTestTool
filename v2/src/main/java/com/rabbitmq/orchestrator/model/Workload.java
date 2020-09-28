package com.rabbitmq.orchestrator.model;

import com.rabbitmq.orchestrator.model.actions.BrokerAction;
import com.rabbitmq.orchestrator.model.clients.ClientConfiguration;
import com.rabbitmq.orchestrator.model.clients.LoadGenConfiguration;
import com.rabbitmq.orchestrator.run.RabbitMQConfiguration;

import java.util.List;
import java.util.Map;

public class Workload {
    RabbitMQConfiguration brokerConfiguration;
    String topologyFile;
    Map<String,String> topologyVariables;
    String policiesFile;
    Map<String,String> policyVariables;
    BrokerAction brokerAction;
    String backgroundTopologyFile;
    String backgroundPoliciesFile;
    int backgroundDelaySeconds;
    int backgroundStepSeconds;
    int backgroundStepRepeat;
    ClientConfiguration clientConfiguration;
    LoadGenConfiguration loadgenConfiguration;
    int overrideStepSeconds;
    int overrideStepRepeat;
    int overrideStepMsgLimit;
    List<String> overrideBrokerHosts;

    public Workload(RabbitMQConfiguration brokerConfiguration,
                    String topologyFile,
                    Map<String, String> topologyVariables,
                    String policiesFile,
                    Map<String, String> policyVariables,
                    BrokerAction brokerAction,
                    String backgroundTopologyFile,
                    String backgroundPoliciesFile,
                    int backgroundDelaySeconds,
                    int backgroundStepSeconds,
                    int backgroundStepRepeat,
                    ClientConfiguration clientConfiguration,
                    LoadGenConfiguration loadgenConfiguration,
                    int overrideStepSeconds,
                    int overrideStepRepeat,
                    int overrideStepMsgLimit,
                    List<String> overrideBrokerHosts) {
        this.brokerConfiguration = brokerConfiguration;
        this.topologyFile = topologyFile;
        this.topologyVariables = topologyVariables;
        this.policiesFile = policiesFile;
        this.policyVariables = policyVariables;
        this.brokerAction = brokerAction;
        this.backgroundTopologyFile = backgroundTopologyFile;
        this.backgroundPoliciesFile = backgroundPoliciesFile;
        this.backgroundDelaySeconds = backgroundDelaySeconds;
        this.backgroundStepSeconds = backgroundStepSeconds;
        this.backgroundStepRepeat = backgroundStepRepeat;
        this.clientConfiguration = clientConfiguration;
        this.loadgenConfiguration = loadgenConfiguration;
        this.overrideStepSeconds = overrideStepSeconds;
        this.overrideStepRepeat = overrideStepRepeat;
        this.overrideStepMsgLimit = overrideStepMsgLimit;
        this.overrideBrokerHosts = overrideBrokerHosts;
    }

    public boolean hasConfigurationChanges() {
        return brokerConfiguration != null;
    }

    public RabbitMQConfiguration getBrokerConfiguration() {
        return brokerConfiguration;
    }

    public void setBrokerConfiguration(RabbitMQConfiguration brokerConfiguration) {
        this.brokerConfiguration = brokerConfiguration;
    }

    public String getTopologyFile() {
        return topologyFile;
    }

    public void setTopologyFile(String topologyFile) {
        this.topologyFile = topologyFile;
    }

    public Map<String, String> getTopologyVariables() {
        return topologyVariables;
    }

    public void setTopologyVariables(Map<String, String> topologyVariables) {
        this.topologyVariables = topologyVariables;
    }

    public String getPoliciesFile() {
        return policiesFile;
    }

    public void setPoliciesFile(String policiesFile) {
        this.policiesFile = policiesFile;
    }

    public Map<String, String> getPoliciesVariables() {
        return policyVariables;
    }

    public void setPoliciesVariables(Map<String, String> policyVariables) {
        this.policyVariables = policyVariables;
    }

    public BrokerAction getBrokerAction() {
        return brokerAction;
    }

    public void setBrokerAction(BrokerAction brokerAction) {
        this.brokerAction = brokerAction;
    }

    public boolean hasBackgroundLoad() {
        return backgroundTopologyFile != null;
    }

    public String getBackgroundTopologyFile() {
        return backgroundTopologyFile;
    }

    public void setBackgroundTopologyFile(String backgroundTopologyFile) {
        this.backgroundTopologyFile = backgroundTopologyFile;
    }

    public String getBackgroundPoliciesFile() {
        return backgroundPoliciesFile;
    }

    public void setBackgroundPoliciesFile(String backgroundPoliciesFile) {
        this.backgroundPoliciesFile = backgroundPoliciesFile;
    }

    public int getBackgroundDelaySeconds() {
        return backgroundDelaySeconds;
    }

    public void setBackgroundDelaySeconds(int backgroundDelaySeconds) {
        this.backgroundDelaySeconds = backgroundDelaySeconds;
    }

    public int getBackgroundStepSeconds() {
        return backgroundStepSeconds;
    }

    public void setBackgroundStepSeconds(int backgroundStepSeconds) {
        this.backgroundStepSeconds = backgroundStepSeconds;
    }

    public int getBackgroundStepRepeat() {
        return backgroundStepRepeat;
    }

    public void setBackgroundStepRepeat(int backgroundStepRepeat) {
        this.backgroundStepRepeat = backgroundStepRepeat;
    }

    public ClientConfiguration getClientConfiguration() {
        return clientConfiguration;
    }

    public void setClientConfiguration(ClientConfiguration clientConfiguration) {
        this.clientConfiguration = clientConfiguration;
    }

    public LoadGenConfiguration getLoadgenConfiguration() {
        return loadgenConfiguration;
    }

    public void setLoadgenConfiguration(LoadGenConfiguration loadgenConfiguration) {
        this.loadgenConfiguration = loadgenConfiguration;
    }

    public int getOverrideStepSeconds() {
        return overrideStepSeconds;
    }

    public void setOverrideStepSeconds(int overrideStepSeconds) {
        this.overrideStepSeconds = overrideStepSeconds;
    }

    public int getOverrideStepRepeat() {
        return overrideStepRepeat;
    }

    public void setOverrideStepRepeat(int overrideStepRepeat) {
        this.overrideStepRepeat = overrideStepRepeat;
    }

    public int getOverrideStepMsgLimit() {
        return overrideStepMsgLimit;
    }

    public void setOverrideStepMsgLimit(int overrideStepMsgLimit) {
        this.overrideStepMsgLimit = overrideStepMsgLimit;
    }

    public String getOverrideBrokerHostsStr() {
        if(overrideBrokerHosts == null)
            return "";

        return String.join(",", overrideBrokerHosts);
    }

    public List<String> getOverrideBrokerHosts() {
        return overrideBrokerHosts;
    }

    public void setOverrideBrokerHosts(List<String> overrideBrokerHosts) {
        this.overrideBrokerHosts = overrideBrokerHosts;
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
