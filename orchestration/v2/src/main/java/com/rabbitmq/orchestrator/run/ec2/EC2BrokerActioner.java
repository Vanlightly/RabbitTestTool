package com.rabbitmq.orchestrator.run.ec2;

import com.rabbitmq.orchestrator.InvalidInputException;
import com.rabbitmq.orchestrator.ProcessExecutor;
import com.rabbitmq.orchestrator.deploy.BaseSystem;
import com.rabbitmq.orchestrator.deploy.Waiter;
import com.rabbitmq.orchestrator.deploy.ec2.model.EC2System;
import com.rabbitmq.orchestrator.meta.EC2Meta;
import com.rabbitmq.orchestrator.model.ActionType;
import com.rabbitmq.orchestrator.model.actions.ActionTrigger;
import com.rabbitmq.orchestrator.model.actions.BrokerAction;
import com.rabbitmq.orchestrator.model.actions.TrafficControl;
import com.rabbitmq.orchestrator.run.BrokerActioner;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EC2BrokerActioner implements BrokerActioner {
    private static final Logger LOGGER = LoggerFactory.getLogger("EC2_BROKER_ACTIONS");

    EC2Meta ec2Meta;
    ProcessExecutor processExecutor;
    String runTag;
    String username;
    String password;
    File scriptDir;

    public EC2BrokerActioner(EC2Meta ec2Meta,
                             ProcessExecutor processExecutor,
                             String runTag,
                             String username,
                             String password,
                             String scriptDir) {
        this.ec2Meta = ec2Meta;
        this.processExecutor = processExecutor;
        this.runTag = runTag;
        this.username = username;
        this.password = password;
        this.scriptDir = new File(scriptDir);
    }

    public void applyActionsIfAny(BaseSystem system,
                                  BrokerAction brokerAction,
                                  AtomicBoolean isCancelled,
                                  Set<String> failedSystems) {
        if(brokerAction == null || brokerAction.getActionType().equals(ActionType.NONE))
            return;

        EC2System ec2System = (EC2System)system;

        switch(brokerAction.getTrigger()) {
            case SECONDS:
                Waiter.waitMs(brokerAction.getTriggerValue()*1000, isCancelled);
                break;
            case MESSAGES:
                waitForMessageTrigger(ec2System, brokerAction.getTriggerValue(), isCancelled, failedSystems);
                break;
            default:
                throw new InvalidInputException("Trigger type not supported: " + brokerAction.getTrigger());
        }

        if(!failedSystems.isEmpty())
            return;

        switch (brokerAction.getActionType()) {
            case STOP_BROKER:
                stopOrRestartBrokers(ec2System, false, "stop", isCancelled, failedSystems);
                break;
            case RESTART_BROKER:
                stopOrRestartBrokers(ec2System, false, "restart", isCancelled, failedSystems);
                break;
            case RESTART_CLUSTER:
                stopOrRestartBrokers(ec2System, true, "restart", isCancelled, failedSystems);
                break;
            case TRAFFIC_CONTROL:
                applyTrafficControl(ec2System, brokerAction.getTrafficControl(), isCancelled, failedSystems);
                break;
            default:
                throw new InvalidInputException("Action type not supported: " + brokerAction.getActionType());
        }
    }

    public void removeAnyAffects(BaseSystem system,
                                 BrokerAction brokerAction,
                                 AtomicBoolean isCancelled,
                                 Set<String> failedSystems) {
        if(brokerAction == null || brokerAction.getActionType().equals(ActionType.NONE))
            return;

        EC2System ec2System = (EC2System)system;
        switch (brokerAction.getActionType()) {
            case STOP_BROKER:
                stopOrRestartBrokers(ec2System, false, "start", isCancelled, failedSystems);
                break;
            case RESTART_BROKER:
                // no action necessary
                break;
            case RESTART_CLUSTER:
                // no action necessary
                break;
            case TRAFFIC_CONTROL:
                ensureNoTrafficControlOnAllBrokers(ec2System, isCancelled, failedSystems);
                break;
            default:
                throw new InvalidInputException("Action type not supported: " + brokerAction.getActionType());
        }

    }

    private void waitForMessageTrigger(EC2System system,
                                       int limit,
                                       AtomicBoolean isCancelled,
                                       Set<String> failedSystems) {

        int messageTotal = getClusterMessageTotal(system);
        while (messageTotal < limit) {
            try {
                messageTotal = getClusterMessageTotal(system);
            } catch (Exception e) {
                LOGGER.error("Failed trying to ascertain the number of queued messages for system: " + system.getName());
                failedSystems.add(system.getName());
            }

            Waiter.waitMs(5000, isCancelled);
        }
    }

    private boolean stopOrRestartBrokers(EC2System system,
                                        boolean allBrokers,
                                        String action,
                                        AtomicBoolean isCancelled,
                                        Set<String> failedSystems) {
        ExecutorService executorService = Executors.newCachedThreadPool();

        if(allBrokers) {
            for (int nodeNumber : system.getNodeNumbers()) {
                executorService.submit(() -> stopOrRestartBroker(system, nodeNumber, action, isCancelled, failedSystems));
            }
        } else {
            executorService.submit(() -> stopOrRestartBroker(system, system.getFirstNode(false), action, isCancelled, failedSystems));
        }

        executorService.shutdown();

        while(!isCancelled.get() && !executorService.isTerminated() && failedSystems.isEmpty()) {
            Waiter.waitMs(100, isCancelled);
        }

        if(isCancelled.get()) {
            LOGGER.info("Restart cancelled before completed");
            return false;
        } else if(!failedSystems.isEmpty()) {
            LOGGER.error("Broker restarts failed for systems: " + String.join(",", failedSystems));
            return false;
        }

        return true;
    }

    private void stopOrRestartBroker(EC2System system,
                                     int nodeNumber,
                                     String action,
                                     AtomicBoolean isCancelled,
                                     Set<String> failedSystems) {
        String logPrefix = "Performing " + action + " on broker " + nodeNumber + " of system " + system.getName();
        List<String> args = Arrays.asList("bash",
                action + "-broker.sh",
                ec2Meta.getKeyPair(),
                String.valueOf(nodeNumber),
                runTag,
                "rabbitmq");
        processExecutor.runProcess(scriptDir, args, logPrefix, isCancelled, failedSystems);
    }

    private boolean ensureNoTrafficControlOnAllBrokers(EC2System system,
                                                      AtomicBoolean isCancelled,
                                                      Set<String> failedSystems) {
        ExecutorService executorService = Executors.newCachedThreadPool();

        for (int nodeNumber : system.getNodeNumbers()) {
            executorService.submit(() -> ensureNoTrafficControl(system, nodeNumber, isCancelled, failedSystems));
        }

        executorService.shutdown();

        while (!isCancelled.get() && !executorService.isTerminated() && failedSystems.isEmpty()) {
            Waiter.waitMs(100, isCancelled);
        }

        if (isCancelled.get()) {
            LOGGER.info("Traffic control removal cancelled before completed");
            return false;
        } else if (!failedSystems.isEmpty()) {
            LOGGER.error("Traffic control removal failed for systems: " + String.join(",", failedSystems));
            return false;
        }

        return true;
    }

    private void ensureNoTrafficControl(EC2System system,
                                     int nodeNumber,
                                     AtomicBoolean isCancelled,
                                     Set<String> failedSystems) {
        String logPrefix = "Ensuring traffic control removed on broker " + nodeNumber + " of system " + system.getName();
        List<String> args = Arrays.asList("bash",
                "remove-traffic-control.sh",
                ec2Meta.getKeyPair(),
                String.valueOf(nodeNumber),
                runTag,
                "rabbitmq");
        processExecutor.runProcess(scriptDir, args, logPrefix, isCancelled, failedSystems);
    }

    private boolean applyTrafficControl(EC2System system,
                                        TrafficControl trafficControl,
                                        AtomicBoolean isCancelled,
                                        Set<String> failedSystems) {
        ExecutorService executorService = Executors.newCachedThreadPool();

        if(trafficControl.isApplyToAllBrokers()) {
            for (int nodeNumber : system.getNodeNumbers()) {
                executorService.submit(() -> applyTrafficControl(system,
                        system.getFirstNode(false),
                        nodeNumber,
                        trafficControl,
                        isCancelled,
                        failedSystems));
            }
        } else {
            executorService.submit(() -> applyTrafficControl(system,
                    system.getFirstNode(false),
                    system.getFirstNode(false),
                    trafficControl,
                    isCancelled,
                    failedSystems));
        }

        executorService.shutdown();

        while(!isCancelled.get() && !executorService.isTerminated() && failedSystems.isEmpty()) {
            Waiter.waitMs(100, isCancelled);
        }

        if(isCancelled.get()) {
            LOGGER.info("Restart cancelled before completed");
            return false;
        } else if(!failedSystems.isEmpty()) {
            LOGGER.error("Broker restarts failed for systems: " + String.join(",", failedSystems));
            return false;
        }

        return true;
    }

    private void applyTrafficControl(EC2System system,
                                     int firstNodeNumber,
                                     int nodeNumber,
                                     TrafficControl trafficControl,
                                     AtomicBoolean isCancelled,
                                     Set<String> failedSystems) {
        try {
            String logPrefix = "Applying traffic control removed on broker " + nodeNumber + " of system " + system.getName();

            // basically we need to apply rules to the IPs that are not the IP of this node
            int relativeNode = nodeNumber - firstNodeNumber;
            List<String> targetIps = new ArrayList<>();
            int pos = 0;
            for (String ip : system.getMainPrivateIps()) {
                if (pos == relativeNode) {
                    pos++;
                    continue;
                }

                targetIps.add(ip);
                pos++;
            }

            List<String> variables = Arrays.asList(
                    "KEY_PAIR=" + ec2Meta.getKeyPair(),
                    "FIRST_NODE=" + firstNodeNumber,
                    "NODE_NUMBER=" + nodeNumber,
                    "RUN_TAG=" + runTag,
                    "TECHNOLOGY=" + "rabbitmq",
                    "TARGET_IPS=" + String.join(",", targetIps),
                    "CLIENT_DELAY=" + trafficControl.isApplyToClientTraffic(),
                    "DELAY=" + convertToString(trafficControl.getDelayMs()),
                    "DELAY_JITTER=" + convertToString(trafficControl.getDelayJitterMs()),
                    "DELAY_DIST=" + trafficControl.getDelayDistribution(),
                    "BANDWIDTH=" + convertToString(trafficControl.getBandwidth()),
                    "PACKET_LOSS_MODE=" + trafficControl.getPacketLossMode(),
                    "PACKET_LOSS_ARG1=" + trafficControl.getPacketLossArg1(),
                    "PACKET_LOSS_ARG2=" + trafficControl.getPacketLossArg2(),
                    "PACKET_LOSS_ARG3=" + trafficControl.getPacketLossArg3(),
                    "PACKET_LOSS_ARG4=" + trafficControl.getPacketLossArg4());

            String variablesFilePath = processExecutor.createFile(variables, ".vars");

            List<String> args = Arrays.asList("bash",
                    "apply-traffic-control.sh",
                    variablesFilePath);

            processExecutor.runProcess(scriptDir, args, logPrefix, isCancelled, failedSystems);
        }
        catch(Exception e) {
            LOGGER.error("Failed applying traffic control on system " + system.getName(), e);
            failedSystems.add(system.getName());
        }
    }

    private String convertToString(int value) {
        DecimalFormat df = new DecimalFormat("#");
        return df.format(value);
    }

    private int getClusterMessageTotal(EC2System system) {
        String url = "http://" + system.getMainPrivateIps().get(0) + ":15672/api/overview";

        try {
            RequestConfig.Builder requestConfig = RequestConfig.custom();
            requestConfig.setConnectTimeout(60 * 1000);
            requestConfig.setConnectionRequestTimeout(60 * 1000);
            requestConfig.setSocketTimeout(60 * 1000);

            CloseableHttpClient client = HttpClients.createDefault();
            HttpGet httpGet = new HttpGet(url);
            httpGet.addHeader("accepts", "application/json");
            httpGet.setConfig(requestConfig.build());

            UsernamePasswordCredentials creds
                    = new UsernamePasswordCredentials(username, password);
            httpGet.addHeader(new BasicScheme().authenticate(creds, httpGet, null));

            CloseableHttpResponse response = client.execute(httpGet);
            int responseCode = response.getStatusLine().getStatusCode();

            if (responseCode != 200) {
                throw new BrokerActionsException("Received a non success response code executing GET " + url
                        + " Code:" + responseCode
                        + " Response: " + response.toString());
            }

            String json = EntityUtils.toString(response.getEntity(), "UTF-8");
            client.close();

            JSONObject overview = convertToJson(json);
            JSONObject queueTotals = overview.getJSONObject("queue_totals");
            if(queueTotals.has("messages"))
                return queueTotals.getInt("messages");
            else
                return 0;
        }
        catch(Exception e) {
            throw new BrokerActionsException("Could not get current message total.", e);
        }
    }

    private JSONObject convertToJson(String json) {
        for (int fixAttempt = 0; fixAttempt < 1000; fixAttempt++) {
            try {
                return new JSONObject(json);
            } catch (JSONException je) {
                if (je.getMessage().startsWith("Duplicate key")) {
                    //System.out.println("Duplicate key bug!");
                    String pattern = "\\\"(.+)\\\"";
                    Pattern r = Pattern.compile(pattern);
                    Matcher m = r.matcher(je.getMessage());
                    if (m.find()) {
                        String duplicateKey = m.group(1);
                        json = json.replaceFirst(duplicateKey, UUID.randomUUID().toString());
                    } else {
                        throw je;
                    }
                } else {
                    throw je;
                }
            }
        }

        throw new BrokerActionsException("Could not fix JSON returned by management API");
    }
}
