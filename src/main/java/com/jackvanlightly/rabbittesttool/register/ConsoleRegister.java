package com.jackvanlightly.rabbittesttool.register;

import com.jackvanlightly.rabbittesttool.CmdArguments;
import com.jackvanlightly.rabbittesttool.InstanceConfiguration;
import com.jackvanlightly.rabbittesttool.model.ConsumeInterval;
import com.jackvanlightly.rabbittesttool.model.Violation;
import com.jackvanlightly.rabbittesttool.model.ViolationType;
import com.jackvanlightly.rabbittesttool.topology.model.Topology;

import java.io.PrintStream;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public class ConsoleRegister implements BenchmarkRegister {

    private PrintStream out;
    private StepStatistics lastStats;

    public ConsoleRegister(PrintStream out) {
        this.out = out;
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
        this.out.println(MessageFormat.format("StartTime={0,time} {0,date},RunId={1},Tech={2},Version={3},Hosting={4},Instance={5},Volume={6}, Tenancy={7}",
                new Date(), runId, technology, version, instanceConfig.getHosting(), instanceConfig.getInstanceType(), instanceConfig.getVolume(), instanceConfig.getTenancy()));
    }

    @Override
    public void logBenchmarkEnd(String benchmarkId) {
        this.out.println(MessageFormat.format("EndTime={0,time} {0,date}", new Date()));
    }

    @Override
    public void logException(String benchmarkId, Exception e) {
        this.out.println(e);
    }

    @Override
    public void logStepStart(String benchmarkId, int step, int durationSeconds, String stepValue) {
        this.out.println(MessageFormat.format("Step {0} started", step));
    }

    @Override
    public void logLiveStatistics(String benchmarkId, int step, StepStatistics stepStatistics) {
        this.out.println(MessageFormat.format("Step {0} live statistics:", step));
        this.out.println(MessageFormat.format("    At seconds: {0,number,#}/{1,number,#}",
                stepStatistics.getRecordingSeconds(),
                stepStatistics.getDurationSeconds()));

        if(lastStats == null) {
            this.out.println(MessageFormat.format("    Msgs Sent={0,number,#}, Bytes Sent={1,number,#},Msgs Received={2,number,#}, Bytes Received={3,number,#}",
                    stepStatistics.getSentCount(),
                    stepStatistics.getSentBytesCount(),
                    stepStatistics.getReceivedCount(),
                    stepStatistics.getReceivedBytesCount()));
        }
        else {
            this.out.println(MessageFormat.format("    Msgs Sent={0,number,#}, Bytes Sent={1,number,#},Msgs Received={2,number,#}, Bytes Received={3,number,#}",
                    stepStatistics.getSentCount() - lastStats.getSentCount(),
                    stepStatistics.getSentBytesCount() - lastStats.getSentBytesCount(),
                    stepStatistics.getReceivedCount() - lastStats.getReceivedCount(),
                    stepStatistics.getReceivedBytesCount() - lastStats.getReceivedBytesCount()));
        }

        lastStats = stepStatistics;

        StringBuilder latenciesSb = new StringBuilder();
        StringBuilder confirmLatenciesSb = new StringBuilder();
        String comma = ", ";

        for(int i = 0; i<stepStatistics.getLatencyPercentiles().length; i++) {
            if(i == stepStatistics.getLatencyPercentiles().length-1)
                comma = "";

            latenciesSb.append(stepStatistics.getLatencyPercentiles()[i] + "=" + stepStatistics.getLatencies()[i] + comma);
        }

        for(int i = 0; i<stepStatistics.getLatencyPercentiles().length; i++) {
            if(i == stepStatistics.getLatencyPercentiles().length-1)
                comma = "";

            confirmLatenciesSb.append(stepStatistics.getLatencyPercentiles()[i] + "=" + stepStatistics.getConfirmLatencies()[i] + comma);
        }

        this.out.println("    Latencies (since start): " + latenciesSb.toString());
        if(stepStatistics.getConfirmLatencies()[1] > 0) {
            this.out.println("    Confirm Latencies (since start): " + confirmLatenciesSb.toString());
        }

        this.out.println("");
    }

    @Override
    public void logStepEnd(String benchmarkId, int step, StepStatistics stepStatistics) {
        this.out.println(MessageFormat.format("Step {0} ended with statistics:", step));
        this.out.println(MessageFormat.format("    Duration seconds: {0,number,#}", stepStatistics.getDurationSeconds()));
        this.out.println(MessageFormat.format("    Msgs sent in step: {0,number,#}", stepStatistics.getSentCount()));
        this.out.println(MessageFormat.format("    Bytes sent in step: {0,number,#}", stepStatistics.getSentBytesCount()));
        this.out.println(MessageFormat.format("    Msgs received in step: {0,number,#}", stepStatistics.getReceivedCount()));
        this.out.println(MessageFormat.format("    Bytes received in step: {0,number,#}", stepStatistics.getReceivedBytesCount()));

        StringBuilder latenciesSb = new StringBuilder();
        StringBuilder confirmLatenciesSb = new StringBuilder();
        StringBuilder sendRatesSb = new StringBuilder();
        StringBuilder receiveRatesSb = new StringBuilder();
        String comma = ", ";

        for(int i = 0; i<stepStatistics.getLatencyPercentiles().length; i++) {
            if(i == stepStatistics.getLatencyPercentiles().length-1)
                comma = "";

            latenciesSb.append(stepStatistics.getLatencyPercentiles()[i] + "=" + stepStatistics.getLatencies()[i] + comma);
        }

        for(int i = 0; i<stepStatistics.getLatencyPercentiles().length; i++) {
            if(i == stepStatistics.getLatencyPercentiles().length-1)
                comma = "";

            confirmLatenciesSb.append(stepStatistics.getLatencyPercentiles()[i] + "=" + stepStatistics.getConfirmLatencies()[i] + comma);
        }

        comma = ", ";
        for(int i = 0; i<stepStatistics.getThroughPutPercentiles().length; i++) {
            if(i == stepStatistics.getThroughPutPercentiles().length-1)
                comma = "";

            sendRatesSb.append(stepStatistics.getThroughPutPercentiles()[i] + "=" + stepStatistics.getSendRates()[i] + comma);
            receiveRatesSb.append(stepStatistics.getThroughPutPercentiles()[i] + "=" + stepStatistics.getReceiveRates()[i] + comma);
        }

        this.out.println("    Latencies: " + latenciesSb.toString());
        if(stepStatistics.getConfirmLatencies()[1] > 0) {
            this.out.println("    Confirm Latencies: " + confirmLatenciesSb.toString());
        }

        this.out.println("    Send rates: " + sendRatesSb.toString());
        this.out.println("    Receive rates: " + receiveRatesSb.toString());
        this.out.println("");
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
    public void logViolations(String benchmarkId, List<Violation> violations) {
        if(violations.isEmpty()) {
            this.out.println("No property violations detected");
        }
        else {
            this.out.println("Property violations detected!");
            for (Violation violation : violations) {
                if(violation.getViolationType() == ViolationType.Ordering) {
                    this.out.println(MessageFormat.format("Type: {0}, Stream: {1}, SeqNo: {2}, Timestamp {3}, Prior Seq No {4}, Prior Timestamp {5}",
                            violation.getViolationType(),
                            violation.getMessagePayload().getStream(),
                            violation.getMessagePayload().getSequenceNumber(),
                            violation.getMessagePayload().getTimestamp(),
                            violation.getPriorMessagePayload().getSequenceNumber(),
                            violation.getPriorMessagePayload().getTimestamp()
                    ));
                }
                else {
                    this.out.println(MessageFormat.format("Type: {0}, Stream: {1}, SeqNo: {2}, Timestamp {3}",
                            violation.getViolationType(),
                            violation.getMessagePayload().getStream(),
                            violation.getMessagePayload().getSequenceNumber(),
                            violation.getMessagePayload().getTimestamp()));
                }
            }
        }
    }

    @Override
    public void logConsumeIntervals(String benchmarkId, List<ConsumeInterval> consumeIntervals) {
        if(consumeIntervals.isEmpty()) {
            this.out.println("No consumer intervals over 1 minute detected");
        }
        else {
            this.out.println("Consumer intervals over 1 minute detected!");
            for (ConsumeInterval interval : consumeIntervals) {
                this.out.println(MessageFormat.format("ConsumerId: {0}, Start Time: {1}, Start Seq No: {2}, End Time {3}, End Seq No {4}",
                        interval.getStartMessage().getConsumerId(),
                        Instant.ofEpochMilli(interval.getStartMessage().getReceiveTimestamp()),
                        interval.getStartMessage().getMessagePayload().getSequenceNumber(),
                        Instant.ofEpochMilli(interval.getEndMessage().getReceiveTimestamp()),
                        interval.getEndMessage().getMessagePayload().getSequenceNumber()));
            }
        }
    }
}
