package com.jackvanlightly.rabbittesttool.topology;

import java.util.HashMap;
import java.util.Map;

public class QueueHosts {
    private static Map<String, String> QueueHosts = new HashMap<>();

    public static void register(String vhost, String queue, String host) {
        QueueHosts.put(vhost + ":" + queue, host);
    }

    public static String getHost(String vhost, String queue) {
        return QueueHosts.get(vhost + ":" + queue);
    }
}
