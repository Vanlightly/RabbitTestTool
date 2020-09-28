package com.rabbitmq.orchestrator.meta;

import com.rabbitmq.orchestrator.deploy.k8s.model.K8sEngine;

public class K8sMeta {
    String userOrProject;
    String regionOrZones;
    String k8sVersion;
    String manifestsDir;
    String deployScriptsDir;
    String runScriptsDir;

    public K8sMeta(String userOrProject,
                   String regionOrZones,
                   String k8sVersion,
                   String manifestsDir,
                   String deployScriptsDir,
                   String runScriptsDir) {
        this.userOrProject = userOrProject;
        this.regionOrZones = regionOrZones;
        this.k8sVersion = k8sVersion;
        this.manifestsDir = manifestsDir;
        this.deployScriptsDir = deployScriptsDir;
        this.runScriptsDir = runScriptsDir;
    }

    public String getUserOrProject() {
        return userOrProject;
    }

    public void setUserOrProject(String userOrProject) {
        this.userOrProject = userOrProject;
    }

    public String getRegionOrZones() {
        return regionOrZones;
    }

    public String getFirstZone() {
        return regionOrZones.split(",")[0];
    }

    public void setRegionOrZones(String regionOrZones) {
        this.regionOrZones = regionOrZones;
    }

    public String getK8sVersion() {
        return k8sVersion;
    }

    public void setK8sVersion(String k8sVersion) {
        this.k8sVersion = k8sVersion;
    }

    public String getManifestsDir() {
        return manifestsDir;
    }

    public void setManifestsDir(String manifestsDir) {
        this.manifestsDir = manifestsDir;
    }

    public String getDeployScriptsDir() {
        return deployScriptsDir;
    }

    public void setDeployScriptsDir(String deployScriptsDir) {
        this.deployScriptsDir = deployScriptsDir;
    }

    public String getRunScriptsDir() {
        return runScriptsDir;
    }

    public void setRunScriptsDir(String runScriptsDir) {
        this.runScriptsDir = runScriptsDir;
    }

    public String getKubeClusterName(String systemName, K8sEngine k8sEngine, String runTag) {
        return "benchmarking-" + String.valueOf(k8sEngine).toLowerCase() + "-" + systemName + "-" + runTag;
    }

    public String getRabbitClusterName(String systemName, K8sEngine k8sEngine) {
        return "rmq-" + String.valueOf(k8sEngine).toLowerCase() + "-" + systemName;
    }

    public String getK8sContext(String systemName, K8sEngine k8sEngine, String runTag) {
        if(k8sEngine.equals(K8sEngine.GKE))
            return "gke_" + userOrProject + "_" + getFirstZone() + "_" + getKubeClusterName(systemName, k8sEngine, runTag);

        if(k8sEngine.equals(K8sEngine.EKS))
            return userOrProject + "@" + getKubeClusterName(systemName, k8sEngine, runTag) + "." + regionOrZones + ".eksctl.io";

        throw new UnsupportedOperationException(k8sEngine + " k8s engine not supported");
    }
}
