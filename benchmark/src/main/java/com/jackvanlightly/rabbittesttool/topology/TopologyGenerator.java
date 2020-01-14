package com.jackvanlightly.rabbittesttool.topology;

import com.jackvanlightly.rabbittesttool.BrokerConfiguration;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.topology.model.*;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TopologyGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger("TOPOLOGY_GEN");
    private ConnectionSettings connectionSettings;
    private BrokerConfiguration brokerConfig;
    private String baseUrl;
    private Random rand;

    public TopologyGenerator(ConnectionSettings connectionSettings,
                             BrokerConfiguration brokerConfig) {
        this.connectionSettings = connectionSettings;
        this.brokerConfig = brokerConfig;
        this.baseUrl = "http://" + brokerConfig.getHosts().get(0).getIp() + ":" + connectionSettings.getManagementPort();
        this.rand = new Random();
    }

    public void declareVHost(VirtualHost vhost) {
        String vhostUrl = getVHostUrl(vhost.getName());
        delete(vhostUrl, true);
        put(vhostUrl, "{}");

        String permissionsJson = "{\"configure\":\".*\",\"write\":\".*\",\"read\":\".*\"}";
        put(getVHostUserPermissionsUrl(vhost.getName(), connectionSettings.getUser()), permissionsJson);

        LOGGER.info("Added vhost " + vhost.getName()+ " and added permissions to user " + connectionSettings.getUser());
    }

    public void deleteVHost(VirtualHost vhost) {
        String vhostUrl = getVHostUrl(vhost.getName());
        delete(vhostUrl, false);
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
        put(getExchangeUrl(exchangeConfig.getVhostName(), exchangeConfig.getName()), exchangeJson);
    }

    private void declareExchangeBindings(ExchangeConfig exchangeConfig) {
        for (BindingConfig bindingConfig : exchangeConfig.getBindings()) {
            JSONObject binding = new JSONObject();

            if (bindingConfig.getBindingKey() != null || StringUtils.isEmpty(bindingConfig.getBindingKey()))
                binding.put("routing_key", bindingConfig.getBindingKey());

            if(!bindingConfig.getProperties().isEmpty()) {
                JSONObject properties = new JSONObject();
                for (Property p : bindingConfig.getProperties()) {
                    properties.put(p.getKey(), p.getValue());
                }
                binding.put("arguments", properties);
            }

            String bindingJson = binding.toString();

            post(getExchangeToExchangeBindingUrl(exchangeConfig.getVhostName(), bindingConfig.getFrom(), exchangeConfig.getName()), bindingJson);
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
        //TODO queue properties
        //String queueTemplate = "{\"auto_delete\":false,\"durable\":true,\"arguments\":{},\"node\":\"rabbit@rabbitmq" + node + "\"}";

        JSONObject arguments = new JSONObject();

        boolean isQuorum = queueConfig.getProperties().stream().anyMatch(x -> x.getKey().equals("x-queue-type") && x.getValue().equals("quorum"));

        if(queueConfig.getProperties() != null && !queueConfig.getProperties().isEmpty()) {
            for(Property prop : queueConfig.getProperties()) {
                if(isQuorum && prop.getKey().equals("x-queue-mode"))
                    continue;

                if(prop.getKey().startsWith("ha-"))
                    continue;

                arguments.put(prop.getKey(), prop.getValue());
            }
        }

        String queueName = queueConfig.getQueueName(ordinal);

        JSONObject queue = new JSONObject();
        queue.put("auto_delete", false);
        queue.put("durable", true);
        queue.put("node", brokerConfig.getHosts().get(nodeIndex).getNodeName());
        queue.put("arguments", arguments);

        put(getQueueUrl(queueConfig.getVhostName(), queueName), queue.toString());

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
            put(getHaQueuesPolicyUrl(queueName, queueConfig.getVhostName()), policyJson.toString());
        }
    }

    public void declareQueueBindings(QueueConfig queueConfig, int ordinal) {
        for (BindingConfig bindingConfig : queueConfig.getBindings()) {
            JSONObject binding = new JSONObject();

            if (bindingConfig.getBindingKey() != null && !StringUtils.isEmpty(bindingConfig.getBindingKey()))
                binding.put("routing_key", bindingConfig.getBindingKey());
            else
                binding.put("routing_key", "");

            if(!bindingConfig.getProperties().isEmpty()) {
                JSONObject properties = new JSONObject();
                for (Property p : bindingConfig.getProperties()) {
                    properties.put(p.getKey(), p.getValue());
                }
                binding.put("arguments", properties);
            }

            String bindingJson = binding.toString();

            post(getExchangeToQueueBindingUrl(queueConfig.getVhostName(), bindingConfig.getFrom(), queueConfig.getQueueName(ordinal)), bindingJson);
        }
    }

    public void declarePolicies(String vhostName, List<Policy> policies) {
        for(Policy policy : policies) {
            JSONObject policyJson = new JSONObject();
            policyJson.put("pattern", policy.getPattern());
            policyJson.put("priority", policy.getPriority());
            policyJson.put("apply-to", policy.getApplyTo());

            JSONObject definition = new JSONObject();

            for (Property prop : policy.getProperties()) {
                definition.put(prop.getKey(), prop.getValue());
            }

            policyJson.put("definition", definition);

            put(getHaQueuesPolicyUrl(policy.getName(), vhostName), policyJson.toString());
        }
    }



    public JSONArray getQueues(String vhost) {
        String url = getQueuesUrl(vhost);

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

            try {
                return new JSONArray(json);
            }
            catch(JSONException je) {
                System.out.println(json);
                if(je.getMessage().startsWith("Duplicate key")) {
                    String pattern = "\\\"(.+)\\\"";
                    Pattern r = Pattern.compile(pattern);
                    Matcher m = r.matcher(je.getMessage());
                    if(m.find()) {
                        String duplicateKey = m.group(1);
                        while (json.contains(duplicateKey))
                            json = json.replaceFirst(duplicateKey, UUID.randomUUID().toString());

                        return new JSONArray(json);
                    }
                    else {
                        throw je;
                    }
                }
                else {
                    throw je;
                }
            }
        }
        catch(Exception e) {
            throw new TopologyException("An exception occurred executing GET " + url, e);
        }
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

    private void post(String url, String json) {
        try {
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
            int responseCode = response.getStatusLine().getStatusCode();
            client.close();

            if(responseCode != 201 && responseCode != 204)
                throw new TopologyException("Received a non success response code executing POST " + url
                        + " Code:" + responseCode
                        + " Response: " + response.toString());
        }
        catch(Exception e) {
            throw new TopologyException("An exception occurred executing POST " + url, e);
        }
    }

    private void put(String url, String json) {
        try {
            CloseableHttpClient client = HttpClients.createDefault();
            HttpPut httpPut = new HttpPut(url);

            StringEntity entity = new StringEntity(json);
            httpPut.setEntity(entity);
            httpPut.setHeader("Accept", "application/json");
            httpPut.setHeader("Content-type", "application/json");
            UsernamePasswordCredentials creds
                    = new UsernamePasswordCredentials(connectionSettings.getUser(), connectionSettings.getPassword());
            httpPut.addHeader(new BasicScheme().authenticate(creds, httpPut, null));

            CloseableHttpResponse response = client.execute(httpPut);
            int responseCode = response.getStatusLine().getStatusCode();
            client.close();

            if(responseCode != 201 && responseCode != 204)
                throw new TopologyException("Received a non success response code executing PUT " + url
                        + " Code:" + responseCode
                        + " Response: " + response.toString());
        }
        catch(Exception e) {
            throw new TopologyException("An exception occurred executing PUT " + url, e);
        }
    }

    private void delete(String url, boolean allow404) {
        try {
            CloseableHttpClient client = HttpClients.createDefault();
            HttpDelete httpDelete = new HttpDelete(url);

            UsernamePasswordCredentials creds
                    = new UsernamePasswordCredentials(connectionSettings.getUser(), connectionSettings.getPassword());
            httpDelete.addHeader(new BasicScheme().authenticate(creds, httpDelete, null));

            CloseableHttpResponse response = client.execute(httpDelete);
            int responseCode = response.getStatusLine().getStatusCode();
            client.close();

            if(responseCode != 200 && responseCode != 204) {
                if(responseCode == 404 && allow404)
                    return;

                throw new TopologyException("Received a non success response code executing DELETE " + url
                        + " Code:" + responseCode
                        + " Response: " + response.toString());
            }
        }
        catch(Exception e) {
            throw new TopologyException("An exception occurred executing DELETE " + url, e);
        }
    }

    private String getVHostUrl(String vhost) {
        return this.baseUrl + "/api/vhosts/" + vhost;
    }

    private String getNodesUrl() {
        return this.baseUrl + "/api/nodes";
    }

    private String getVHostUserPermissionsUrl(String vhost, String user) {
        return this.baseUrl + "/api/permissions/"+ vhost + "/" + user;
    }

    private String getExchangeUrl(String vhost, String exchange) {
        return this.baseUrl + "/api/exchanges/" + vhost + "/" + exchange;
    }

    private String getQueueUrl(String vhost, String queue) {
        return this.baseUrl + "/api/queues/" + vhost + "/" + queue;
    }

    private String getQueuesUrl(String vhost) {
        return this.baseUrl + "/api/queues/" + vhost;
    }

    private String getExchangeToQueueBindingUrl(String vhost, String from, String to) {
        return this.baseUrl + "/api/bindings/" + vhost + "/e/" + from + "/q/" + to;
    }

    private String getExchangeToExchangeBindingUrl(String vhost, String from, String to) {
        return this.baseUrl + "/api/bindings/" + vhost + "/e/" + from + "/e/" + to;
    }

    private String getHaQueuesPolicyUrl(String name, String vhost) {
        return this.baseUrl + "/api/policies/" + vhost + "/" + name;
    }
}
