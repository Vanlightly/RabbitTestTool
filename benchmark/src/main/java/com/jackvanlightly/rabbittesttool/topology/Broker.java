package com.jackvanlightly.rabbittesttool.topology;

public class Broker {
    private String ip;
    private String port;
    private String nodeName;

    public Broker(String ip, String port, String nodeName) {
        this.ip = ip;
        this.port = port;
        this.nodeName = nodeName;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }
}
