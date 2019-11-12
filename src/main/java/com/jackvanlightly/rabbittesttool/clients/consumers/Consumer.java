package com.jackvanlightly.rabbittesttool.clients.consumers;

import com.jackvanlightly.rabbittesttool.clients.ClientUtils;
import com.jackvanlightly.rabbittesttool.clients.ConnectionSettings;
import com.jackvanlightly.rabbittesttool.topology.QueueHosts;
import com.jackvanlightly.rabbittesttool.clients.WithNagleSocketConfigurator;
import com.jackvanlightly.rabbittesttool.model.MessageModel;
import com.jackvanlightly.rabbittesttool.statistics.Stats;
import com.rabbitmq.client.*;
import io.micrometer.core.instrument.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.*;

public class Consumer implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Consumer.class);

    private String consumerId;
    private ConnectionSettings connectionSettings;
    private ConnectionFactory factory;
    private ExecutorService executorService;
    private boolean isCancelled;
    private Integer step;
    private Stats stats;
    private MessageModel messageModel;
    private ConsumerSettings consumerSettings;
    private EventingConsumer eventingConsumer;
    private ConsumerStats consumerStats;

    public Consumer(String consumerId,
                    ConnectionSettings connectionSettings,
                    ConsumerSettings consumerSettings,
                    Stats stats,
                    MessageModel messageModel) {

        this.consumerId = consumerId;
        this.connectionSettings = connectionSettings;
        this.isCancelled = isCancelled;
        this.stats = stats;
        this.messageModel = messageModel;
        this.consumerSettings = consumerSettings;
        this.step = 0;
        this.executorService = Executors.newFixedThreadPool(1, new NamedThreadFactory("Consumer-" + consumerId));
        this.consumerStats = new ConsumerStats();
    }

    public void signalStop() {
        isCancelled = true;
    }

    public void setAckInterval(int ackInterval) {
        this.consumerSettings.getAckMode().setAckInterval(ackInterval);
    }

    public void setPrefetch(int prefetch) {
        this.consumerSettings.getAckMode().setConsumerPrefetch(prefetch);
    }

    public void setProcessingMs(int processingMs) {
        this.consumerSettings.setProcessingMs(processingMs);
        if(this.eventingConsumer != null)
            this.eventingConsumer.setProcessingMs(processingMs);
    }

    public void triggerNewChannel() {
        step++;
    }

    public long getRecordedReceiveCount() {
        return consumerStats.getAndResetRecordedReceived();
    }

    public long getRealReceiveCount() {
        return consumerStats.getAndResetRealReceived();
    }

    @Override
    public void run() {
        while(!isCancelled) {
            try {
                Connection connection = getConnection();
                LOGGER.info("Consumer " + consumerId + " opened connection");

                int exitReason = 0;
                while (!isCancelled && exitReason != 3) {
                    int currentStep = step;
                    exitReason = startChannel(connection, currentStep);
                }

                if(connection.isOpen()) {
                    connection.close(AMQP.REPLY_SUCCESS, "Closed by RabbitTestTool", 3000);
                    LOGGER.info("Consumer " + consumerId + " closed connection");
                }
                this.executorService.shutdownNow();
            } catch (IOException e) {
                if(!isCancelled)
                    stats.handleConnectionError();
                LOGGER.error("Consumer " + consumerId + " has failed in step " + step, e);
            } catch (TimeoutException e) {
                if(!isCancelled)
                    stats.handleConnectionError();
                LOGGER.error("Consumer " + consumerId + " failed to connect in step " + step, e);
            } catch (Exception e) {
                if(!isCancelled)
                    stats.handleConnectionError();
                LOGGER.error("Consumer " + consumerId + " has failed unexpectedly in step " + step, e);
            }

            if(!isCancelled) {
                recreateConsumerExecutor();
            }
        }
    }

    private void recreateConsumerExecutor() {
        LOGGER.info("Consumer " + consumerId + " will restart in 5 seconds");
        this.executorService.shutdownNow();
        try {
            this.executorService.awaitTermination(5, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        ClientUtils.waitFor(5000, isCancelled);

        this.executorService = Executors.newFixedThreadPool(1, new NamedThreadFactory("Consumer-" + consumerId));

    }

//    private void closeConnection(Connection connection) throws IOException {
//        boolean closed = false;
//        while (!closed) {
//            try {
//                connection.close();
//                closed = true;
//            } catch (AlreadyClosedException e) {
//                LOGGER.info("Waiting for connection to auto-recover in order to cleanly close");
//                ClientUtils.waitFor(100, false);
//            }
//        }
//    }

    private int startChannel(Connection connection, Integer currentStep) throws IOException, TimeoutException {
        int exitReason = 0;
        Channel channel = connection.createChannel();
        LOGGER.info("Consumer " + consumerId + " opened channel");
        try {
            boolean noAck = false;
            if (consumerSettings.getAckMode().isManualAcks()) {
                channel.confirmSelect();
            } else {
                noAck = true;
            }

            if (consumerSettings.getAckMode().getConsumerPrefetch() > 0)
                channel.basicQos(consumerSettings.getAckMode().getConsumerPrefetch());

            eventingConsumer = new EventingConsumer(channel,
                    stats,
                    messageModel,
                    consumerStats,
                    consumerSettings.getAckMode().getConsumerPrefetch(),
                    consumerSettings.getAckMode().getAckInterval(),
                    consumerSettings.getProcessingMs());
            String consumerTag = channel.basicConsume(consumerSettings.getQueue(), noAck, eventingConsumer);
            LOGGER.info("Consumer " + consumerId + " consuming with tag: " + consumerTag);

            while (!isCancelled && currentStep.equals(step) && channel.isOpen()) {
                ClientUtils.waitFor(100, this.isCancelled);
            }

            if(isCancelled)
                exitReason = 1;
            else if(!currentStep.equals(step))
                exitReason = 2;
            else
                exitReason = 3;
        }
        finally {
            if(channel.isOpen()) {
                try {
                    channel.close();
                    LOGGER.info("Consumer " + consumerId + " closed channel");
                }
                catch(Exception e) {
                    LOGGER.error("Consumer " + consumerId + " could not close channel", e);
                }
            }
            else {
                exitReason = 3;
            }

            return exitReason;
        }
    }

    private Connection getConnection() throws IOException, TimeoutException {
        factory = new ConnectionFactory();
        factory.setUsername(connectionSettings.getUser());
        factory.setPassword(connectionSettings.getPassword());
        factory.setVirtualHost(connectionSettings.getVhost());

        String host = "";
        if(connectionSettings.isTryConnectToLocalBroker())
            host = QueueHosts.getHost(connectionSettings.getVhost(), consumerSettings.getQueue());
        else
            host = connectionSettings.getNextHostAndPort();

        factory.setHost(host.split(":")[0]);
        factory.setPort(Integer.valueOf(host.split(":")[1]));

        factory.setConnectionTimeout(5000);
        factory.setAutomaticRecoveryEnabled(false);
        factory.setShutdownTimeout(0);
        factory.setRequestedFrameMax(consumerSettings.getFrameMax());
        factory.setRequestedHeartbeat(10);
        factory.setSharedExecutor(this.executorService);
        factory.setThreadFactory(r -> {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setDaemon(true);
            return t;
        });

        if(!connectionSettings.isNoTcpDelay())
            factory.setSocketConfigurator(new WithNagleSocketConfigurator());


        return factory.newConnection();
    }

}
