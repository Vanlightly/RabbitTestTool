package com.jackvanlightly.rabbittesttool.topology.model;

import java.util.ArrayList;
import java.util.List;

public class ExchangeConfig {
    private String name;
    private String vhostName;
    private ExchangeType exchangeType;
    private List<BindingConfig> bindings;
    private boolean isDownstream;
    private ShovelConfig shovelConfig;

    public ExchangeConfig() {
        bindings = new ArrayList<>();
    }

    public ExchangeConfig(String name, String vhostName, ExchangeType exchangeType, List<BindingConfig> bindings, boolean isDownstream, ShovelConfig shovelConfig) {
        this.name = name;
        this.vhostName = vhostName;
        this.exchangeType = exchangeType;
        this.bindings = bindings;
        this.isDownstream = isDownstream;
        this.shovelConfig = shovelConfig;
    }

    public ExchangeConfig clone(int scaleNumber) {
        List<BindingConfig> newBindings = new ArrayList<>();
        for(BindingConfig bc : bindings) {
            newBindings.add(bc.clone(scaleNumber));
        }

        ShovelConfig sc = null;
        if(this.shovelConfig != null)
            sc = this.shovelConfig.clone(scaleNumber);

        return new ExchangeConfig(this.name + VirtualHost.getScaleSuffix(scaleNumber),
                this.vhostName,
                this.exchangeType,
                newBindings,
                this.isDownstream,
                sc);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVhostName() {
        return vhostName;
    }

    public void setVhostName(String vhostName) {
        this.vhostName = vhostName;
    }

    public ExchangeType getExchangeType() {
        return exchangeType;
    }

    public String getExchangeTypeName() {
        switch(exchangeType) {
            case Fanout: return "fanout";
            case Headers: return "headers";
            case Direct: return "direct";
            case Topic: return "topic";
            case ConsistentHash: return "x-consistent-hash";
            case ModulusHash: return "x-modulus-hash";
            default:
                throw new RuntimeException("Unsupported exchange type: " + exchangeType);
        }
    }

    public void setExchangeType(ExchangeType exchangeType) {
        this.exchangeType = exchangeType;
    }

    public List<BindingConfig> getBindings() {
        return bindings;
    }

    public void setBindings(List<BindingConfig> bindings) {
        this.bindings = bindings;
    }

    public boolean isDownstream() {
        return isDownstream;
    }

    public void setDownstream(boolean downstream) {
        isDownstream = downstream;
    }

    public ShovelConfig getShovelConfig() {
        return shovelConfig;
    }

    public void setShovelConfig(ShovelConfig shovelConfig) {
        this.shovelConfig = shovelConfig;
    }
}
