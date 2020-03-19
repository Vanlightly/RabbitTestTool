package com.jackvanlightly.rabbittesttool.clients;

import com.jackvanlightly.rabbittesttool.topology.Broker;

import java.util.List;

public class ConnectionSettings {
    private List<Broker> hosts;
    private String user;
    private String password;
    private String vhost;
    private int managementPort;
    private ConnectToNode publisherConnectToNode;
    private ConnectToNode consumerConnectToNode;
    private boolean noTcpDelay;
    private boolean isDownstream;

    public List<Broker> getHosts() {
        return hosts;
    }

    public void setHosts(List<Broker> hosts) {
        this.hosts = hosts;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVhost() {
        return vhost;
    }

    public void setVhost(String vhost) {
        this.vhost = vhost;
    }

    public int getManagementPort() {
        return managementPort;
    }

    public void setManagementPort(int managementPort) {
        this.managementPort = managementPort;
    }

    public boolean isNoTcpDelay() {
        return noTcpDelay;
    }

    public void setNoTcpDelay(boolean noTcpDelay) {
        this.noTcpDelay = noTcpDelay;
    }

    public ConnectToNode getPublisherConnectToNode() {
        return publisherConnectToNode;
    }

    public void setPublisherConnectToNode(ConnectToNode publisherConnectToNode) {
        this.publisherConnectToNode = publisherConnectToNode;
    }

    public ConnectToNode getConsumerConnectToNode() {
        return consumerConnectToNode;
    }

    public void setConsumerConnectToNode(ConnectToNode consumerConnectToNode) {
        this.consumerConnectToNode = consumerConnectToNode;
    }

    public boolean isDownstream() {
        return isDownstream;
    }

    public void setDownstream(boolean downstream) {
        isDownstream = downstream;
    }

    public ConnectionSettings getClone(String vhostName) {
        ConnectionSettings cs = new ConnectionSettings();
        cs.setNoTcpDelay(noTcpDelay);
        cs.setPassword(password);
        cs.setUser(user);
        cs.setVhost(vhostName);
        cs.setManagementPort(managementPort);
        cs.setHosts(hosts);
        cs.setPublisherConnectToNode(publisherConnectToNode);
        cs.setConsumerConnectToNode(consumerConnectToNode);
        cs.setDownstream(isDownstream);

        return cs;
    }
}
