package com.jackvanlightly.rabbittesttool.topology;

public class Broker {
    String ip;
    String port;
    String nodeName;
    String streamPort;

    public Broker(String ip, String port, String nodeName, String streamPort) {
        this.ip = ip;
        this.port = port;
        this.nodeName = nodeName;
        this.streamPort = streamPort;
    }

    public String getIp() {
        return ip;
    }

    public int getStreamPort() {
        return Integer.valueOf(streamPort);
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
