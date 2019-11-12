package com.jackvanlightly.rabbittesttool.clients;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ConnectionSettings {
    private List<String> hosts;
    private String user;
    private String password;
    private String vhost;
    private int managementPort;
    private boolean tryConnectToLocalBroker;
    private boolean noTcpDelay;
    private static AtomicInteger currentHost = new AtomicInteger(0);

    public List<String> getHosts() {
        return hosts;
    }

    public void setHosts(List<String> hosts) {
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

    public String getNextHostAndPort() {
        int index = currentHost.addAndGet(1) % hosts.size();
        return hosts.get(index);
    }

    public String getHostAndPort(int index) {
        return hosts.get(index);
    }

    public String getHostOnly() {
        int index = currentHost.addAndGet(1) % hosts.size();
        return hosts.get(index).split(":")[0];
    }

    public String getHostOnly(int index) {
        return hosts.get(index).split(":")[0];
    }

    public boolean isTryConnectToLocalBroker() {
        return tryConnectToLocalBroker;
    }

    public void setTryConnectToLocalBroker(boolean tryConnectToLocalBroker) {
        this.tryConnectToLocalBroker = tryConnectToLocalBroker;
    }

    public ConnectionSettings getClone(String vhostName) {
        ConnectionSettings cs = new ConnectionSettings();
        cs.setNoTcpDelay(noTcpDelay);
        cs.setPassword(password);
        cs.setUser(user);
        cs.setVhost(vhostName);
        cs.setManagementPort(managementPort);
        cs.setHosts(hosts);
        cs.setTryConnectToLocalBroker(tryConnectToLocalBroker);

        return cs;
    }
}
