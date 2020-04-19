package com.jackvanlightly.rabbittesttool.topology.model;

import java.util.ArrayList;
import java.util.List;

public class BindingConfig {
    private String from;
    private String bindingKey;
    private List<Property> properties;

    public BindingConfig() {
        properties = new ArrayList<>();
    }

    public BindingConfig(String from, String bindingKey, List<Property> properties) {
        this.from = from;
        this.bindingKey = bindingKey;
        this.properties = properties;
    }

    public BindingConfig clone(int scaleNumber) {
        return new BindingConfig(this.from + VirtualHost.getScaleSuffix(scaleNumber),
                this.bindingKey,
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

    public String getBindingKey() {
        return bindingKey;
    }

    public void setBindingKey(String bindingKey) {
        this.bindingKey = bindingKey;
    }
}
