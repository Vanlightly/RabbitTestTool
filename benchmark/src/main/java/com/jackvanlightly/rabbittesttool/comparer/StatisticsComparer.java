package com.jackvanlightly.rabbittesttool.comparer;

import com.jackvanlightly.rabbittesttool.InstanceConfiguration;
import com.jackvanlightly.rabbittesttool.register.BenchmarkMetaData;
import com.jackvanlightly.rabbittesttool.register.BenchmarkRegister;
import com.jackvanlightly.rabbittesttool.register.StepStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class StatisticsComparer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsComparer.class);
    BenchmarkRegister benchmarkRegister;

    public StatisticsComparer(BenchmarkRegister benchmarkRegister) {
        this.benchmarkRegister = benchmarkRegister;
    }

    public void generateReport(String reportFileDirectory,
                               String runId1,
                               String technology1,
                               String version1,
                               String configTag1,
                               String descVars) {
        InstanceConfiguration ic1 = benchmarkRegister.getInstanceConfiguration(runId1, technology1, version1, configTag1);

        if(ic1 == null)
            throw new RuntimeException("No instance configuration could be found for config tag: " + configTag1);

        List<StepStatistics> stepStatistics1 = benchmarkRegister.getStepStatistics(runId1, technology1, version1, configTag1)
                .stream()
                .sorted(Comparator.comparing(StepStatistics::getTopology).thenComparing(StepStatistics::getStep).thenComparing(StepStatistics::getNode))
                .collect(Collectors.toList());

        List<StepComparison> stepComparisons = generateOneSidedComparisons(stepStatistics1);

        List<String> reportLines = new ArrayList<>();
        reportLines.add(StepComparison.getOneSidedCsvHeader());

        List<String> descVarsList = Arrays.stream(descVars.split(",")).map(x -> x.toLowerCase()).collect(Collectors.toList());
        for(StepComparison comparison : stepComparisons) {
            reportLines.addAll(comparison.toOneSidedCsv(descVarsList));
        }

        try {
            File reportDir = new File(reportFileDirectory);
            if(!reportDir.exists())
                reportDir.mkdir();

            long filePrefix = Instant.now().getEpochSecond();
            File headerFile = new File(Paths.get(reportFileDirectory, filePrefix + "-header.txt").toString());
            headerFile.createNewFile();
            FileWriter hdrWriter = new FileWriter(headerFile.getAbsoluteFile());
            hdrWriter.write("RunId=" + runId1 + System.lineSeparator());
            hdrWriter.write("\n");
            hdrWriter.write("Run Configuration:" + System.lineSeparator());
            hdrWriter.write("    ConfigTag=" + configTag1 + System.lineSeparator());
            hdrWriter.write("    Technology=" + technology1 + System.lineSeparator());
            hdrWriter.write("    Version=" + version1 + System.lineSeparator());
            hdrWriter.write("    Hosting=" + ic1.getHosting() + System.lineSeparator());
            hdrWriter.write("    InstanceType=" + ic1.getInstanceType() + System.lineSeparator());
            hdrWriter.write("    Volume=" + ic1.getVolume() + System.lineSeparator());
            hdrWriter.write("    FileSystem=" + ic1.getFileSystem() + System.lineSeparator());
            hdrWriter.write("    Tenancy=" + ic1.getTenancy() + System.lineSeparator());
            hdrWriter.write("    CoreCount=" + ic1.getCoreCount() + System.lineSeparator());
            hdrWriter.write("    ThreadsPerCore=" + ic1.getThreadsPerCore() + System.lineSeparator());
            hdrWriter.write("\n");

            hdrWriter.write("\n");
            hdrWriter.write("Benchmark metadata:" + System.lineSeparator());

            List<BenchmarkMetaData> metaDataList = benchmarkRegister.getBenchmarkMetaData(runId1, technology1, version1, configTag1);
            for(BenchmarkMetaData md : metaDataList) {
                hdrWriter.write("\n");
                hdrWriter.write("---------------- Benchmark #" + md.getRunOrdinal() + "----------------" + System.lineSeparator());
                hdrWriter.write("Topology = " + md.getTopology() + System.lineSeparator());
                hdrWriter.write("Policies = " + md.getPolicy() + System.lineSeparator());
                hdrWriter.write("Arguments = " + md.getArguments() + System.lineSeparator());
            }

            hdrWriter.write("\n");
            hdrWriter.write("\n");
            hdrWriter.write("----------- Steps executed ---------------\n");

            stepStatistics1 = stepStatistics1
                    .stream()
                    .sorted(Comparator.comparing(StepStatistics::getStartMs).thenComparing(StepStatistics::getStep).thenComparing(StepStatistics::getNode))
                    .collect(Collectors.toList());

            for(StepStatistics sStats : stepStatistics1) {
                hdrWriter.write(MessageFormat.format("    Ordinal={0}, Topology={1}, Step={2}, Node={3}, StartMs={4,number,#}, EndMs={5,number,#}\n",
                        sStats.getRunOrdinal(),
                        sStats.getTopology(),
                        sStats.getStep(),
                        sStats.getNode(),
                        sStats.getStartMs(),
                        sStats.getEndMs()));
            }

            hdrWriter.close();
            LOGGER.info("Created header file: " + headerFile.getAbsolutePath());

            File csvFile = new File(Paths.get(reportFileDirectory, filePrefix + "-stats.csv").toString());
            csvFile.createNewFile();
            FileWriter writer = new FileWriter(csvFile.getAbsoluteFile());
            for (String str : reportLines) {
                writer.write(str + System.lineSeparator());
            }
            writer.close();
            LOGGER.info("Created CSV file: " + csvFile.getAbsolutePath());
        } catch(IOException e) {
            throw new RuntimeException("Failed generating comparison report", e);
        }
    }

    public void generateReport(String reportFileDirectory,
                               String runId1, String technology1, String version1, String configTag1,
                               String runId2, String technology2, String version2, String configTag2,
                               String descVars) {
        InstanceConfiguration ic1 = benchmarkRegister.getInstanceConfiguration(runId1, technology1, version1, configTag1);
        InstanceConfiguration ic2 = benchmarkRegister.getInstanceConfiguration(runId2, technology2, version2, configTag2);

        if(ic1 == null)
            throw new RuntimeException("No instance configuration could be found for config tag: " + configTag1);

        if(ic2 == null)
            throw new RuntimeException("No instance configuration could be found for config tag: " + configTag2);

        List<StepStatistics> stepStatistics1 = benchmarkRegister.getStepStatistics(runId1, technology1, version1, configTag1)
                .stream()
                .sorted(Comparator.comparing(StepStatistics::getTopology).thenComparing(StepStatistics::getStep).thenComparing(StepStatistics::getNode))
                .collect(Collectors.toList());

        List<StepStatistics> stepStatistics2 = benchmarkRegister.getStepStatistics(runId2, technology2, version2, configTag2)
                .stream()
                .sorted(Comparator.comparing(StepStatistics::getTopology).thenComparing(StepStatistics::getStep).thenComparing(StepStatistics::getNode))
                .collect(Collectors.toList());

        List<StepComparison> stepComparisons = generateComparisons(stepStatistics1, stepStatistics2);

        List<String> descVarsList = Arrays.stream(descVars.split(",")).map(x -> x.toLowerCase()).collect(Collectors.toList());
        List<String> reportLines = new ArrayList<>();
        reportLines.add(StepComparison.getCsvHeader());
        for(StepComparison comparison : stepComparisons) {
            reportLines.addAll(comparison.toTwoSidedCsv(descVarsList));
        }

        try {
            File reportDir = new File(reportFileDirectory);
            if(!reportDir.exists())
                reportDir.mkdir();

            long filePrefix = Instant.now().getEpochSecond();
            File headerFile = new File(Paths.get(reportFileDirectory, filePrefix + "-header.txt").toString());
            headerFile.createNewFile();
            FileWriter hdrWriter = new FileWriter(headerFile.getAbsoluteFile());
            hdrWriter.write("RunId=" + runId1 + System.lineSeparator());
            hdrWriter.write("C1:" + System.lineSeparator());
            hdrWriter.write("    ConfigTag=" + configTag1 + System.lineSeparator());
            hdrWriter.write("    Technology=" + technology1 + System.lineSeparator());
            hdrWriter.write("    Version=" + version1 + System.lineSeparator());
            hdrWriter.write("    Hosting=" + ic1.getHosting() + System.lineSeparator());
            hdrWriter.write("    InstanceType=" + ic1.getInstanceType() + System.lineSeparator());
            hdrWriter.write("    Volume=" + ic1.getVolume() + System.lineSeparator());
            hdrWriter.write("    FileSystem=" + ic1.getFileSystem() + System.lineSeparator());
            hdrWriter.write("    Tenancy=" + ic1.getTenancy() + System.lineSeparator());
            hdrWriter.write("    CoreCount=" + ic1.getCoreCount() + System.lineSeparator());
            hdrWriter.write("    ThreadsPerCore=" + ic1.getThreadsPerCore() + System.lineSeparator());
            hdrWriter.write("C2:" + System.lineSeparator());
            hdrWriter.write("    ConfigTag=" + configTag2 + System.lineSeparator());
            hdrWriter.write("    Technology=" + technology2 + System.lineSeparator());
            hdrWriter.write("    Version=" + version2 + System.lineSeparator());
            hdrWriter.write("    Hosting=" + ic2.getHosting() + System.lineSeparator());
            hdrWriter.write("    InstanceType=" + ic2.getInstanceType() + System.lineSeparator());
            hdrWriter.write("    Volume=" + ic2.getVolume() + System.lineSeparator());
            hdrWriter.write("    FileSystem=" + ic2.getFileSystem() + System.lineSeparator());
            hdrWriter.write("    Tenancy=" + ic2.getTenancy() + System.lineSeparator());
            hdrWriter.write("    CoreCount=" + ic2.getCoreCount() + System.lineSeparator());
            hdrWriter.write("    ThreadsPerCore=" + ic2.getThreadsPerCore() + System.lineSeparator());
            hdrWriter.write("\n");
            hdrWriter.write("Steps executed by C1:\n");

            stepStatistics1 = stepStatistics1
                    .stream()
                    .sorted(Comparator.comparing(StepStatistics::getStartMs).thenComparing(StepStatistics::getStep).thenComparing(StepStatistics::getNode))
                    .collect(Collectors.toList());

            for(StepStatistics sStats : stepStatistics1) {
                hdrWriter.write(MessageFormat.format("    Ordinal={0}, Topology={1}, Step={2}, Node={3}, StartMs={4,number,#}, EndMs={5,number,#}\n",
                        sStats.getRunOrdinal(),
                        sStats.getTopology(),
                        sStats.getStep(),
                        sStats.getNode(),
                        sStats.getStartMs(),
                        sStats.getEndMs()));
            }

            hdrWriter.write("\n");
            hdrWriter.write("Steps executed by C2:\n");

            stepStatistics2 = stepStatistics2
                    .stream()
                    .sorted(Comparator.comparing(StepStatistics::getStartMs).thenComparing(StepStatistics::getStep).thenComparing(StepStatistics::getNode))
                    .collect(Collectors.toList());

            for(StepStatistics sStats : stepStatistics2) {
                hdrWriter.write(MessageFormat.format("    Ordinal={0}, Topology={1}, Step={2}, Node={3}, StartMs={4,number,#}, EndMs={5,number,#}\n",
                        sStats.getRunOrdinal(),
                        sStats.getTopology(),
                        sStats.getStep(),
                        sStats.getNode(),
                        sStats.getStartMs(),
                        sStats.getEndMs()));
            }

            hdrWriter.close();
            LOGGER.info("Created header file: " + headerFile.getAbsolutePath());

            File csvFile = new File(Paths.get(reportFileDirectory, filePrefix + "-stats.csv").toString());
            csvFile.createNewFile();
            FileWriter writer = new FileWriter(csvFile.getAbsoluteFile());
            for (String str : reportLines) {
                writer.write(str + System.lineSeparator());
            }
            writer.close();
            LOGGER.info("Created CSV file: " + csvFile.getAbsolutePath());
        } catch(IOException e) {
            throw new RuntimeException("Failed generating comparison report", e);
        }
    }

    private List<StepComparison> generateOneSidedComparisons(List<StepStatistics> stepStatistics1) {
        Map<String,StepComparison> comparisons = new HashMap<>();
        addComparisons(comparisons, stepStatistics1, 1);

        return comparisons.entrySet()
                .stream()
                .map(x -> x.getValue())
                .sorted(Comparator.comparing(StepComparison::getRunOrdinal).thenComparing(StepComparison::getStep))
                .collect(Collectors.toList());
    }

    private List<StepComparison> generateComparisons(List<StepStatistics> stepStatistics1, List<StepStatistics> stepStatistics2) {
        Map<String,StepComparison> comparisons = new HashMap<>();
        addComparisons(comparisons, stepStatistics1, 1);
        addComparisons(comparisons, stepStatistics2, 2);

        return comparisons.entrySet()
                .stream()
                .map(x -> x.getValue())
                .sorted(Comparator.comparing(StepComparison::getRunOrdinal).thenComparing(StepComparison::getStep))
                .collect(Collectors.toList());
    }

    private void addComparisons(Map<String,StepComparison> comparisons, List<StepStatistics> stepStatistics, int measurement) {
        for(int i=0; i<stepStatistics.size(); i++) {
            StepStatistics sStats = stepStatistics.get(i);

            String comparisonKey = getComparisonKey(sStats.getTopology(), sStats.getRunOrdinal(), sStats.getStep());
            if(!comparisons.containsKey(comparisonKey))
                comparisons.put(comparisonKey, new StepComparison(sStats.getTopology(),
                        sStats.getTopologyDescription(),
                        sStats.getDimensions(),
                        sStats.getStep(),
                        sStats.getStepValue(),
                        sStats.getRunOrdinal(),
                        sStats.getRecordingSeconds(),
                        sStats.getBenchmarkType()));

            StepComparison comparison = comparisons.get(comparisonKey);
            updateComparison(comparison, sStats, measurement);
        }
    }

    private void updateComparison(StepComparison comparison, StepStatistics sStats, int measurement) {
        comparison.getSentCount().addMeasurementValue(measurement, sStats.getSentCount());
        comparison.getSentBytesCount().addMeasurementValue(measurement, sStats.getSentBytesCount());
        comparison.getReceiveCount().addMeasurementValue(measurement, sStats.getReceivedCount());
        comparison.getReceiveBytesCount().addMeasurementValue(measurement, sStats.getReceivedBytesCount());
        comparison.getLatencyMin().addMeasurementValue(measurement, sStats.getLatencies()[0]);
        comparison.getLatency50().addMeasurementValue(measurement, sStats.getLatencies()[1]);
        comparison.getLatency75().addMeasurementValue(measurement, sStats.getLatencies()[2]);
        comparison.getLatency95().addMeasurementValue(measurement, sStats.getLatencies()[3]);
        comparison.getLatency99().addMeasurementValue(measurement, sStats.getLatencies()[4]);
        comparison.getLatency999().addMeasurementValue(measurement, sStats.getLatencies()[5]);
        comparison.getLatencyMax().addMeasurementValue(measurement, sStats.getLatencies()[6]);
        comparison.getConfirmLatencyMin().addMeasurementValue(measurement, sStats.getConfirmLatencies()[0]);
        comparison.getConfirmLatency50().addMeasurementValue(measurement, sStats.getConfirmLatencies()[1]);
        comparison.getConfirmLatency75().addMeasurementValue(measurement, sStats.getConfirmLatencies()[2]);
        comparison.getConfirmLatency95().addMeasurementValue(measurement, sStats.getConfirmLatencies()[3]);
        comparison.getConfirmLatency99().addMeasurementValue(measurement, sStats.getConfirmLatencies()[4]);
        comparison.getConfirmLatency999().addMeasurementValue(measurement, sStats.getConfirmLatencies()[5]);
        comparison.getConfirmLatencyMax().addMeasurementValue(measurement, sStats.getConfirmLatencies()[6]);
        comparison.getSendRateMin().addMeasurementValue(measurement, sStats.getSendRates()[0]);
        comparison.getSendRateAvg().addMeasurementValue(measurement, sStats.getSendRates()[1]);
        comparison.getSendRateStdDev().addMeasurementValue(measurement, sStats.getSendRates()[3]);
        comparison.getSendRateMax().addMeasurementValue(measurement, sStats.getSendRates()[4]);
        comparison.getReceiveRateMin().addMeasurementValue(measurement, sStats.getReceiveRates()[0]);
        comparison.getReceiveRateAvg().addMeasurementValue(measurement, sStats.getReceiveRates()[1]);
        comparison.getReceiveRateStdDev().addMeasurementValue(measurement, sStats.getReceiveRates()[3]);
        comparison.getReceiveRateMax().addMeasurementValue(measurement, sStats.getReceiveRates()[4]);
        comparison.getSendVsReceiveDiffMin().addMeasurementValue(measurement, sStats.getSendRates()[0]-sStats.getReceiveRates()[0]);
        comparison.getSendVsReceiveDiffAvg().addMeasurementValue(measurement, sStats.getSendRates()[1]-sStats.getReceiveRates()[1]);
        comparison.getSendVsReceiveDiffStdDev().addMeasurementValue(measurement, sStats.getSendRates()[3]-sStats.getReceiveRates()[3]);
        comparison.getSendVsReceiveDiffMax().addMeasurementValue(measurement, sStats.getSendRates()[4]-sStats.getReceiveRates()[4]);
    }

    private String getComparisonKey(String topology, int runOrdinal, int step) {
        return topology + "-" + runOrdinal + "-" + step;
    }
}
