package com.rabbitmq.orchestrator;

import com.rabbitmq.orchestrator.deploy.Waiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class ProcessExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger("ProcessExecutor");

    public void runProcess(File scriptDir,
                           List<String> args,
                           String logPrefix,
                           String systemName,
                           AtomicBoolean isCancelled,
                           Set<String> failedSystems) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder()
                    .directory(scriptDir)
                    .inheritIO()
                    .command(args);
            Process p = processBuilder.start();

            while (!isCancelled.get() && failedSystems.isEmpty() && p.isAlive()) {
                Waiter.waitMs(1000, isCancelled);
            }

            if (p.isAlive()) {
                p.destroyForcibly();
            } else {
                if (p.exitValue() == 0) {
                    LOGGER.info(logPrefix + " has completed");
                } else {
                    LOGGER.error(logPrefix + " has a non-zero exit code: " + p.exitValue());
                    failedSystems.add(systemName);
                }
            }
        } catch (IOException e) {
            LOGGER.error(logPrefix + " has failed. ", e);
            failedSystems.add(systemName);
        }
    }

    public boolean runProcess(File scriptDir,
                              List<String> args,
                              String logPrefix,
                              AtomicBoolean isCancelled,
                              Set<String> failedSystems) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder()
                    .directory(scriptDir)
                    .inheritIO()
                    .command(args);
            Process p = processBuilder.start();

            while(!isCancelled.get() && failedSystems.isEmpty() && p.isAlive()) {
                Waiter.waitMs(1000, isCancelled);
            }

            if(p.isAlive()) {
                p.destroyForcibly();
                return false;
            }
            else {
                if(p.exitValue() == 0) {
                    LOGGER.info(logPrefix + " has completed");
                    return true;
                }
                else {
                    LOGGER.error(logPrefix + " has a non-zero exit code: " + p.exitValue());
                    return false;
                }
            }
        } catch(IOException e) {
            LOGGER.error(logPrefix + " has failed. ", e);
            return false;
        }
    }

    public String readFromProcess(File scriptDir,
                                  List<String> args,
                                  String logPrefix,
                                  AtomicBoolean isCancelled,
                                  Set<String> failedSystems) {
        String result;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder()
                    .directory(scriptDir)
                    .inheritIO()
                    .command(args);
            Process p = processBuilder.start();

            final BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));

            StringJoiner sj = new StringJoiner(System.getProperty("line.separator"));
            reader.lines().iterator().forEachRemaining(sj::add);
            result = sj.toString();

            while(!isCancelled.get() && failedSystems.isEmpty() && p.isAlive()) {
                Waiter.waitMs(1000, isCancelled);
            }

            if(p.isAlive()) {
                p.destroyForcibly();
                return "";
            }
            else {
                if(p.exitValue() == 0) {
                    LOGGER.info(logPrefix + " has completed");
                    return result;
                }
                else {
                    LOGGER.error(logPrefix + " has a non-zero exit code: " + p.exitValue());
                    return "";
                }
            }
        } catch(IOException e) {
            LOGGER.error(logPrefix + " has failed. ", e);
            return "";
        }
    }

    public String createFile(List<String> lines, String fileExtension) {
        String path = "";

        try {
            if(!fileExtension.contains("."))
                fileExtension = "." + fileExtension;

            path = "/tmp/" + UUID.randomUUID() + fileExtension;
            FileWriter writer = new FileWriter(path);
            for (String str : lines) {
                writer.write(str + System.lineSeparator());
            }
            writer.close();
            return path;
        } catch(IOException e) {
            throw new RuntimeException("Could not create file: " + path);
        }
    }

    public String createFile(String content, String fileExtension) {
        String path = "";

        try {
            if(!fileExtension.contains("."))
                fileExtension = "." + fileExtension;

            path = "/tmp/" + UUID.randomUUID() + fileExtension;
            FileWriter writer = new FileWriter(path);
            writer.write(content);
            writer.close();
            return path;
        } catch(IOException e) {
            throw new RuntimeException("Could not create file: " + path);
        }
    }
}
