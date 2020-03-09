package com.jackvanlightly.rabbittesttool.topology;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.BrokerConfiguration;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
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
    private BenchmarkLogger logger;

    private boolean isDownstream = false;
    private List<Broker> brokers;
    private Map<String, Broker> brokersMap;
    private Map<String, Broker> queueHosts;
    private Random rand;
    private AtomicBoolean isCancelled;
    private ReadWriteLock lock = new ReentrantReadWriteLock();
    private AtomicInteger currentIndex;

    private TopologyGenerator topologyGenerator;

    public QueueHosts(TopologyGenerator topologyGenerator) {
        this.logger = new BenchmarkLogger("QUEUE_HOSTS");
        this.topologyGenerator = topologyGenerator;
        this.brokers = new ArrayList<>();
        this.brokersMap = new HashMap<>();
        this.queueHosts = new HashMap<>();
        this.rand = new Random();
        this.isCancelled = new AtomicBoolean();
        this.currentIndex = new AtomicInteger();
    }

    public void monitorQueueHosts(List<String> vhosts) {
        while(!isCancelled.get()) {
            updateQueueHosts(vhosts);
            ClientUtils.waitFor(30000, this.isCancelled);
        }
    }

    public void updateQueueHosts(List<String> vhosts) {
        try {
            for (String vhost : vhosts) {
                JSONArray queues = topologyGenerator.getQueues(vhost, isDownstream);

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

                        lock.writeLock().lock();
                        try {
                            String queueKey = getQueueKey(vhost, queueName);
                            if(queueHosts.containsKey(queueKey)) {
                                String current = queueHosts.get(queueKey).getNodeName();
                                if(!current.equals(nodeName))
                                    logger.info("Detected host change for " + queueKey + ", was on: " + current + " now on: " + broker.getNodeName());
                            }
                            else {
                                logger.info(queueKey + " hosted on: " + broker.getNodeName());
                            }

                            queueHosts.put(queueKey, broker);

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

    public boolean isQueueHost(String vhost, String queue, Broker broker) {
        String queueKey = getQueueKey(vhost, queue);

        lock.readLock().lock();
        try {
            if(queueHosts.isEmpty())
                return false;

            if(queueHosts.containsKey(queueKey))
                return queueHosts.get(queueKey).getNodeName().equals(broker.getNodeName());
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
        int index = rand.nextInt(brokers.size());
        return brokers.get(index);
    }

    public Broker getHost(String vhost, String queue) {
        lock.readLock().lock();
        try {
            if(queueHosts.isEmpty())
                return null;

            return queueHosts.get(getQueueKey(vhost, queue));
        }
        finally {
            lock.readLock().unlock();
        }
    }

    public Broker getRandomOtherHost(String vhost, String queue) {
        List<Broker> hosts = null;
        String queueKey = getQueueKey(vhost, queue);

        lock.readLock().lock();
        try {
            if(queueHosts.isEmpty())
                return null;

            String nodeName = queueHosts.get(queueKey).getNodeName();
            hosts = brokers.stream()
                    .filter(x -> !x.getNodeName().equals(nodeName))
                    .collect(Collectors.toList());
        }
        finally {
            lock.readLock().unlock();
        }

        return hosts.get(rand.nextInt(hosts.size()));
    }

    private String getQueueKey(String vhost, String queueName) {
        return vhost + ":" + queueName;
    }
}
