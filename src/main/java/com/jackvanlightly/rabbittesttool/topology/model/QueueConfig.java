package com.jackvanlightly.rabbittesttool.topology.model;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class QueueConfig {
    private String group;
    private String vhostName;
    private int scale;
    private List<Property> properties;
    private List<BindingConfig> bindings;

    public QueueConfig() {
        properties = new ArrayList<>();
        bindings = new ArrayList<>();
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
}
