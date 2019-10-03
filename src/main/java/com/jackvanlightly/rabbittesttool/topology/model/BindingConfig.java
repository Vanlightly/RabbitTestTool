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
