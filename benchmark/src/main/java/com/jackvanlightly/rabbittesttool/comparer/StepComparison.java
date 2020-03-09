package com.jackvanlightly.rabbittesttool.comparer;

import java.text.MessageFormat;
import java.util.*;

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
        return "Topology|Ordinal|Description|Variables|Dimensions|Step|StepValue|BenchmarkType|Duration|Measurement|C1 Runs|C2 Run|C1 Avg|C2 Avg|Change %|C1 StdDev|C2 StdDev|Change %|C1 Min|C2 Min|Change %|C1 Max|C2 Max|Change %";
    }

    public static String getOneSidedCsvHeader() {
        return "Topology|Ordinal|Description|Variables|Dimensions|Step|StepValue|BenchmarkType|Duration|Measurement|C1 Runs|C1 Avg|C1 StdDev|C1 Min|C1 Max";
    }

    public List<String> toOneSidedCsv(List<String> descVarList) {
        StringBuilder sb = new StringBuilder();
        for(String variable : this.topologyDescription.toLowerCase().split(",")) {
            String[] parts = variable.split("=");
            if(descVarList.contains(parts[0].trim())) {
                sb.append(variable + ",");
            }
        }

        String description = sb.toString();

        List<String> lines = new ArrayList<>();
        lines.add(getOneSidedLine(sendRateAvg, description));
        lines.add(getOneSidedLine(sendRateStdDev, description));
        lines.add(getOneSidedLine(receiveRateAvg, description));
        lines.add(getOneSidedLine(receiveRateStdDev, description));
        lines.add(getOneSidedLine(sendVsReceiveDiffAvg, description));
        lines.add(getOneSidedLine(sendVsReceiveDiffStdDev, description));
        lines.add(getOneSidedLine(latencyMin, description));
        lines.add(getOneSidedLine(latency50, description));
        lines.add(getOneSidedLine(latency75, description));
        lines.add(getOneSidedLine(latency95, description));
        lines.add(getOneSidedLine(latency99, description));
        lines.add(getOneSidedLine(latency999, description));
        lines.add(getOneSidedLine(latencyMax, description));

        confirmLatencyMax.compare();
        if(confirmLatencyMax.getMeasurement1().getMaxValue() > 0 || confirmLatencyMax.getMeasurement2().getMaxValue() > 0) {
            lines.add(getOneSidedLine(confirmLatencyMin, description));
            lines.add(getOneSidedLine(confirmLatency50, description));
            lines.add(getOneSidedLine(confirmLatency75, description));
            lines.add(getOneSidedLine(confirmLatency95, description));
            lines.add(getOneSidedLine(confirmLatency99, description));
            lines.add(getOneSidedLine(confirmLatency999, description));
            lines.add(getOneSidedLine(confirmLatencyMax, description));
        }

        return lines;
    }

    public List<String> toTwoSidedCsv(List<String> descVarList) {
        StringBuilder sb = new StringBuilder();
        for(String variable : this.topologyDescription.split(",")) {
            String[] parts = variable.split("=");
            if(descVarList.contains(parts[0])) {
                sb.append(variable + ",");
            }
        }

        String description = sb.toString();

        List<String> lines = new ArrayList<>();
        //lines.add(getLine(sendRateMin));
        lines.add(getTwoSidedLine(sendRateAvg, description));
        lines.add(getTwoSidedLine(sendRateStdDev, description));
        //lines.add(getLine(sendRateMax, description));
        //lines.add(getLine(receiveRateMin, description));
        lines.add(getTwoSidedLine(receiveRateAvg, description));
        lines.add(getTwoSidedLine(receiveRateStdDev, description));
        lines.add(getTwoSidedLine(sendVsReceiveDiffAvg, description));
        lines.add(getTwoSidedLine(sendVsReceiveDiffStdDev, description));
        //lines.add(getLine(receiveRateMax, description));
//        lines.add(getLine(sentCount, description));
//        lines.add(getLine(receiveCount, description));
//        lines.add(getLine(sentBytesCount, description));
//        lines.add(getLine(receiveBytesCount, description));
        lines.add(getTwoSidedLine(latencyMin, description));
        lines.add(getTwoSidedLine(latency50, description));
        lines.add(getTwoSidedLine(latency75, description));
        lines.add(getTwoSidedLine(latency95, description));
        lines.add(getTwoSidedLine(latency99, description));
        lines.add(getTwoSidedLine(latency999, description));
        lines.add(getTwoSidedLine(latencyMax, description));

        confirmLatencyMax.compare();
        if(confirmLatencyMax.getMeasurement1().getMaxValue() > 0 || confirmLatencyMax.getMeasurement2().getMaxValue() > 0) {
            lines.add(getTwoSidedLine(confirmLatencyMin, description));
            lines.add(getTwoSidedLine(confirmLatency50, description));
            lines.add(getTwoSidedLine(confirmLatency75, description));
            lines.add(getTwoSidedLine(confirmLatency95, description));
            lines.add(getTwoSidedLine(confirmLatency99, description));
            lines.add(getTwoSidedLine(confirmLatency999, description));
            lines.add(getTwoSidedLine(confirmLatencyMax, description));
        }

        return lines;
    }

    private String getOneSidedLine(Comparison comparison, String description) {
        comparison.compare();

        return MessageFormat.format("{0}|{1}|{2}|{3}|{4}|{5}|{6}|{7}|{8}|{9}|{10}|{11,number,#.####}|{12,number,#.####}|{13,number,#.####}|{14,number,#.####}",
                this.topology,
                this.runOrdinal,
                description,
                this.topologyDescription,
                this.dimensions,
                this.step,
                this.stepValues,
                this.benchmarkType,
                this.recordingSeconds,
                comparison.getMeasurementName(),
                comparison.getMeasurement1().getValues().size(),
                comparison.getMeasurement1().getAvgValue(),
                comparison.getMeasurement1().getStdDevValue(),
                comparison.getMeasurement1().getMinValue(),
                comparison.getMeasurement1().getMaxValue());
    }

    private String getTwoSidedLine(Comparison comparison, String description) {
        comparison.compare();

        return MessageFormat.format("{0}|{1}|{2}|{3}|{4}|{5}|{6}|{7}|{8}|{9}|{10}|{11,number,#.####}|{12,number,#.####}|{13,number,#.####}|{14,number,#.####}|{15,number,#.####}|{16,number,#.####}|{17,number,#.####}|{18,number,#.####}|{19,number,#.####}|{20,number,#.####}|{21,number,#.####}|{22,number,#.####}|{23,number,#.####}",
                this.topology,
                this.runOrdinal,
                description,
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
