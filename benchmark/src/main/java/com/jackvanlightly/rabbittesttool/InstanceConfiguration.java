package com.jackvanlightly.rabbittesttool;

import javax.xml.bind.DatatypeConverter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.util.UUID;

public class InstanceConfiguration {
    private String instanceType;
    private String volume;
    private String fileSystem;
    private String tenancy;
    private String hosting;
    private short coreCount;
    private short threadsPerCore;
    private String configTag;

    public InstanceConfiguration(String instanceType, String volume, String fileSystem, String tenancy, String hosting, short coreCount, short threadsPerCore) {
        this.instanceType = instanceType;
        this.volume = volume;
        this.fileSystem = fileSystem;
        this.tenancy = tenancy;
        this.hosting = hosting;
        this.coreCount = coreCount;
        this.threadsPerCore = threadsPerCore;
    }

    public String getInstanceType() {
        return instanceType;
    }

    public void setInstanceType(String instanceType) {
        this.instanceType = instanceType;
    }

    public String getVolume() {
        return volume;
    }

    public void setVolume(String volume) {
        this.volume = volume;
    }

    public String getFileSystem() {
        return fileSystem;
    }

    public void setFileSystem(String fileSystem) {
        this.fileSystem = fileSystem;
    }

    public String getTenancy() {
        return tenancy;
    }

    public void setTenancy(String tenancy) {
        this.tenancy = tenancy;
    }

    public String getHosting() {
        return hosting;
    }

    public void setHosting(String hosting) {
        this.hosting = hosting;
    }

    public short getCoreCount() {
        return coreCount;
    }

    public void setCoreCount(short coreCount) {
        this.coreCount = coreCount;
    }

    public short getThreadsPerCore() {
        return threadsPerCore;
    }

    public void setThreadsPerCore(short threadsPerCore) {
        this.threadsPerCore = threadsPerCore;
    }

    public String getConfigTag() {
        return configTag;
    }

    public void setConfigTag(String configTag) {
        this.configTag = configTag;
    }

    public void setConfigTagAsHash() {
        try {
            String input = MessageFormat.format("{0}-{1}-{2}-{3}-{4}-{5}-{7}", instanceType, volume, fileSystem, tenancy, hosting, coreCount, threadsPerCore);
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(input.getBytes());
            byte[] digest = md.digest();
            this.configTag = DatatypeConverter.printHexBinary(digest).toUpperCase();
        }
        catch (NoSuchAlgorithmException e) {
            this.configTag = UUID.randomUUID().toString();
        }
    }
}
