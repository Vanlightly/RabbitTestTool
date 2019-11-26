package com.jackvanlightly.rabbittesttool.comparer;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public class StepComparison {
    private String topology;
    private String topologyDescription;
    private String dimensions;
    private int step;
    private int runOrdinal;
    private String stepValues;
    private int recordingSeconds;
    private String benchmarkType;

    private Comparison sentCount;
    private Comparison sentBytesCount;
    private Comparison receiveCount;
    private Comparison receiveBytesCount;
    private Comparison latencyMin;
    private Comparison latency50;
    private Comparison latency75;
    private Comparison latency95;
    private Comparison latency99;
    private Comparison latency999;
    private Comparison latencyMax;
    private Comparison confirmLatencyMin;
    private Comparison confirmLatency50;
    private Comparison confirmLatency75;
    private Comparison confirmLatency95;
    private Comparison confirmLatency99;
    private Comparison confirmLatency999;
    private Comparison confirmLatencyMax;
    private Comparison sendRateMin;
    private Comparison sendRateAvg;
    private Comparison sendRateMax;
    private Comparison sendRateStdDev;
    private Comparison receiveRateMin;
    private Comparison receiveRateAvg;
    private Comparison receiveRateMax;
    private Comparison receiveRateStdDev;
    private Comparison sendVsReceiveDiffMin;
    private Comparison sendVsReceiveDiffAvg;
    private Comparison sendVsReceiveDiffMax;
    private Comparison sendVsReceiveDiffStdDev;

    public StepComparison(String topology,
                          String topologyDescription,
                          String dimensions,
                          int step,
                          String stepValues,
                          int runOrdinal,
                          int recordingSeconds,
                          String benchmarkType) {
        this.topology = topology;
        this.topologyDescription = topologyDescription;
        this.dimensions = dimensions;
        this.step = step;
        this.stepValues = stepValues;
        this.runOrdinal = runOrdinal;
        this.recordingSeconds = recordingSeconds;
        this.benchmarkType = benchmarkType;
        sentCount = new Comparison("Sent Count");
        receiveCount = new Comparison("Received Count");
        sentBytesCount = new Comparison("Sent Bytes Count");
        receiveBytesCount = new Comparison("Received Bytes Count");
        latencyMin = new Comparison("Min Latency");
        latency50 = new Comparison("50th Latency");
        latency75 = new Comparison("75th Latency");
        latency95 = new Comparison("95th Latency");
        latency99 = new Comparison("99th Latency");
        latency999 = new Comparison("99.9th Latency");
        latencyMax = new Comparison("Max Latency");
        confirmLatencyMin = new Comparison("Min Confirm Latency");
        confirmLatency50 = new Comparison("50th Confirm Latency");
        confirmLatency75 = new Comparison("75th Confirm Latency");
        confirmLatency95 = new Comparison("95th Confirm Latency");
        confirmLatency99 = new Comparison("99th Confirm Latency");
        confirmLatency999 = new Comparison("99.9th Confirm Latency");
        confirmLatencyMax = new Comparison("Max Confirm Latency");
        sendRateMin = new Comparison("Min Send Rate");
        sendRateAvg = new Comparison("Avg Send Rate");
        sendRateStdDev = new Comparison("Std Dev Send Rate");
        sendRateMax = new Comparison("Max Send Rate");
        receiveRateMin = new Comparison("Min Receive Rate");
        receiveRateAvg = new Comparison("Avg Receive Rate");
        receiveRateStdDev = new Comparison("Std Dev Receive Rate");
        receiveRateMax = new Comparison("Max Receive Rate");
        sendVsReceiveDiffMin = new Comparison("Min Send minus Receive");
        sendVsReceiveDiffAvg = new Comparison("Avg Send minus Receive");
        sendVsReceiveDiffStdDev = new Comparison("Std Send minus Receive");
        sendVsReceiveDiffMax = new Comparison("Max Send minus Receive");
    }

    public String getTopology() {
        return topology;
    }

    public int getStep() {
        return step;
    }

    public int getRunOrdinal() {
        return runOrdinal;
    }

    public Comparison getSentCount() {
        return sentCount;
    }

    public Comparison getSentBytesCount() {
        return sentBytesCount;
    }

    public Comparison getReceiveCount() {
        return receiveCount;
    }

    public Comparison getReceiveBytesCount() {
        return receiveBytesCount;
    }

    public Comparison getLatencyMin() {
        return latencyMin;
    }

    public Comparison getLatency50() {
        return latency50;
    }

    public Comparison getLatency75() {
        return latency75;
    }

    public Comparison getLatency95() {
        return latency95;
    }

    public Comparison getLatency99() {
        return latency99;
    }

    public Comparison getLatency999() {
        return latency999;
    }

    public Comparison getLatencyMax() {
        return latencyMax;
    }

    public Comparison getConfirmLatencyMin() {
        return confirmLatencyMin;
    }

    public Comparison getConfirmLatency50() {
        return confirmLatency50;
    }

    public Comparison getConfirmLatency75() {
        return confirmLatency75;
    }

    public Comparison getConfirmLatency95() {
        return confirmLatency95;
    }

    public Comparison getConfirmLatency99() {
        return confirmLatency99;
    }

    public Comparison getConfirmLatency999() {
        return confirmLatency999;
    }

    public Comparison getConfirmLatencyMax() {
        return confirmLatencyMax;
    }

    public Comparison getSendRateMin() {
        return sendRateMin;
    }

    public Comparison getSendRateAvg() {
        return sendRateAvg;
    }

    public Comparison getSendRateMax() {
        return sendRateMax;
    }

    public Comparison getSendRateStdDev() {
        return sendRateStdDev;
    }

    public Comparison getReceiveRateMin() {
        return receiveRateMin;
    }

    public Comparison getReceiveRateAvg() {
        return receiveRateAvg;
    }

    public Comparison getReceiveRateMax() {
        return receiveRateMax;
    }

    public Comparison getReceiveRateStdDev() {
        return receiveRateStdDev;
    }

    public Comparison getSendVsReceiveDiffMin() {
        return sendVsReceiveDiffMin;
    }

    public Comparison getSendVsReceiveDiffAvg() {
        return sendVsReceiveDiffAvg;
    }

    public Comparison getSendVsReceiveDiffMax() {
        return sendVsReceiveDiffMax;
    }

    public Comparison getSendVsReceiveDiffStdDev() {
        return sendVsReceiveDiffStdDev;
    }

    public static String getCsvHeader() {
        return "Topology|Ordinal|Topology Description|Dimensions|Step|StepValue|BenchmarkType|Duration|Measurement|C1 Runs|C2 Run|C1 Avg|C2 Avg|Change %|C1 StdDev|C2 StdDev|Change %|C1 Min|C2 Min|Change %|C1 Max|C2 Max|Change %";
    }


    public List<String> toCsv() {
        List<String> lines = new ArrayList<>();
        //lines.add(getLine(sendRateMin));
        lines.add(getLine(sendRateAvg));
        lines.add(getLine(sendRateStdDev));
        //lines.add(getLine(sendRateMax));
        //lines.add(getLine(receiveRateMin));
        lines.add(getLine(receiveRateAvg));
        lines.add(getLine(receiveRateStdDev));
        lines.add(getLine(sendVsReceiveDiffAvg));
        lines.add(getLine(sendVsReceiveDiffStdDev));
        //lines.add(getLine(receiveRateMax));
//        lines.add(getLine(sentCount));
//        lines.add(getLine(receiveCount));
//        lines.add(getLine(sentBytesCount));
//        lines.add(getLine(receiveBytesCount));
        lines.add(getLine(latencyMin));
        lines.add(getLine(latency50));
        lines.add(getLine(latency75));
        lines.add(getLine(latency95));
        lines.add(getLine(latency99));
        lines.add(getLine(latency999));
        lines.add(getLine(latencyMax));

        confirmLatencyMax.compare();
        if(confirmLatencyMax.getMeasurement1().getMaxValue() > 0 || confirmLatencyMax.getMeasurement2().getMaxValue() > 0) {
            lines.add(getLine(confirmLatencyMin));
            lines.add(getLine(confirmLatency50));
            lines.add(getLine(confirmLatency75));
            lines.add(getLine(confirmLatency95));
            lines.add(getLine(confirmLatency99));
            lines.add(getLine(confirmLatency999));
            lines.add(getLine(confirmLatencyMax));
        }

        return lines;
    }

    private String getLine(Comparison comparison) {
        comparison.compare();

        return MessageFormat.format("{0}|{1}|{2}|{3}|{4}|{5}|{6}|{7}|{8}|{9}|{10,number,#.####}|{11,number,#.####}|{12,number,#.####}|{13,number,#.####}|{14,number,#.####}|{15,number,#.####}|{16,number,#.####}|{17,number,#.####}|{18,number,#.####}|{19,number,#.####}|{20,number,#.####}|{21,number,#.####}|{22,number,#.####}",
                this.topology,
                this.runOrdinal,
                this.topologyDescription,
                this.dimensions,
                this.step,
                this.stepValues,
                this.benchmarkType,
                this.recordingSeconds,
                comparison.getMeasurementName(),
                comparison.getMeasurement1().getValues().size(),
                comparison.getMeasurement2().getValues().size(),
                comparison.getMeasurement1().getAvgValue(),
                comparison.getMeasurement2().getAvgValue(),
                comparison.getAvgValueDiffPercent(),
                comparison.getMeasurement1().getStdDevValue(),
                comparison.getMeasurement2().getStdDevValue(),
                comparison.getStdDevValueDiffPercent(),
                comparison.getMeasurement1().getMinValue(),
                comparison.getMeasurement2().getMinValue(),
                comparison.getMinValueDiffPercent(),
                comparison.getMeasurement1().getMaxValue(),
                comparison.getMeasurement2().getMaxValue(),
                comparison.getMaxValueDiffPercent());
    }

}
