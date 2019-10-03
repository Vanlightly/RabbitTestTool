package com.jackvanlightly.rabbittesttool;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CmdArguments {

    private static final Logger LOGGER = LoggerFactory.getLogger(CmdArguments.class);

    public static void printTopLevelHelp(PrintStream printStream) {
        printStream.println("There are four modes:");
        printStream.println("--mode local-benchmark         When running a single instance of the benchmark locally");
        printStream.println("--mode logged-benchmark        When deploying and running one or more benchmarks with all data sent to Postgres");
        printStream.println("--mode model                   WIP. Model driven property based test");
        printStream.println("--mode comparison              Compares the results of two logged benchmark configurations (by reading data from Postgres)");
        printStream.println("To see arguments, for mode local-benchmark as an example, use the args: help --mode local-benchmark");
    }

    public static void printLocalBenchmarkHelp(PrintStream printStream) {
        printStream.println("Local benchmark arguments are categorized as either:");
        printStream.println(" + Determining behaviour of a benchmark run (mandatory)");
        printStream.println(" + Broker connection details (mandatory)");
        printStream.println(" + InfluxDB connection details (optional). Sending of metrics to InfluxDB is not required but can add a lot of value.");
        printStream.println(" + Used in logging and tagging of results and metrics (mandatory only if including InfluxDB)");
        printStream.println("");
        printStream.println("Arguments can be passed via the command line and/or a JSON configuration file. Command line arguments take precedence over configuration file arguments when they exist in both.");
        printStream.println("To include a JSON configuration file, use the arg:");
        printStream.println("--config-file      /path/to/config/file");
        printStream.println("The fields in the JSON file must be the same as the command line but without --. For example: {\"broker-host\": \"localhost\"}");
        printStream.println("");
        printStream.println("All arguments listed below are in the command line format: --arg-name");
        printStream.println("");
        printStream.println("Behaviour arguments:");
        printStream.println("--topology         The absolute filepath to the topology file of the benchmark");
        printStream.println("");
        printStream.println("Broker connection:");
        printStream.println("--broker-host      The broker hostname or IP");
        printStream.println("--broker-mgmt-port The broker management plugin port");
        printStream.println("--broker-port      The broker amqp port");
        printStream.println("--broker-user      The broker user");
        printStream.println("--broker-password  The broker password");
        printStream.println("--broker-vhost     The broker virtual host");
        printStream.println("");
        printStream.println("InfluxDB connection (optional):");
        printStream.println("--metrics-influx-uri       The url of the influxdb server");
        printStream.println("--metrics-influx-user      The influxdb user");
        printStream.println("--metrics-influx-password  The influxdb password");
        printStream.println("--metrics-influx-database  The influxdb database");
        printStream.println("--metrics-influx-interval  The interval (seconds) to post metrics to influx. 10 is a good value.");
        printStream.println("");
        printStream.println("Logging and tagging arguments (Mandatory when including InfluxDB):");
        printStream.println("--technology       The broker under test, for example: rabbitmq");
        printStream.println("--version          The broker version, for example: 3.7.15");
        printStream.println("--node             The numerical suffix of the logged broker, for example, if the target broker is rabbitmq1, then this arg value would be: 1");
        printStream.println("--instance         Details of the broker server. If in the cloud, the instance type, like c5.large");
    }

    public static void printLoggedBenchmarkHelp(PrintStream printStream) {
        printStream.println("Logged benchmark arguments are categorized as either:");
        printStream.println(" + Determining behaviour of a benchmark run (mandatory)");
        printStream.println(" + Broker connection details (mandatory)");
        printStream.println(" + Postgres connection details (mandatory). Storing results in Postgres allows for keeping track of previous runs and running statistical comparisons of different runs.");
        printStream.println(" + InfluxDB connection details (optional). Sending of metrics to InfluxDB is not required but can add a lot of value.");
        printStream.println(" + Used in logging and tagging of results and metrics (mandatory)");
        printStream.println("");
        printStream.println("Arguments can be passed via the command line and/or a JSON configuration file. Command line arguments take precedence over configuration file arguments when they exist in both.");
        printStream.println("To include a JSON configuration file, use the arg:");
        printStream.println("--config-file      /path/to/config/file");
        printStream.println("The fields in the JSON file must be the same as the command line but without --. For example: {\"broker-host\": \"localhost\"}");
        printStream.println("");
        printStream.println("All arguments listed below are in the command line format: --arg-name");
        printStream.println("");
        printStream.println("Behaviour arguments:");
        printStream.println("--topology         The absolute filepath to the topology file of the benchmark");
        printStream.println("");
        printStream.println("Broker connection:");
        printStream.println("--broker-host      The broker hostname or IP");
        printStream.println("--broker-mgmt-port The broker management plugin port");
        printStream.println("--broker-port      The broker amqp port");
        printStream.println("--broker-user      The broker user");
        printStream.println("--broker-password  The broker password");
        printStream.println("--broker-vhost     The broker virtual host");
        printStream.println("");
        printStream.println("Postgres connection:");
        printStream.println("--postgres-jdbc-url    The postgres connection url");
        printStream.println("--postgres-user        The postgres user");
        printStream.println("--postgres-pwd         The postgres password");
        printStream.println("");
        printStream.println("InfluxDB connection (optional):");
        printStream.println("--metrics-influx-uri       The url of the influxdb server");
        printStream.println("--metrics-influx-user      The influxdb user");
        printStream.println("--metrics-influx-password  The influxdb password");
        printStream.println("--metrics-influx-database  The influxdb database");
        printStream.println("--metrics-influx-interval  The interval (seconds) to post metrics to influx. 10 is a good value.");
        printStream.println("");
        printStream.println("Logging and tagging arguments:");
        printStream.println("--run-id           The identifier of the run that this benchmark is a part of. A single run can include multiple benchmarks.");
        printStream.println("--config-tag       A single run can include multiple configurations. This arg is used to group the data of parallel instances of the same benchmark during Comparison mode.");
        printStream.println("--run-tag          Differentiates results when running multiple identical benchmarks in parallel.");
        printStream.println("--technology       The broker under test, for example: rabbitmq");
        printStream.println("--version          The broker version, for example: 3.7.15");
        printStream.println("--node             The numerical suffix of the logged broker, for example, if the target broker is rabbitmq1, then this arg value would be: 1");
        printStream.println("--instance         Details of the broker server. If in the cloud, the instance type, like c5.large");
        printStream.println("--volume           Details of the broker disk drive. If in the cloud, for example gp2, io1 etc.");
        printStream.println("--filesystem       Filesystem of the broker: XFS, ext4 etc");
        printStream.println("--hosting          Local, AWS, GCP etc");
        printStream.println("--tenancy          In AWS dedicated or default for example");
        printStream.println("--core-count       The number of cores available to the broker");
        printStream.println("--threads-per-core The number of threads per core: 1 or 2");
    }

    public static void printComparisonHelp(PrintStream printStream) {

        printStream.println("--report-dir           The path to the directory where the report files should be written to");
        printStream.println("--run-id1              The run-id of the first configuration");
        printStream.println("--config-tag1          The config-tag of the first configuration");
        printStream.println("--technology1          The broker technology of the first configuration, for example: rabbitmq");
        printStream.println("--version1             The broker version of the first configuration, for example: 3.7.15");
        printStream.println("--run-id2              The run-id of the second configuration");
        printStream.println("--config-tag2          The config-tag of the second configuration");
        printStream.println("--technology2          The broker technology of the second configuration, for example: rabbitmq");
        printStream.println("--version2             The broker version of the second configuration, for example: 3.7.15");
        printStream.println("--postgres-jdbc-url    The postgres connection url");
        printStream.println("--postgres-user        The postgres user");
        printStream.println("--postgres-pwd         The postgres password");
    }

    public static void printModelHelp(PrintStream printStream) {

        printStream.println("TODO - WIP");
    }

    private Map<String,String> arguments;
    private boolean helpRequested;

    public CmdArguments(String[] args) {

        int startPos = 0;
        this.arguments = new HashMap<>();
        if(args.length % 2 != 0) {
            if(args[0] == "help") {
                startPos = 1;
                helpRequested = true;
            }
            else
                throw new RuntimeException("You have supplied an odd number of arguments. Arguments are expected in --key value format. Use the help argument for more information.");
        }

        // load any config file arguments first
        for(int i=startPos; i<args.length-1; i+=2) {
            if (args[i].equals("--config-file")) {
                JSONObject configJson = loadJson(args[i + 1]);
                for (String key : configJson.keySet()) {
                    arguments.put("--" + key, configJson.getString(key));
                }
            }
        }

        // load arguments from command line, overwriting any that were already loaded from file
        for(int i=startPos; i<args.length-1; i+=2) {
            if (!args[i].equals("--config-file"))
                arguments.put(args[i], args[i + 1]);
        }
    }

    public boolean hasRequestedHelp() {
        return helpRequested;
    }

    public boolean hasKey(String key) {
        return arguments.containsKey(key);
    }

    public String getStr(String key) {
        if(arguments.containsKey(key))
            return arguments.get(key);

        throw new RuntimeException("No such argument" + key);
    }

    public String getStr(String key, String defaultValue) {
        return arguments.getOrDefault(key, defaultValue);
    }

    public List<String> getListStr(String key) {
        if(arguments.containsKey(key))
            return Arrays.asList(arguments.get(key).split(","));

        throw new RuntimeException("No such argument" + key);
    }

    public List<String> getListStr(String key, String defaultValue) {
        return Arrays.asList(arguments.getOrDefault(key, defaultValue).split(","));
    }

    public Integer getInt(String key) {
        if(arguments.containsKey(key))
            return Integer.valueOf(arguments.get(key));

        throw new RuntimeException("No such argument" + key);
    }

    public Integer getInt(String key, Integer defaultValue) {
        if(arguments.containsKey(key))
            return Integer.valueOf(arguments.get(key));

        return defaultValue;
    }

    public Boolean getBoolean(String key) {
        if(arguments.containsKey(key))
            return Boolean.valueOf(arguments.get(key));

        throw new RuntimeException("No such argument" + key);
    }

    public Boolean getBoolean(String key, Boolean defaultValue) {
        if(arguments.containsKey(key))
            return Boolean.valueOf(arguments.get(key));

        return defaultValue;
    }

    public void printArguments() {
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<String,String> entry : arguments.entrySet()) {
            if(entry.getKey().contains("pwd") || entry.getKey().contains("password"))
                sb.append(entry.getKey() + " = *****\n");
            else
                sb.append(entry.getKey() + " = " + entry.getValue() + "\n");
        }

        LOGGER.info("Arguments: \n" + sb.toString());
    }

    public boolean hasRegisterStore() {
        return hasKey("--postgres-jdbc-url");
    }

    public boolean hasMetrics() {
        return hasKey("--metrics-influx-uri");
    }

    private JSONObject loadJson(String configFilePath) {
        try {

            File f = new File(configFilePath);
            if (f.exists()) {
                try(InputStream is = new FileInputStream(configFilePath)) {
                    String jsonTxt = IOUtils.toString(is, "UTF-8");

                    return new JSONObject(jsonTxt);
                }
            }
            else {
                throw new RuntimeException("Could not find the requested config file");
            }
        }
        catch(Exception e) {
            throw new RuntimeException("Failed loading the requested config file", e);
        }
    }

}
