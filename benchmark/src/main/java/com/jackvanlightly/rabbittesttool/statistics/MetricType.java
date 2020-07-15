package com.jackvanlightly.rabbittesttool.statistics;

public enum MetricType {
    ConsumerPrefetch,
    ConsumerAckInterval,
    ConsumerAckIntervalMs,
    ConsumerAcks,
    ConsumerAckedMessages,
    ConsumerConnectionErrors,
    ConsumerLatencies,
    ConsumerStreamMessages,
    ConsumerStreamBatches,
    ConsumerReceivedMessage,
    ConsumerReceivedBytes,
    ConsumerReceivedHeaderCount,
    PublisherBlockedConnection,
    PublisherConfirm,
    PublisherConfirmLatencies,
    PublisherDeliveryMode,
    PublisherNacked,
    PublisherReturned,
    PublisherRoutingKeyLength,
    PublisherSentMessage,
    PublisherSentBytes,
    PublisherSentHeaderCount,
    PublisherUnblockedConnection
}
