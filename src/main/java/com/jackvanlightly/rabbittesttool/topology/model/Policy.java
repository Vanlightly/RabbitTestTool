package com.jackvanlightly.rabbittesttool.topology.model;

import java.util.List;

public class Policy {
    private String name;
    private String pattern;
    private String applyTo;
    private int priority;
    private List<Property> properties;

    public Policy(String name, String pattern, String applyTo, int priority, List<Property> properties) {
        this.name = name;
        this.pattern = pattern;
        this.applyTo = applyTo;
        this.priority = priority;
        this.properties = properties;
    }

    public String getName() {
        return name;
    }

    public String getPattern() {
        return pattern;
    }

    public String getApplyTo() {
        return applyTo;
    }

    public int getPriority() {
        return priority;
    }

    public List<Property> getProperties() {
        return properties;
    }
}
