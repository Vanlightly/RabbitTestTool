package com.rabbitmq.orchestrator.deploy;

public class OutputData {
    final String influxSubpath;
    final String influxUser;
    final String influxPassword;
    final String influxUrl;
    final String influxDbName;
    final String postgresUrl;
    final String postgresUser;
    final String postgresPassword;
    final String postgresDatabase;
    final String rabbitmqUsername;
    final String rabbitmqPassword;

    public OutputData(String influxSubpath, String influxUser, String influxPassword, String influxUrl, String influxDbName,
                   String postgresUrl, String postgresUser, String postgresPassword, String postgresDatabase,
                      String rabbitmqUsername, String rabbitmqPassword) {
        this.influxSubpath = influxSubpath;
        this.influxUser = influxUser;
        this.influxPassword = influxPassword;
        this.influxUrl = influxUrl;
        this.influxDbName = influxDbName;
        this.postgresUrl = postgresUrl;
        this.postgresUser = postgresUser;
        this.postgresPassword = postgresPassword;
        this.postgresDatabase = postgresDatabase;
        this.rabbitmqUsername = rabbitmqUsername;
        this.rabbitmqPassword = rabbitmqPassword;
    }

    public String getInfluxSubpath() {
        return influxSubpath;
    }

    public String getInfluxUser() {
        return influxUser;
    }

    public String getInfluxPassword() {
        return influxPassword;
    }

    public String getInfluxUrl() {
        return influxUrl;
    }

    public String getInfluxDbName() {
        return influxDbName;
    }

    public String getPostgresUrl() {
        return postgresUrl;
    }

    public String getPostgresUser() {
        return postgresUser;
    }

    public String getPostgresPassword() {
        return postgresPassword;
    }

    public String getPostgresDatabase() {
        return postgresDatabase;
    }

    public String getRabbitmqUsername() {
        return rabbitmqUsername;
    }

    public String getRabbitmqPassword() {
        return rabbitmqPassword;
    }
}
