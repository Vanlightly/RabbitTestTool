package com.jackvanlightly.rabbittesttool.register;

import com.jackvanlightly.rabbittesttool.CmdArguments;
import com.jackvanlightly.rabbittesttool.InstanceConfiguration;
import com.jackvanlightly.rabbittesttool.model.ConsumeInterval;
import com.jackvanlightly.rabbittesttool.model.Violation;
import com.jackvanlightly.rabbittesttool.model.ViolationType;
import com.jackvanlightly.rabbittesttool.topology.model.Topology;

import java.io.*;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class ConsoleRegister implements BenchmarkRegister {

    private PrintStream out;
    private StepStatistics lastStats;
    private Instant started;
    private Instant stopped;
    private boolean printLiveStats;

    public ConsoleRegister(PrintStream out, boolean printLiveStats) {
        this.out = out;
        this.printLiveStats = printLiveStats;
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
        started = Instant.now();
        this.out.println(MessageFormat.format("StartTime={0,time} {0,date},RunId={1},Tech={2},Version={3},Hosting={4},Instance={5},Volume={6}, Tenancy={7}",
                new Date(), runId, technology, version, instanceConfig.getHosting(), instanceConfig.getInstanceType(), instanceConfig.getVolume(), instanceConfig.getTenancy()));
    }

    @Override
    public void logBenchmarkEnd(String benchmarkId) {
        stopped = Instant.now();
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
        if(printLiveStats) {
            this.out.println(MessageFormat.format("Step {0} live statistics:", step));
            this.out.println(MessageFormat.format("    At seconds: {0,number,#}/{1,number,#}",
                    stepStatistics.getRecordingSeconds(),
                    stepStatistics.getDurationSeconds()));

            if (lastStats == null) {
                this.out.println(MessageFormat.format("    Msgs Sent={0,number,#}, Bytes Sent={1,number,#},Msgs Received={2,number,#}, Bytes Received={3,number,#}",
                        stepStatistics.getSentCount(),
                        stepStatistics.getSentBytesCount(),
                        stepStatistics.getReceivedCount(),
                        stepStatistics.getReceivedBytesCount()));
            } else {
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

            for (int i = 0; i < stepStatistics.getLatencyPercentiles().length; i++) {
                if (i == stepStatistics.getLatencyPercentiles().length - 1)
                    comma = "";

                latenciesSb.append(stepStatistics.getLatencyPercentiles()[i] + "=" + stepStatistics.getLatencies()[i] + comma);
            }

            for (int i = 0; i < stepStatistics.getLatencyPercentiles().length; i++) {
                if (i == stepStatistics.getLatencyPercentiles().length - 1)
                    comma = "";

                confirmLatenciesSb.append(stepStatistics.getLatencyPercentiles()[i] + "=" + stepStatistics.getConfirmLatencies()[i] + comma);
            }

            this.out.println("    Latencies (since start): " + latenciesSb.toString());
            if (stepStatistics.getConfirmLatencies()[1] > 0) {
                this.out.println("    Confirm Latencies (since start): " + confirmLatenciesSb.toString());
            }

            this.out.println("");
        }
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
    public List<BenchmarkMetaData> getBenchmarkMetaData(String runId, String technology, String version, String configTag) {
        return null;
    }

    @Override
    public void logViolations(String benchmarkId, List<Violation> violations) {
        this.out.println("");
        this.out.println("----------------------------------------------------");
        this.out.println("----------- PROPERTY VIOLATIONS --------------------");
        if(violations.isEmpty()) {
            this.out.println("No property violations detected");
        }
        else {
            this.out.println("Property violations detected!");
            List<Violation> sortedViolations = violations.stream()
                    .sorted(Comparator.comparing(Violation::getViolationType)
                            .thenComparing(Violation::getTimestamp))
                    .collect(Collectors.toList());
            writeToFile(sortedViolations);
        }
    }

    private void writeToFile(List<Violation> violations) {
        try {
            String filename = "/tmp/prop-violations-" + Instant.now().getEpochSecond() + ".txt";
            File fout = new File(filename);
            FileOutputStream fos = new FileOutputStream(fout);

            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

            for (Violation violation : violations) {
                if(violation.getViolationType() == ViolationType.Ordering) {
                    bw.write(MessageFormat.format("Type: {0}, Stream: {1,number,#}, SeqNo: {2,number,#}, Timestamp {3,number,#}, Prior Seq No {4,number,#}, Prior Timestamp {5,number,#}",
                            violation.getViolationType(),
                            violation.getMessagePayload().getStream(),
                            violation.getMessagePayload().getSequenceNumber(),
                            violation.getMessagePayload().getTimestamp(),
                            violation.getPriorMessagePayload().getSequenceNumber(),
                            violation.getPriorMessagePayload().getTimestamp()
                    ));
                }
                else {
                    bw.write(MessageFormat.format("Type: {0}, Stream: {1,number,#}, SeqNo: {2,number,#}, Timestamp {3,number,#}",
                            violation.getViolationType(),
                            violation.getMessagePayload().getStream(),
                            violation.getMessagePayload().getSequenceNumber(),
                            violation.getMessagePayload().getTimestamp()));
                }
                bw.newLine();
            }

            bw.close();

            this.out.println("Saved violations to file " + filename);
        }
        catch(Exception e) {
            this.out.println("Failed to write violations to file.");
            e.printStackTrace(this.out);
        }
    }

    @Override
    public void logConsumeIntervals(String benchmarkId, List<ConsumeInterval> consumeIntervals, int unavailabilityThresholdSeconds, double availability) {
        this.out.println("");
        this.out.println("----------------------------------------------------");
        this.out.println("----------- UNAVAILABILITY PERIODS -----------------");
        if(consumeIntervals.isEmpty()) {
            this.out.println("No unavailability periods over " + unavailabilityThresholdSeconds + " seconds detected");
        }
        else {
            this.out.println("Unavailability periods over " + unavailabilityThresholdSeconds + " seconds minute detected!");

            for (ConsumeInterval interval : consumeIntervals) {
                Instant start = Instant.ofEpochMilli(interval.getStartMessage().getReceiveTimestamp());
                Instant end = Instant.ofEpochMilli(interval.getEndMessage().getReceiveTimestamp());
                long seconds = Duration.between(start, end).getSeconds();
                this.out.println(MessageFormat.format("ConsumerId: {0}, Seconds: {1,number,#}, Start Time: {2}, Start Seq No: {3,number,#}, End Time {4}, End Seq No {5,number,#}",
                        interval.getStartMessage().getConsumerId(),
                        seconds,
                        start,
                        interval.getStartMessage().getMessagePayload().getSequenceNumber(),
                        end,
                        interval.getEndMessage().getMessagePayload().getSequenceNumber()));
            }

            this.out.println(MessageFormat.format("Availability: {0,number,#.##}%", availability));
        }
        this.out.println("----------------------------------------------------");
    }
}
