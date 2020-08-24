package com.jackvanlightly.rabbittesttool.register;

import com.jackvanlightly.rabbittesttool.InstanceConfiguration;
import com.jackvanlightly.rabbittesttool.model.*;
import com.jackvanlightly.rabbittesttool.topology.model.Topology;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

public class FileRegister implements BenchmarkRegister {

    private FileWriter fileWriter;
    private PrintWriter printWriter;
    private LocalDateTime startTime;
    private String violationsFile;
    private String consumeIntervalsFile;
    private String disconnectedIntervalsFile;

    public FileRegister(String path) throws IOException {
        startTime = LocalDateTime.now();
        fileWriter = new FileWriter(path + "/" + startTime.toString() + ".log");
        printWriter = new PrintWriter(fileWriter);
        long ts = System.currentTimeMillis();
        violationsFile = path + "/model-results-violations-" + ts + ".txt";
        consumeIntervalsFile = path + "/tmp/model-results-no-consume-periods-" + ts + ".txt";
        disconnectedIntervalsFile = path + "/tmp/model-results-no-connection-periods-" + ts + ".txt";
    }

    @Override
    public void logBenchmarkStart(String runId,
                                  int runOrdinal,
                                  String technology,
                                  String version,
                                  InstanceConfiguration instanceConfig,
                                  Topology topology,
                                  String arguments,
                                  String benchmarkTag) {
        printWriter.println(MessageFormat.format("StartTime={0,time} {0,date},RunId={1},Tech={2},Version={3},Hosting={4},Instance={5},Volume={6}, Tenancy={7}",
                new Date(), runId, technology, version, instanceConfig.getHosting(), instanceConfig.getInstanceType(), instanceConfig.getVolume(), instanceConfig.getTenancy()));
        printWriter.flush();
    }

    @Override
    public void logLiveStatistics(String benchmarkId, int step, StepStatistics stepStatistics) {}

    @Override
    public void logBenchmarkEnd(String benchmarkId) {
        printWriter.println(MessageFormat.format("EndTime={0,time} {0,date}", new Date()));
        printWriter.flush();
        printWriter.close();
    }

    @Override
    public void logException(String benchmarkId, Exception e) {
        printWriter.println(e);
        printWriter.flush();
    }

    @Override
    public void logStepStart(String benchmarkId, int step, int durationSeconds, String stepValue) {
        printWriter.println(MessageFormat.format("Step {0} started", step));
        printWriter.flush();
    }

    @Override
    public void logStepEnd(String benchmarkId, int step, StepStatistics stepStatistics) {
        printWriter.println(MessageFormat.format("Step {0} ended with statistics:", step));
        printWriter.println(MessageFormat.format("    Duration seconds: {0}", stepStatistics.getDurationSeconds()));
        printWriter.println(MessageFormat.format("    Sent: {0}", stepStatistics.getSentCount()));
        printWriter.println(MessageFormat.format("    Received: {0}", stepStatistics.getReceivedCount()));

        StringBuilder latenciesSb = new StringBuilder();
        StringBuilder sendRatesSb = new StringBuilder();
        StringBuilder receiveRatesSb = new StringBuilder();
        String comma = ", ";

        for(int i = 0; i<stepStatistics.getLatencyPercentiles().length; i++) {
            if(i == stepStatistics.getLatencyPercentiles().length-1)
                comma = "";

            latenciesSb.append(stepStatistics.getLatencyPercentiles()[i] + "=" + stepStatistics.getLatencies()[i] + comma);
            sendRatesSb.append(stepStatistics.getLatencyPercentiles()[i] + "=" + stepStatistics.getSendRates()[i] + comma);
            receiveRatesSb.append(stepStatistics.getLatencyPercentiles()[i] + "=" + stepStatistics.getReceiveRates()[i] + comma);
        }

        printWriter.println("    Latencies: " + latenciesSb.toString());
        printWriter.println("    Send rates: " + sendRatesSb.toString());
        printWriter.println("    Receive rates: " + receiveRatesSb.toString());
        printWriter.println("");
        printWriter.flush();
    }

    @Override
    public List<StepStatistics> getStepStatistics(String runId, String technology, String version, String configTag) {
        return null;
    }

    @Override
    public InstanceConfiguration getInstanceConfiguration(String runId, String technology, String version, String configTag) {
        return null;
    }

    @Override
    public List<BenchmarkMetaData> getBenchmarkMetaData(String runId, String technology, String version, String configTag) {
        return null;
    }

    @Override
    public void logModelSummary(Summary sumary) {
        // TODO
    }

    @Override
    public void logViolations(String benchmarkId, List<Violation> violations) {
        if(violations.isEmpty()) {
            printWriter.println("No property violations detected");
        }
        else {
            printWriter.println("Property violations detected!");
            for (Violation violation : violations) {
                if(violation.getViolationType() == ViolationType.Ordering) {
                    printWriter.println(MessageFormat.format("Type: {0}, Stream: {1}, SeqNo: {2}, Timestamp {3}, Prior Seq No {4}, Prior Timestamp {5}",
                            violation.getViolationType(),
                            violation.getMessagePayload().getSequence(),
                            violation.getMessagePayload().getSequenceNumber(),
                            violation.getMessagePayload().getTimestamp(),
                            violation.getPriorMessagePayload().getSequenceNumber(),
                            violation.getPriorMessagePayload().getTimestamp()
                    ));
                }
                else if(violation.getMessagePayload() != null) {
                    printWriter.println(MessageFormat.format("Type: {0}, Stream: {1,number,#}, SeqNo: {2,number,#}, Timestamp {3,number,#}",
                            violation.getViolationType(),
                            violation.getMessagePayload().getSequence(),
                            violation.getMessagePayload().getSequenceNumber(),
                            violation.getMessagePayload().getTimestamp()));
                }
                else {
                    printWriter.println(MessageFormat.format("Type: {0}, Stream: {1,number,#}, Size: {2,number,#}, Low SeqNo: {3,number,#}, High SeqNo: {4,number,#}, Span ts {5,number,#}",
                            violation.getViolationType(),
                            violation.getSpan().getSequence(),
                            violation.getSpan().size(),
                            violation.getSpan().getLow(),
                            violation.getSpan().getHigh(),
                            violation.getSpan().getCreated()));
                }
            }
        }
        printWriter.flush();
    }

    @Override
    public void logConsumeIntervals(String benchmarkId, List<ConsumeInterval> consumeIntervals, int unavailabilityThresholdSeconds, double availability) {
        if(consumeIntervals.isEmpty()) {
            printWriter.println("No consumer intervals over " + unavailabilityThresholdSeconds + " seconds detected");
        }
        else {
            printWriter.println("Consumer intervals over " + unavailabilityThresholdSeconds + " seconds detected!");
            for (ConsumeInterval interval : consumeIntervals) {
                printWriter.println(MessageFormat.format("ConsumerId: {0}, Start Time: {1}, Start Seq No: {2}, End Time {3}, End Seq No {4}",
                        interval.getStartMessage().getConsumerId(),
                        Instant.ofEpochMilli(interval.getStartMessage().getReceiveTimestamp()),
                        interval.getStartMessage().getMessagePayload().getSequenceNumber(),
                        Instant.ofEpochMilli(interval.getEndMessage().getReceiveTimestamp()),
                        interval.getEndMessage().getMessagePayload().getSequenceNumber()));
            }
        }
        printWriter.flush();
    }

    @Override
    public void logDisconnectedIntervals(String benchmarkId, List<DisconnectedInterval> disconnectedIntervals, int unavailabilityThresholdSeconds, double availability) {
        // TODO
    }
}
