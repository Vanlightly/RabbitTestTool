package com.rabbitmq.orchestrator.model;

import com.rabbitmq.orchestrator.model.actions.BrokerAction;
import com.rabbitmq.orchestrator.model.clients.ClientConfiguration;
import com.rabbitmq.orchestrator.model.clients.LoadGenConfiguration;
import com.rabbitmq.orchestrator.run.RabbitMQConfiguration;

public class Workload {
    RabbitMQConfiguration brokerConfiguration;
    RabbitLoad mainLoad;
    RabbitLoad backgroundLoad;
    BrokerAction brokerAction;
    ClientConfiguration clientConfiguration;
    LoadGenConfiguration loadgenConfiguration;
    //List<String> overrideBrokerHosts;

    public Workload(RabbitMQConfiguration brokerConfiguration,
                    RabbitLoad mainLoad,
                    RabbitLoad backgroundLoad,
                    BrokerAction brokerAction,
                    ClientConfiguration clientConfiguration,
                    LoadGenConfiguration loadgenConfiguration) {
        this.brokerConfiguration = brokerConfiguration;
        this.mainLoad = mainLoad;
        this.backgroundLoad = backgroundLoad;
        this.brokerAction = brokerAction;
        this.clientConfiguration = clientConfiguration;
        this.loadgenConfiguration = loadgenConfiguration;
    }

    public RabbitMQConfiguration getBrokerConfiguration() {
        return brokerConfiguration;
    }

    public RabbitLoad getMainLoad() {
        return mainLoad;
    }

    public RabbitLoad getBackgroundLoad() {
        return backgroundLoad;
    }

    public BrokerAction getBrokerAction() {
        return brokerAction;
    }

    public ClientConfiguration getClientConfiguration() {
        return clientConfiguration;
    }

    public LoadGenConfiguration getLoadgenConfiguration() {
        return loadgenConfiguration;
    }

    public boolean hasConfigurationChanges() {
        return brokerConfiguration != null;
    }

    public boolean hasBackgroundLoad() {
        return backgroundLoad != null;
    }

//    public List<String> getOverrideBrokerHosts() {
//        return overrideBrokerHosts;
//    }
//
//    public String getOverrideBrokerHostsStr() {
//        if(overrideBrokerHosts == null)
//            return "";
//
//        return String.join(",", overrideBrokerHosts);
//    }

}
