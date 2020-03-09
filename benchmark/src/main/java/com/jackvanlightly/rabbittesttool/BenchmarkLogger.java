package com.jackvanlightly.rabbittesttool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;

public class BenchmarkLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("");
    private static String BrokerName;
    private String name;

    public static void SetBrokerName(String brokerName) {
        BrokerName = brokerName;
    }

    public BenchmarkLogger(String name) {
        this.name = name;
    }

    public void info(String message) {
        LOGGER.info(MessageFormat.format("{0}/{1}: {2}", BrokerName, name, message));
    }

    public void warn(String message) {
        LOGGER.warn(MessageFormat.format("{0}/{1}: {2}", BrokerName, name, message));
    }

    public void warn(String message, Exception e) {
        LOGGER.warn(MessageFormat.format("{0}/{1}: {2}", BrokerName, name, message), e);
    }

    public void error(String message) {
        LOGGER.error(MessageFormat.format("{0}/{1}: {2}", BrokerName, name, message));
    }

    public void error(String message, Exception e) {
        LOGGER.error(MessageFormat.format("{0}/{1}: {2}", BrokerName, name, message), e);
    }
}
