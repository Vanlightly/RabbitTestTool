package com.jackvanlightly.rabbittesttool.topology.model;

import com.jackvanlightly.rabbittesttool.topology.model.actions.ActionListConfig;
import com.rabbitmq.stream.ByteCapacity;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class QueueConfig {
    String group;
    String vhostName;
    QueueType queueType;
    boolean isDownstream;
    int scale;
    List<Property> properties;
    List<BindingConfig> bindings;
    String deadletterExchange;
    ShovelConfig shovelConfig;
    ActionListConfig actionList;
    ByteCapacity retentionSize;
    ByteCapacity segmentSize;

    public QueueConfig() {
        properties = new ArrayList<>();
        bindings = new ArrayList<>();
    }

    public QueueConfig(String group,
                       String vhostName,
                       QueueType queueType,
                       boolean isDownstream,
                       int scale,
                       List<Property> properties,
                       List<BindingConfig> bindings,
                       String deadletterExchange,
                       ShovelConfig shovelConfig,
                       ActionListConfig actionList) {
        this(group, vhostName, queueType, isDownstream, scale,
                properties, bindings, deadletterExchange, shovelConfig, actionList,
                ByteCapacity.B(0), ByteCapacity.B(0));
    }

    public QueueConfig(String group,
                       String vhostName,
                       QueueType queueType,
                       boolean isDownstream,
                       int scale, List<Property> properties,
                       List<BindingConfig> bindings,
                       String deadletterExchange,
                       ShovelConfig shovelConfig,
                       ActionListConfig actionList,
                       ByteCapacity retentionBytes,
                       ByteCapacity segmentSize) {
        this.group = group;
        this.vhostName = vhostName;
        this.queueType = queueType;
        this.isDownstream = isDownstream;
        this.scale = scale;
        this.properties = properties;
        this.bindings = bindings;
        this.deadletterExchange = deadletterExchange;
        this.shovelConfig = shovelConfig;
        this.actionList = actionList;
        this.retentionSize = retentionBytes;
        this.segmentSize = segmentSize;
    }

    public QueueConfig clone(int scaleNumber) {
        List<BindingConfig> newBindings = new ArrayList<>();
        for(BindingConfig bc : bindings) {
            newBindings.add(bc.clone(scaleNumber));
        }

        ShovelConfig sc = null;
        if(this.shovelConfig != null)
            sc = this.shovelConfig.clone(scaleNumber);

        String dlx = null;
        if(this.deadletterExchange != null)
            dlx = this.deadletterExchange + VirtualHost.getScaleSuffix(scaleNumber);

        return new QueueConfig(
                this.group + VirtualHost.getScaleSuffix(scaleNumber),
                this.vhostName,
                this.queueType,
                this.isDownstream,
                this.scale,
                this.properties,
                newBindings,
                dlx,
                sc,
                this.actionList,
                this.retentionSize,
                this.segmentSize);
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

    public QueueType getQueueType() {
        return queueType;
    }

    public void setQueueType(QueueType queueType) {
        this.queueType = queueType;
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

    public String getDeadletterExchange() {
        return deadletterExchange;
    }

    public void setDeadletterExchange(String deadletterExchange) {
        this.deadletterExchange = deadletterExchange;
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

    public void setRetentionSize(ByteCapacity retentionSize) {
        this.retentionSize = retentionSize;
    }

    public void setSegmentSize(ByteCapacity segmentSize) {
        this.segmentSize = segmentSize;
    }

    public ByteCapacity getRetentionSize() {
        return retentionSize;
    }

    public ByteCapacity getSegmentSize() {
        return segmentSize;
    }
}
