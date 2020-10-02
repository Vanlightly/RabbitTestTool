package com.rabbitmq.orchestrator.run;

import com.rabbitmq.orchestrator.deploy.Waiter;
import com.rabbitmq.orchestrator.deploy.ec2.model.EC2System;
import com.rabbitmq.orchestrator.model.Workload;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface Runner {
    void runMainLoad(Workload workload,
                     String runId,
                     int runOrdinal,
                     String tags);
    void runBackroundLoad(Workload workload);
}
