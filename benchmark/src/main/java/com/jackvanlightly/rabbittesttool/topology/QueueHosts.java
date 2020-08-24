package com.jackvanlightly.rabbittesttool.topology;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.BrokerConfiguration;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.clients.publishers.StreamPublisherListener;
import com.rabbitmq.stream.impl.Client;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class QueueHosts {
    BenchmarkLogger logger;

    boolean isDownstream = false;
    List<Broker> brokers;
    Map<String, Broker> brokersMap;
    Map<String, Broker> queueHosts;
    Random rand;
    AtomicBoolean isCancelled;
    ReadWriteLock lock = new ReentrantReadWriteLock();
    AtomicInteger currentIndex;
    ConnectionSettings connectionSettings;
    String streamHost;
    int streamPort;

    private TopologyGenerator topologyGenerator;

    public QueueHosts(TopologyGenerator topologyGenerator,
                      ConnectionSettings connectionSettings,
                      String streamHost, int streamPort) {
        this.logger = new BenchmarkLogger("QUEUE_HOSTS");
        this.topologyGenerator = topologyGenerator;
        this.connectionSettings = connectionSettings;
        this.streamHost = streamHost;
        this.streamPort = streamPort;
        this.brokers = new ArrayList<>();
        this.brokersMap = new HashMap<>();
        this.queueHosts = new HashMap<>();
        this.rand = new Random();
        this.isCancelled = new AtomicBoolean();
        this.currentIndex = new AtomicInteger();
    }

    public boolean clusterExists() {
        return !brokers.isEmpty();
    }

    public void monitorQueueHosts(List<String> vhosts, Map<String, List<String>> streamQueues) {
        while(!isCancelled.get()) {
            updateQueueHosts(vhosts);
            updateStreamQueueHosts(streamQueues);
            ClientUtils.waitFor(30000, this.isCancelled);
        }
    }

    public void updateQueueHosts(List<String> vhosts) {
        try {
            for (String vhost : vhosts) {
                JSONArray queues = topologyGenerator.getQueues(vhost, isDownstream, 5);

                for (int i = 0; i < queues.length(); i++) {
                    JSONObject queue = queues.getJSONObject(i);
                    String queueName = queue.getString("name");
                    String nodeName = "";
                    if (queue.has("leader") && !queue.isNull("leader"))
                        nodeName = queue.getString("leader");
                    else if (queue.has("node"))
                        nodeName = queue.getString("node");

                    if (!nodeName.equals("")) {
                        Broker broker = brokersMap.get(nodeName);
                        if(broker == null) {
                            logger.info("Broker could not be determined for queue: " + queueName);
                            continue;
                        }

                        lock.writeLock().lock();
                        try {
                            if(queueHosts.containsKey(queueName)) {
                                String current = queueHosts.get(queueName).getNodeName();
                                if(!current.equals(nodeName))
                                    logger.info("Detected host change for " + queueName + ", was on: " + current + " now on: " + broker.getNodeName());
                            }
                            else {
                                logger.info(queueName + " hosted on: " + broker.getNodeName());
                            }

                            queueHosts.put(queueName, broker);

                        } finally {
                            lock.writeLock().unlock();
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            logger.error("Failed updating queue hosts", e);
        }
    }

    public void updateStreamQueueHosts(Map<String, List<String>> streamQueues) {
        if(streamQueues.isEmpty())
            return;

        try {
            for(String vhost : streamQueues.keySet()) {
                Client client = getClient(vhost);
                try {
                    List<String> queues = streamQueues.get(vhost);

                    String[] streamArr = new String[queues.size()];
                    for (int i = 0; i < queues.size(); i++)
                        streamArr[i] = queues.get(i);

                    Map<String, Client.StreamMetadata> metaData = client.metadata(streamArr);
                    for (Map.Entry<String, Client.StreamMetadata> streamEntry : metaData.entrySet()) {
                        Client.Broker leader = streamEntry.getValue().getLeader();
                        if (leader == null) {
                            logger.info("No leader detected for stream " + streamEntry.getKey());
                        } else {
                            String host = leader.getHost();
                            Optional<Broker> b = brokers.stream()
                                    .filter(x -> x.getNodeName().endsWith(host))
                                    .findFirst();

                            if (b.isPresent()) {
                                Broker broker = b.get();
                                lock.writeLock().lock();
                                try {
                                    if (queueHosts.containsKey(streamEntry.getKey())) {
                                        String current = queueHosts.get(streamEntry.getKey()).getNodeName();
                                        if (!current.equals(b.get().getNodeName()))
                                            logger.info("Detected host change for stream queue " + streamEntry.getKey() + ", was on: " + current + " now on: " + broker.getNodeName());
                                    } else {
                                        logger.info("Stream queue " + streamEntry.getKey() + " hosted on: " + broker.getNodeName());
                                    }

                                    queueHosts.put(streamEntry.getKey(), broker);

                                } finally {
                                    lock.writeLock().unlock();
                                }
                            }
                        }
                    }
                }
                finally {
                    client.close();
                }
            }
        }
        catch (Exception e) {
            logger.error("Failed updating stream queue hosts", e);
        }
    }

    private Client getClient(String vhost) {
        return new Client(new Client.ClientParameters()
                .host(streamHost)
                .port(streamPort)
                .virtualHost(vhost)
                .username(connectionSettings.getUser())
                .password(connectionSettings.getPassword()));
    }

    public void stopMonitoring() {
        this.isCancelled.set(true);
    }

    public void addHosts(BrokerConfiguration brokerConfiguration) {
        for(Broker broker : brokerConfiguration.getHosts()) {
            brokers.add(broker);
            brokersMap.put(broker.getNodeName(), broker);
        }
    }

    public void addDownstreamHosts(BrokerConfiguration brokerConfiguration) {
        for(Broker broker : brokerConfiguration.getDownstreamHosts()) {
            brokers.add(broker);
            brokersMap.put(broker.getNodeName(), broker);
        }

        isDownstream = true;
    }

    public boolean isQueueHost(String queue, Broker broker) {
        lock.readLock().lock();
        try {
            if(queueHosts.isEmpty())
                return false;

            if(queueHosts.containsKey(queue))
                return queueHosts.get(queue).getNodeName().equals(broker.getNodeName());
        }
        finally {
            lock.readLock().unlock();
        }

        return false;
    }

    public Broker getHostRoundRobin() {
        int index = currentIndex.getAndIncrement();
        return brokers.get(index % brokers.size());
    }

    public Broker getRandomHost() {
        if(brokers.size() == 1)
            return brokers.get(0);

        int index = rand.nextInt(brokers.size());
        return brokers.get(index);
    }

    public Broker getHost(String queue) {
        lock.readLock().lock();
        try {
            if(queueHosts.isEmpty())
                return null;

            return queueHosts.get(queue);
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Broker getRandomOtherHost(String queue) {
        if(brokers.size() == 1)
            return brokers.get(0);

        List<Broker> hosts = null;

        lock.readLock().lock();
        try {
            if(queueHosts.isEmpty())
                return null;

            String nodeName = queueHosts.get(queue).getNodeName();
            hosts = brokers.stream()
                    .filter(x -> !x.getNodeName().equals(nodeName))
                    .collect(Collectors.toList());
        }
        finally {
            lock.readLock().unlock();
        }

        return hosts.get(rand.nextInt(hosts.size()));
    }
}
