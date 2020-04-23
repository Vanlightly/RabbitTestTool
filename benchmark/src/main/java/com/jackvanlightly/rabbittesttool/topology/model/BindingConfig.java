package com.jackvanlightly.rabbittesttool.topology.model;

import java.util.*;
import java.util.stream.Collectors;

public class BindingConfig {
    private String from;
    private List<String> bindingKeys;
    private int bindingKeysPerQueue;
    private List<Property> properties;

    public BindingConfig() {
        properties = new ArrayList<>();
    }

    public BindingConfig(String from,
                         List<String> bindingKeys,
                         int bindingKeysPerQueue,
                         List<Property> properties) {
        this.from = from;
        this.bindingKeys = bindingKeys;
        this.bindingKeysPerQueue = bindingKeysPerQueue;
        this.properties = properties;
    }

    public BindingConfig clone(int scaleNumber) {
        return new BindingConfig(this.from + VirtualHost.getScaleSuffix(scaleNumber),
                this.bindingKeys,
                this.bindingKeysPerQueue,
                this.properties);
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
    }

    public List<String> getBindingKeys(int ordinal, int prefixSize, int fanoutDegree) {
        Set<String> bkList = new HashSet<>();
        int counter = ordinal-1;
        while(counter < bindingKeys.size()) {
            for(int degree=0; degree<fanoutDegree; degree++) {
                int logicalIndex =  counter+degree;
                int physicalIndex =  logicalIndex < prefixSize ? logicalIndex : logicalIndex % prefixSize;

                bkList.add(bindingKeys.get(physicalIndex));
            }
            counter += prefixSize;
        }

        return bkList.stream().sorted(String::compareTo).collect(Collectors.toList());
    }

    public void setBindingKeys(List<String> bindingKeys) {
        this.bindingKeys = bindingKeys;
    }

    public int getBindingKeysPerQueue() {
        return bindingKeysPerQueue;
    }

    public void setBindingKeysPerQueue(int bindingKeysPerQueue) {
        this.bindingKeysPerQueue = bindingKeysPerQueue;
    }
}
