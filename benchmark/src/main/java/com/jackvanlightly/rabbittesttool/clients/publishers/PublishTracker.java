package com.jackvanlightly.rabbittesttool.clients.publishers;

import com.jackvanlightly.rabbittesttool.clients.MessagePayload;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

public class PublishTracker {
    ConcurrentMap<Long, MessagePayload> payloads;


}
