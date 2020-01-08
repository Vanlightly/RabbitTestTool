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

public class NamesGetter {

    private static final Logger LOGGER = LoggerFactory.getLogger("NAMES_GETTER");
    private String baseUrl;
    private String user;
    private String password;

    public NamesGetter(String ip, String port, String user, String password) {
        this.baseUrl = "http://" + ip + ":" + port;
        this.user = user;
        this.password = password;
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
                    = new UsernamePasswordCredentials(user, password);
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

    private String getNodesUrl() {
        return this.baseUrl + "/api/nodes";
    }
}
