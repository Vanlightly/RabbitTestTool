package com.rabbitmq.orchestrator.deploy;

import com.rabbitmq.orchestrator.deploy.k8s.model.K8sEngine;
import com.rabbitmq.orchestrator.model.hosts.Host;

public class BaseSystem {
    String name;
    Host host;
    K8sEngine k8sEngine;

    public BaseSystem(String name, Host host, K8sEngine k8sEngine) {
        this.name = name;
        this.host = host;
        this.k8sEngine = k8sEngine;
    }

    public String getName() {
        return name;
    }

    public Host getHost() {
        return host;
    }

    public K8sEngine getK8sEngine() {
        return k8sEngine;
    }

    public String getK8sEngineStr() {
        return String.valueOf(k8sEngine).toLowerCase();
    }
}
