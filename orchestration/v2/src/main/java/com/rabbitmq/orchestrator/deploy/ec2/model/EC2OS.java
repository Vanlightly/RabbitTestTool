package com.rabbitmq.orchestrator.deploy.ec2.model;

public class EC2OS {
    int fileDescriptorLimit;
    String filesystem;

    public EC2OS() {
    }

    public EC2OS(int fileDescriptorLimit, String filesystem) {
        this.fileDescriptorLimit = fileDescriptorLimit;
        this.filesystem = filesystem;
    }

    public int getFileDescriptorLimit() {
        return fileDescriptorLimit;
    }

    public void setFileDescriptorLimit(int fileDescriptorLimit) {
        this.fileDescriptorLimit = fileDescriptorLimit;
    }

    public String getFilesystem() {
        return filesystem;
    }

    public void setFilesystem(String filesystem) {
        this.filesystem = filesystem;
    }
}
