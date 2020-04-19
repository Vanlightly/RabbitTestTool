package com.jackvanlightly.rabbittesttool.topology.model;

import com.jackvanlightly.rabbittesttool.topology.model.actions.ActionListConfig;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class QueueConfig {
    private String group;
    private String vhostName;
    private boolean isDownstream;
    private int scale;
    private List<Property> properties;
    private List<BindingConfig> bindings;
    private ShovelConfig shovelConfig;
    private ActionListConfig actionList;

    public QueueConfig() {
        properties = new ArrayList<>();
        bindings = new ArrayList<>();
    }

    public QueueConfig(String group, String vhostName, boolean isDownstream, int scale, List<Property> properties, List<BindingConfig> bindings, ShovelConfig shovelConfig, ActionListConfig actionList) {
        this.group = group;
        this.vhostName = vhostName;
        this.isDownstream = isDownstream;
        this.scale = scale;
        this.properties = properties;
        this.bindings = bindings;
        this.shovelConfig = shovelConfig;
        this.actionList = actionList;
    }

    public QueueConfig clone(int scaleNumber) {
        List<BindingConfig> newBindings = new ArrayList<>();
        for(BindingConfig bc : bindings) {
            newBindings.add(bc.clone(scaleNumber));
        }

        ShovelConfig sc = null;
        if(this.shovelConfig != null)
            sc = this.shovelConfig.clone(scaleNumber);

        return new QueueConfig(
                this.group + VirtualHost.getScaleSuffix(scaleNumber),
                this.vhostName,
                this.isDownstream,
                this.scale,
                this.properties,
                newBindings,
                sc,
                this.actionList);
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getVhostName() {
        return vhostName;
    }

    public void setVhostName(String vhostName) {
        this.vhostName = vhostName;
    }

    public boolean isDownstream() {
        return isDownstream;
    }

    public void setDownstream(boolean downstream) {
        isDownstream = downstream;
    }

    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    public List<BindingConfig> getBindings() {
        return bindings;
    }

    public void setBindings(List<BindingConfig> bindings) {
        this.bindings = bindings;
    }

    public List<String> getInitialQueues() {
        List<String> queues = new ArrayList<>();

        if(this.group.equals("sharded")) {
            queues.add("sharded");
        }
        else {
            for (int i = 1; i <= scale; i++)
                queues.add(getQueueName(i));
        }

        return queues;
    }

    public String getQueueName(int ordinal) {
        return this.group + "_" + StringUtils.leftPad(String.valueOf(ordinal), 5, "0");
    }

    public ShovelConfig getShovelConfig() {
        return shovelConfig;
    }

    public void setShovelConfig(ShovelConfig shovelConfig) {
        this.shovelConfig = shovelConfig;
    }

    public ActionListConfig getActionListConfig() {
        return actionList;
    }

    public void setActionList(ActionListConfig actionList) {
        this.actionList = actionList;
    }
}
