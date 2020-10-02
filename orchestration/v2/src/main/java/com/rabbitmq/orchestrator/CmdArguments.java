package com.rabbitmq.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

public class CmdArguments {

    private static final Logger LOGGER = LoggerFactory.getLogger(CmdArguments.class);

    public static void printHelp(PrintStream printStream) {
        printStream.println("--playlist-file        The path to the playlist to be run");
    }

    private Map<String,String> arguments;
    private boolean helpRequested;

    public CmdArguments(String[] args) {
        List<String> argsList = new ArrayList<>();
        for(String arg : args) {
            for(String a1 : arg.split(" ")) {
                argsList.add(a1);
            }
        }

        int startPos = 0;
        this.arguments = new HashMap<>();
        if(argsList.size() % 2 != 0) {
            if(argsList.get(0).equals("help")) {
                helpRequested = true;
                startPos = 1;
            }
            else {
                StringBuilder sb = new StringBuilder();
                for(String arg : argsList)
                    sb.append(arg + " ");
                LOGGER.info(sb.toString());
                throw new CmdArgumentException("You have supplied an odd number of arguments. Arguments are expected in --key value format. Use the help argument for more information.");
            }
        }

        // load any config file arguments first
//        for(int i=startPos; i<argsList.size()-1; i+=2) {
//            if (argsList.get(i).equals("--config-file")) {
//                JSONObject configJson = loadJson(argsList.get(i + 1));
//                for (String key : configJson.keySet()) {
//                    arguments.put("--" + key, configJson.getString(key));
//                }
//            }
//        }

        // load arguments from command line, overwriting any that were already loaded from file
        for(int i=startPos; i<argsList.size()-1; i+=2) {
            if (!argsList.get(i).equals("--config-file"))
                arguments.put(argsList.get(i), argsList.get(i + 1));
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

        throw new CmdArgumentException("No such argument: " + key);
    }

    public String getStr(String key, String defaultValue) {
        return arguments.getOrDefault(key, defaultValue);
    }

    public List<String> getListStr(String key) {
        if(arguments.containsKey(key))
            return Arrays.asList(arguments.get(key).split(","));

        throw new CmdArgumentException("No such argument: " + key);
    }

    public List<String> getListStr(String key, String defaultValue) {
        return Arrays.asList(arguments.getOrDefault(key, defaultValue).split(","));
    }

    public Integer getInt(String key) {
        if(arguments.containsKey(key))
            return Integer.valueOf(arguments.get(key));

        throw new CmdArgumentException("No such argument: " + key);
    }

    public Integer getInt(String key, Integer defaultValue) {
        if(arguments.containsKey(key))
            return Integer.valueOf(arguments.get(key));

        return defaultValue;
    }

    public Boolean getBoolean(String key) {
        if(arguments.containsKey(key))
            return Boolean.valueOf(arguments.get(key));

        throw new CmdArgumentException("No such argument: " + key);
    }

    public Boolean getBoolean(String key, Boolean defaultValue) {
        if(arguments.containsKey(key))
            return Boolean.valueOf(arguments.get(key));

        return defaultValue;
    }

    public void printArguments() {
        LOGGER.info("Arguments: \n" + getArgsStr("\n"));
    }

    public String getArgsStr(String separator) {
        StringBuilder sb = new StringBuilder();
        for(Map.Entry<String,String> entry : arguments.entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(Collectors.toList())) {
            if(entry.getKey().contains("pwd") || entry.getKey().contains("password"))
                sb.append(entry.getKey() + " = *****" + separator);
            else
                sb.append(entry.getKey() + " = " + entry.getValue() + separator);
        }

        return sb.toString();
    }
}

