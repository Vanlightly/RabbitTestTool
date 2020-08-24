package com.jackvanlightly.rabbittesttool.topology;

import com.jackvanlightly.rabbittesttool.BenchmarkLogger;
import com.jackvanlightly.rabbittesttool.BrokerConfiguration;
import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.topology.model.*;
import com.rabbitmq.stream.impl.Client;
import com.rabbitmq.stream.StreamException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TopologyGenerator {

    private BenchmarkLogger logger;
    private ConnectionSettings connectionSettings;
    private BrokerConfiguration brokerConfig;
    private List<String> baseUrls;
    private String baseAmqpUri;
    private List<String> downstreamBaseUrls;
    private Random rand;
    private int retryBudget=30;

    public TopologyGenerator(ConnectionSettings connectionSettings,
                             BrokerConfiguration brokerConfig) {
        this.logger = new BenchmarkLogger("TOPOLOGY_GEN");
        this.connectionSettings = connectionSettings;
        this.brokerConfig = brokerConfig;
        this.baseUrls = new ArrayList<>();
        for(Broker b : brokerConfig.getHosts())
            baseUrls.add("http://" + b.getIp() + ":" + connectionSettings.getManagementPort());

        this.baseAmqpUri = "amqp://" + connectionSettings.getUser() + ":" + connectionSettings.getPassword() + "@";

        this.downstreamBaseUrls = new ArrayList();
        for(Broker dsb : brokerConfig.getDownstreamHosts())
            this.downstreamBaseUrls.add("http://" + dsb.getIp() + ":" + connectionSettings.getManagementPort());

        this.rand = new Random();
    }

    public void declareVHost(VirtualHost vhost) {
        String vhostUrl = getVHostUrl(vhost.getName(), vhost.isDownstream());
        put(vhostUrl, "{}");

        String permissionsJson = "{\"configure\":\".*\",\"write\":\".*\",\"read\":\".*\"}";
        put(getVHostUserPermissionsUrl(vhost.getName(), connectionSettings.getUser(), vhost.isDownstream()), permissionsJson);

        logger.info("Added vhost " + vhost.getName() + " on " +
                (vhost.isDownstream() ? "downstream" : "upstream")
                + " and added permissions to user " + connectionSettings.getUser());
    }

    public boolean deleteVHost(VirtualHost vhost) {
        String vhostUrl = getVHostUrl(vhost.getName(), vhost.isDownstream());
        boolean deleted = delete(vhostUrl, true, 120000, 3, Duration.ofSeconds(10));

        if(deleted)
            logger.info("Deleted vhost " + vhost.getName() + " on " + (vhost.isDownstream() ? "downstream" : "upstream"));

        return deleted;
    }

    public void declareExchanges(VirtualHost vhost) {
        for(ExchangeConfig exchangeConfig : vhost.getExchanges())
            declareExchange(exchangeConfig);

        for(ExchangeConfig exchangeConfig : vhost.getExchanges())
            declareExchangeBindings(exchangeConfig);
    }

    private void declareExchange(ExchangeConfig exchangeConfig) {
        String exchangeTemplate = "{\"type\":\"[ex]\",\"auto_delete\":false,\"durable\":true,\"internal\":false,\"arguments\":{}}";
        String exchangeJson = exchangeTemplate.replace("[ex]", exchangeConfig.getExchangeTypeName());
        put(getExchangeUrl(exchangeConfig.getVhostName(), exchangeConfig.getName(), exchangeConfig.isDownstream()), exchangeJson);

        // if this exchange has a shovel that feeds it, declare here
        if(exchangeConfig.getShovelConfig() != null) {
            ShovelConfig sc = exchangeConfig.getShovelConfig();

            addShovel(exchangeConfig.getVhostName(),
                    sc.isSrcIsDownstream(),
                    sc.getSrcTargetType(),
                    sc.getSrcTargetName(),
                    exchangeConfig.isDownstream(),
                    ShovelTarget.Exchange,
                    exchangeConfig.getName(),
                    sc.getPrefetch(),
                    sc.getReconnectDelaySeconds(),
                    sc.getAckMode());
        }
    }

    private void declareExchangeBindings(ExchangeConfig exchangeConfig) {
        for (BindingConfig bindingConfig : exchangeConfig.getBindings()) {
            JSONObject binding = new JSONObject();

            List<String> bindingKeys = bindingConfig.getBindingKeys(1, 1, 1);
            if(bindingKeys.isEmpty())
                bindingKeys.add("");

            for(String bindingKey : bindingKeys) {
                if (bindingKey != null && !StringUtils.isEmpty(bindingKey))
                    binding.put("routing_key", bindingKey);

                if (!bindingConfig.getProperties().isEmpty()) {
                    JSONObject properties = new JSONObject();
                    for (Property p : bindingConfig.getProperties()) {
                        properties.put(p.getKey(), p.getValue());
                    }
                    binding.put("arguments", properties);
                }

                String bindingJson = binding.toString();

                post(getExchangeToExchangeBindingUrl(exchangeConfig.getVhostName(),
                        bindingConfig.getFrom(),
                        exchangeConfig.getName(),
                        exchangeConfig.isDownstream()), bindingJson);
            }
        }
    }

    public void declareQueuesAndBindings(QueueConfig queueConfig) {
        int nodeIndex = rand.nextInt(brokerConfig.getHosts().size());
        for(int i = 1; i<= queueConfig.getScale(); i++) {

            declareQueue(queueConfig, i, nodeIndex);
            declareQueueBindings(queueConfig, i);

            nodeIndex++;
            if(nodeIndex >= brokerConfig.getHosts().size())
                nodeIndex = 0;
        }
    }

    public void declareQueue(QueueConfig queueConfig, int ordinal, int nodeIndex) {
        String queueName = queueConfig.getQueueName(ordinal);
        if(queueConfig.getQueueType() == QueueType.Standard)
            declareStandardQueue(queueConfig, queueName, ordinal, nodeIndex);
        else
            declareStreamQueue(queueConfig, queueName, nodeIndex);
    }

    private void declareStandardQueue(QueueConfig queueConfig, String queueName, int ordinal, int nodeIndex) {

        JSONObject arguments = new JSONObject();

        // add the dlx to the properties. We do this via a custom field in the topology file
        // because exchange names are generated based on scaling and so are not included in properties array
        if(queueConfig.getDeadletterExchange() != null)
            arguments.put("x-dead-letter-exchange", queueConfig.getDeadletterExchange());

        boolean isQuorum = queueConfig.getProperties().stream().anyMatch(x -> x.getKey().equals("x-queue-type") && x.getValue().equals("quorum"));

        if(queueConfig.getProperties() != null && !queueConfig.getProperties().isEmpty()) {
            for(Property prop : queueConfig.getProperties()) {
                // remove the x-queue-mode property if this is a quorum queue
                if(isQuorum && prop.getKey().equals("x-queue-mode"))
                    continue;

                // remove any HA queue properties, these will be added via a policy
                if(prop.getKey().startsWith("ha-"))
                    continue;

                arguments.put(prop.getKey(), prop.getValue());
            }
        }

        JSONObject queue = new JSONObject();
        queue.put("auto_delete", false);
        queue.put("durable", true);
        String nodeName = queueConfig.isDownstream()
                ? brokerConfig.getDownstreamHosts().get(nodeIndex).getNodeName()
                : brokerConfig.getHosts().get(nodeIndex).getNodeName();
        queue.put("node", nodeName);
        queue.put("arguments", arguments);

        boolean deleted = deleteQueue(queueConfig.getVhostName(), queueName, queueConfig.isDownstream());
        if(deleted)
            ClientUtils.waitFor(1000);

        put(getQueueUrl(queueConfig.getVhostName(), queueName, queueConfig.isDownstream()), queue.toString());

        // if this has HA queue properties, declare a separate policy for that
        if(queueConfig.getProperties().stream().anyMatch(x -> x.getKey().equals("ha-mode"))) {
            JSONObject policyJson = new JSONObject();
            policyJson.put("pattern", queueConfig.getQueueName(ordinal));
            policyJson.put("priority", 0);
            policyJson.put("apply-to", "queues");

            JSONObject definition = new JSONObject();

            if(queueConfig.getProperties() != null && !queueConfig.getProperties().isEmpty()) {
                for(Property prop : queueConfig.getProperties()) {
                    if(prop.getKey().startsWith("ha-"))
                        definition.put(prop.getKey(), prop.getValue());
                }
            }
            policyJson.put("definition", definition);
            put(getHaQueuesPolicyUrl(queueName,
                    queueConfig.getVhostName(),
                    queueConfig.isDownstream()),
                    policyJson.toString());
        }

        // if this queue has a shovel that feeds it, declare here
        if(queueConfig.getShovelConfig() != null) {
            ShovelConfig sc = queueConfig.getShovelConfig();

            String srcName = "";
            if(sc.getSrcTargetType() == ShovelTarget.Queue
                    && sc.getSrcTargetName().toLowerCase().equals("counterpart")) {
                srcName = queueName;
            }
            else {
                srcName = sc.getSrcTargetName();
            }


            addShovel(queueConfig.getVhostName(),
                    sc.isSrcIsDownstream(),
                    sc.getSrcTargetType(),
                    srcName,
                    queueConfig.isDownstream(),
                    ShovelTarget.Queue,
                    queueName,
                    sc.getPrefetch(),
                    sc.getReconnectDelaySeconds(),
                    sc.getAckMode());
        }
    }

    private void declareStreamQueue(QueueConfig queueConfig, String queueName, int nodeIndex) {
        Broker broker = queueConfig.isDownstream()
                ? brokerConfig.getDownstreamHosts().get(nodeIndex)
                : brokerConfig.getHosts().get(nodeIndex);

        boolean declared = false;
        int attempts = 0;
        StreamException exception = null;
        Client client = null;

        while(!declared && attempts < 3) {
            attempts++;
            try {
                client = new Client(new Client.ClientParameters()
                        .host(broker.getIp())
                        .port(broker.getStreamPort())
                        .virtualHost(queueConfig.getVhostName())
                        .username(connectionSettings.getUser())
                        .password(connectionSettings.getPassword())
                );

                client.create(queueName, new Client.StreamParametersBuilder()
                        .maxLengthBytes(queueConfig.getRetentionSize())
                        .maxSegmentSizeBytes(queueConfig.getSegmentSize()).build());
                declared = true;
                tryClose(client);
            } catch (StreamException e) {
                tryClose(client);
                logger.info("Failed to declare stream");
                exception = e;
                ClientUtils.waitFor(60000);
            }
        }

        if(!declared)
            logger.error("Exhausted attempts to declare the stream", exception);


    }

    private void tryClose(Client client) {
        try {
            client.close();
        }
        catch(Exception e) {
            logger.error("Could not close topology generator stream client", e);
        }
    }

    private boolean deleteQueue(String vhost, String queueName, boolean isDownstream) {
        return delete(getQueueUrl(vhost, queueName, isDownstream), true, 30000, 3, Duration.ofSeconds(2));
    }

    public void declareQueueBindings(QueueConfig queueConfig, int ordinal) {
        for (BindingConfig bindingConfig : queueConfig.getBindings()) {
            JSONObject binding = new JSONObject();

            List<String> bindingKeys = bindingConfig.getBindingKeys(ordinal, queueConfig.getScale(), bindingConfig.getBindingKeysPerQueue());
            if(bindingKeys.isEmpty())
                bindingKeys.add("");

            for(String bindingKey : bindingKeys) {
                if (bindingKey != null && !StringUtils.isEmpty(bindingKey))
                    binding.put("routing_key", bindingKey);
                else
                    binding.put("routing_key", "");

                if (!bindingConfig.getProperties().isEmpty()) {
                    JSONObject properties = new JSONObject();
                    for (Property p : bindingConfig.getProperties()) {
                        properties.put(p.getKey(), p.getValue());
                    }
                    binding.put("arguments", properties);
                }

                String bindingJson = binding.toString();

                post(getExchangeToQueueBindingUrl(queueConfig.getVhostName(),
                        bindingConfig.getFrom(),
                        queueConfig.getQueueName(ordinal),
                        queueConfig.isDownstream()), bindingJson);
            }
        }
    }

    public void declarePolicies(String vhostName, List<Policy> policies, boolean isDownstreamVhost) {
        for(Policy policy : policies) {
            if(policy.isDownstream() == isDownstreamVhost) {
                JSONObject policyJson = new JSONObject();
                policyJson.put("pattern", policy.getPattern());
                policyJson.put("priority", policy.getPriority());
                policyJson.put("apply-to", policy.getApplyTo());

                JSONObject definition = new JSONObject();

                for (Property prop : policy.getProperties()) {
                    definition.put(prop.getKey(), prop.getValue());
                }

                policyJson.put("definition", definition);

                put(getHaQueuesPolicyUrl(policy.getName(), vhostName, policy.isDownstream()), policyJson.toString());
            }
        }
    }

    public void addUpstream(VirtualHost vhost,
                            int prefetch,
                            int reconnectDelaySeconds,
                            String ackMode) {
        String url = getFederationUrl(vhost.getName() + "-upstream", vhost.getName());

        JSONObject json = new JSONObject();
        json.put("uri", getUpstreamUri());
        json.put("prefetch-count", prefetch);
        json.put("reconnect-delay", reconnectDelaySeconds);
        json.put("ack-mode", ackMode);

        JSONObject wrapper = new JSONObject();
        wrapper.put("value", json);

        put(url, wrapper.toString());
    }

    public JSONArray getQueues(String vhost, boolean isDownstream, int attemptLimit) {
        int attempts = 1;
        TopologyException lastEx = null;
        while(attempts <= attemptLimit) {
            attempts++;
            String url = getQueuesUrl(vhost, isDownstream);

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
                        = new UsernamePasswordCredentials(connectionSettings.getUser(), connectionSettings.getPassword());
                httpGet.addHeader(new BasicScheme().authenticate(creds, httpGet, null));

                CloseableHttpResponse response = client.execute(httpGet);
                int responseCode = response.getStatusLine().getStatusCode();

                if (responseCode != 200) {
                    throw new TopologyException("Received a non success response code executing GET " + url
                            + " Code:" + responseCode
                            + " Response: " + response.toString());
                }

                String json = EntityUtils.toString(response.getEntity(), "UTF-8");
                client.close();

                for(int fixAttempt=0; fixAttempt<1000; fixAttempt++) {
                    try {
                        return new JSONArray(json);
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

                lastEx = new TopologyException("Reached the JSON fix limit for GET" + url);
            } catch (Exception e) {
                lastEx = new TopologyException("An exception occurred executing GET " + url, e);
            }
        }

        throw lastEx;
    }

    public List<String> getNodeNames() {
        String url = getNodesUrl();

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
                    = new UsernamePasswordCredentials(connectionSettings.getUser(), connectionSettings.getPassword());
            httpGet.addHeader(new BasicScheme().authenticate(creds, httpGet, null));

            CloseableHttpResponse response = client.execute(httpGet);
            int responseCode = response.getStatusLine().getStatusCode();

            if(responseCode != 200) {
                throw new TopologyException("Received a non success response code executing GET " + url
                        + " Code:" + responseCode
                        + " Response: " + response.toString());
            }

            String json = EntityUtils.toString(response.getEntity(), "UTF-8");
            client.close();

            List<String> nodeNames = new ArrayList<>();
            JSONArray jsonArray = new JSONArray(json);
            for(int i=0; i<jsonArray.length(); i++) {
                JSONObject jsonObj = jsonArray.getJSONObject(i);
                nodeNames.add(jsonObj.getString("name"));
            }

            return nodeNames;
        }
        catch(Exception e) {
            throw new TopologyException("An exception occurred executing GET " + url, e);
        }
    }

    public void purgeQueue(String vhost, String queueName, boolean isDownstream) {
        delete(getPurgeQueueUrl(vhost, queueName, isDownstream), false, 30000, 3, Duration.ofSeconds(10));
    }

    private void addShovel(String vhost,
                           boolean srcIsDownstream,
                           ShovelTarget srcType,
                           String srcName,
                           boolean destIsDownstream,
                           ShovelTarget destType,
                           String destName,
                           int prefetch,
                           int reconnectDelaySeconds,
                           String ackMode) {
        String url = getShovelUrl(vhost + "-" + destName, vhost, destIsDownstream);

        JSONObject json = new JSONObject();

        if(srcIsDownstream)
            json.put("src-uri", getDownstreamUri()+"/"+vhost);
        else
            json.put("src-uri", getUpstreamUri()+"/"+vhost);

        if(destIsDownstream)
            json.put("dest-uri", getDownstreamUri()+"/"+vhost);
        else
            json.put("dest-uri", getUpstreamUri()+"/"+vhost);

        if(srcType == ShovelTarget.Queue)
            json.put("src-queue", srcName);
        else
            json.put("src-exchange", srcName);

        if(destType == ShovelTarget.Queue)
            json.put("dest-queue", destName);
        else
            json.put("dest-exchange", destName);

        json.put("src-prefetch-count", prefetch);
        json.put("reconnect-delay", reconnectDelaySeconds);
        json.put("ack-mode", ackMode);
        json.put("src-protocol", "amqp091");
        json.put("dest-protocol", "amqp091");

        JSONObject wrapper = new JSONObject();
        wrapper.put("value", json);

        put(url, wrapper.toString());
    }

    private void post(String url, String json) {
        boolean success = false;
        int attempts = 0;
        int lastResponseCode = 0;
        CloseableHttpResponse lastResponse = null;

        try {
            while(!success && attempts <= 3 && retryBudget > 0) {
                attempts++;

                if (attempts > 1) {
                    retryBudget--;
                    ClientUtils.waitFor(2000);
                }

                CloseableHttpClient client = HttpClients.createDefault();
                HttpPost httpPost = new HttpPost(url);

                StringEntity entity = new StringEntity(json);
                httpPost.setEntity(entity);
                httpPost.setHeader("Accept", "application/json");
                httpPost.setHeader("Content-type", "application/json");
                UsernamePasswordCredentials creds
                        = new UsernamePasswordCredentials(connectionSettings.getUser(), connectionSettings.getPassword());
                httpPost.addHeader(new BasicScheme().authenticate(creds, httpPost, null));

                CloseableHttpResponse response = client.execute(httpPost);
                lastResponseCode = response.getStatusLine().getStatusCode();
                client.close();

                success = lastResponseCode == 201 || lastResponseCode == 204;
            }
        }
        catch(Exception e) {
            throw new TopologyException("An exception occurred executing POST " + url, e);
        }

        if(!success) {
            String responseMsg = lastResponse != null ? lastResponse.toString() : "";
            throw new TopologyException("Received a non success response code executing POST " + url
                    + " Code:" + lastResponseCode
                    + " Response: " + responseMsg);
        }
    }

    private void put(String url, String json) {
        boolean success = false;
        int attempts = 0;
        int lastResponseCode = 0;
        CloseableHttpResponse lastResponse = null;

        try {
            while(!success && attempts <= 3 && retryBudget > 0) {
                attempts++;

                if (attempts > 1) {
                    retryBudget--;
                    ClientUtils.waitFor(2000);
                }

                CloseableHttpClient client = HttpClients.createDefault();
                HttpPut httpPut = new HttpPut(url);

                StringEntity entity = new StringEntity(json);
                httpPut.setEntity(entity);
                httpPut.setHeader("Accept", "application/json");
                httpPut.setHeader("Content-type", "application/json");
                UsernamePasswordCredentials creds
                        = new UsernamePasswordCredentials(connectionSettings.getUser(), connectionSettings.getPassword());
                httpPut.addHeader(new BasicScheme().authenticate(creds, httpPut, null));

                lastResponse = client.execute(httpPut);
                lastResponseCode = lastResponse.getStatusLine().getStatusCode();
                client.close();

                success = lastResponseCode == 201 || lastResponseCode == 204;
            }
        }
        catch(Exception e) {
            throw new TopologyException("An exception occurred executing PUT " + url, e);
        }

        if(!success) {
            String responseMsg = lastResponse != null ? lastResponse.toString() : "";
            throw new TopologyException("Received a non success response code executing PUT " + url
                    + " Code:" + lastResponseCode
                    + " Response: " + responseMsg
                    + " Body: " + json);
        }
    }

    private boolean delete(String url, boolean allow404, int timeoutMs, int attemptLimit, Duration retryPause) {
        boolean deleted = false;
        boolean success = false;
        int attempts = 0;
        int lastResponseCode = 0;
        CloseableHttpResponse lastResponse = null;

        while(!success && attempts <= attemptLimit && retryBudget > 0) {
            try {
            attempts++;

            if (attempts > 1) {
                retryBudget--;
                ClientUtils.waitFor((int)retryPause.toMillis());
            }

            CloseableHttpClient client = HttpClients.createDefault();
            HttpDelete httpDelete = new HttpDelete(url);

            RequestConfig.Builder requestConfig = RequestConfig.custom();
            requestConfig.setConnectTimeout(timeoutMs);
            requestConfig.setConnectionRequestTimeout(timeoutMs);
            requestConfig.setSocketTimeout(timeoutMs);
            httpDelete.setConfig(requestConfig.build());

            UsernamePasswordCredentials creds
                    = new UsernamePasswordCredentials(connectionSettings.getUser(), connectionSettings.getPassword());
            httpDelete.addHeader(new BasicScheme().authenticate(creds, httpDelete, null));

            lastResponse = client.execute(httpDelete);
            lastResponseCode = lastResponse.getStatusLine().getStatusCode();
            client.close();

            if (lastResponseCode != 200 && lastResponseCode != 204) {
                if (lastResponseCode == 404 && allow404) {
                    success = true;
                    deleted = false;
                }
            }
            else {
                success = true;
                deleted = true;
            }

            }
            catch(Exception e) {
                throw new TopologyException("An exception occurred executing DELETE " + url, e);
            }
        }

        if(!success) {
            String responseMsg = lastResponse != null ? lastResponse.toString() : "";
            throw new TopologyException("Received a non success response code executing DELETE " + url
                    + " Code:" + lastResponseCode
                    + " Response: " + responseMsg);
        }

        return deleted;
    }

    private String getVHostUrl(String vhost, boolean isDownstream) {
        return chooseUrl(isDownstream) + "/api/vhosts/" + vhost;
    }

    private String getNodesUrl() {
        return this.baseUrls + "/api/nodes";
    }

    private String getVHostUserPermissionsUrl(String vhost, String user, boolean isDownstream) {
        return chooseUrl(isDownstream) + "/api/permissions/"+ vhost + "/" + user;
    }

    private String getExchangeUrl(String vhost, String exchange, boolean isDownstream) {
        return chooseUrl(isDownstream) + "/api/exchanges/" + vhost + "/" + exchange;
    }

    private String getQueueUrl(String vhost, String queue, boolean isDownstream) {
        return chooseUrl(isDownstream) + "/api/queues/" + vhost + "/" + queue;
    }

    private String getPurgeQueueUrl(String vhost, String queue, boolean isDownstream) {
        return chooseUrl(isDownstream) + "/api/queues/" + vhost + "/" + queue + "/contents";
    }

    private String getQueuesUrl(String vhost, boolean isDownstream) {
        return chooseUrl(isDownstream) + "/api/queues/" + vhost;
    }

    private String getExchangeToQueueBindingUrl(String vhost, String from, String to, boolean isDownstream) {
        return chooseUrl(isDownstream) + "/api/bindings/" + vhost + "/e/" + from + "/q/" + to;
    }

    private String getExchangeToExchangeBindingUrl(String vhost, String from, String to, boolean isDownstream) {
        return chooseUrl(isDownstream) + "/api/bindings/" + vhost + "/e/" + from + "/e/" + to;
    }

    private String getHaQueuesPolicyUrl(String name, String vhost, boolean isDownstream) {
        return chooseUrl(isDownstream) + "/api/policies/" + vhost + "/" + name;
    }

    private String getUpstreamUri() {
        // for now just get random broker from upstream cluster
        return baseAmqpUri +brokerConfig.getHosts().get(rand.nextInt(brokerConfig.getHosts().size())).getIp();
    }

    private String getDownstreamUri() {
        // for now just get random broker from downstream cluster
        return baseAmqpUri +brokerConfig.getDownstreamHosts().get(rand.nextInt(brokerConfig.getDownstreamHosts().size())).getIp();
    }

    private String getShovelUrl(String shovelName, String vhost, boolean isDownstream) {
        return chooseUrl(isDownstream) + "/api/parameters/shovel/" + vhost + "/"+shovelName;
    }

    private String getFederationUrl(String name, String vhost) {
        return downstreamBaseUrls + "/api/parameters/federation-upstream/" + vhost +"/" + name;
    }

    private String chooseUrl(boolean isDownstream) {
        if(isDownstream) {
            if(downstreamBaseUrls.size() == 1)
                return downstreamBaseUrls.get(0);
            else
                return downstreamBaseUrls.get(rand.nextInt(downstreamBaseUrls.size() - 1));
        }

        if(baseUrls.size() == 1)
            return baseUrls.get(0);
        else
            return baseUrls.get(rand.nextInt(baseUrls.size()-1));
    }
}
