package com.jackvanlightly.rabbittesttool.clients;

import com.jackvanlightly.rabbittesttool.topology.Broker;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionSettings {
    private List<Broker> hosts;
    private String user;
    private String password;
    private String vhost;
    private int managementPort;
    private ConnectToNode connectToNode;
    private boolean noTcpDelay;

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

    public ConnectToNode getConnectToNode() {
        return connectToNode;
    }

    public void setConnectToNode(ConnectToNode connectToNode) {
        this.connectToNode = connectToNode;
    }

    public ConnectionSettings getClone(String vhostName) {
        ConnectionSettings cs = new ConnectionSettings();
        cs.setNoTcpDelay(noTcpDelay);
        cs.setPassword(password);
        cs.setUser(user);
        cs.setVhost(vhostName);
        cs.setManagementPort(managementPort);
        cs.setHosts(hosts);
        cs.setConnectToNode(connectToNode);

        return cs;
    }
}
