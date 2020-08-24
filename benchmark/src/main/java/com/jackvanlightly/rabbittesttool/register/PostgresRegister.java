package com.jackvanlightly.rabbittesttool.register;

import com.jackvanlightly.rabbittesttool.InstanceConfiguration;
import com.jackvanlightly.rabbittesttool.model.*;
import com.jackvanlightly.rabbittesttool.topology.model.Topology;
import com.jackvanlightly.rabbittesttool.topology.model.TopologyType;
import org.postgresql.util.PGobject;
import org.postgresql.util.PSQLException;

import java.sql.*;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summarizingDouble;

public class PostgresRegister implements BenchmarkRegister {
    String jdbcUrl;
    String user;
    String password;
    String node;
    String runId;
    String runTag;
    String configTag;

    boolean printLiveStats;
    Duration printLiveStatsInterval;
    Instant lastPrintedLiveStats;

    public PostgresRegister(String url,
                            String user,
                            String password,
                            String node,
                            String runId,
                            String runTag,
                            String configTag,
                            boolean printLiveStats,
                            Duration printLiveStatsInterval) {
        this.jdbcUrl = url;
        this.user = user;
        this.password = password;
        this.node = node;
        this.runId = runId;
        this.runTag = runTag;
        this.configTag = configTag;
        this.printLiveStats = printLiveStats;
        this.printLiveStatsInterval = printLiveStatsInterval;
        this.lastPrintedLiveStats = Instant.now();
    }

    public PostgresRegister(String url,
                            String user,
                            String password) {
        this.jdbcUrl = url;
        this.user = user;
        this.password = password;
    }


    @Override
    public void logBenchmarkStart(String benchmarkId,
                                  int runOrdinal,
                                  String technology,
                                  String version,
                                  InstanceConfiguration instanceConfig,
                                  Topology topology,
                                  String arguments,
                                  String benchmarkTags) {
        String query = "INSERT INTO BENCHMARK(BENCHMARK_ID, " +
                "NODE, " +
                "RUN_ID, " +
                "RUN_ORDINAL, " +
                "RUN_TAG, " +
                "CONFIG_TAG, " +
                "TOPOLOGY_NAME, " +
                "TOPOLOGY, " +
                "POLICIES, " +
                "ARGUMENTS, " +
                "BENCHMARK_TYPE, " +
                "TAGS," +
                "DIMENSIONS," +
                "DESCRIPTION," +
                "TECHNOLOGY, " +
                "BROKER_VERSION, " +
                "HOSTING, " +
                "INSTANCE, " +
                "VOLUME, " +
                "FILESYSTEM, " +
                "CORE_COUNT, " +
                "THREADS_PER_CORE, " +
                "TENANCY, " +
                "START_TIME, " +
                "START_MS)\n" +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection con = tryGetConnection();
             PreparedStatement pst = con.prepareStatement(query)) {

            pst.setObject(1, UUID.fromString(benchmarkId));
            pst.setString(2, node);
            pst.setString(3, runId);
            pst.setInt(4, runOrdinal);
            pst.setString(5, runTag);
            pst.setString(6, configTag);
            pst.setString(7, topology.getTopologyName());

            PGobject topologyJsonCol = new PGobject();
            topologyJsonCol.setType("json");
            topologyJsonCol.setValue(topology.getTopologyJson());
            pst.setObject(8, topologyJsonCol);

            PGobject policiesJsonCol = new PGobject();
            policiesJsonCol.setType("json");
            policiesJsonCol.setValue(topology.getPoliciesJson());
            pst.setObject(9, policiesJsonCol);

            pst.setString(10, arguments);

            pst.setString(11, topology.getBenchmarkType().toString());
            pst.setString(12, benchmarkTags);

            String dimensions = null;
            if(topology.getTopologyType() == TopologyType.Fixed) {
                dimensions = topology.getTopologyType().toString();
            }
            else if(topology.getTopologyType() == TopologyType.SingleVariable) {
                dimensions = topology.getTopologyType() + ": " + topology.getVariableConfig().getDimension();
            }
            else if(topology.getTopologyType() == TopologyType.MultiVariable) {
                dimensions = topology.getTopologyType() + ": [" + String.join(",", Arrays.stream(topology.getVariableConfig().getMultiDimensions()).map(x -> x.toString()).collect(Collectors.toList())) + "]";
            }

            pst.setString(13, dimensions);
            pst.setString(14, topology.getDescription());

            pst.setString(15, technology);
            pst.setString(16, version);
            pst.setString(17, instanceConfig.getHosting());
            pst.setString(18, instanceConfig.getInstanceType());
            pst.setString(19, instanceConfig.getVolume());
            pst.setString(20, instanceConfig.getFileSystem());
            pst.setShort(21, instanceConfig.getCoreCount());
            pst.setShort(22, instanceConfig.getThreadsPerCore());
            pst.setString(23, instanceConfig.getTenancy());
            pst.setTimestamp(24, toTimestamp(Instant.now()));
            pst.setLong(25, Instant.now().getEpochSecond()*1000);

            pst.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed logging benchmark start", e);
        }
    }

    @Override
    public void logLiveStatistics(String benchmarkId, int step, StepStatistics stepStatistics) {
        if(printLiveStats) {
            Instant now = Instant.now();
            if(Duration.between(lastPrintedLiveStats, now).toMillis() > printLiveStatsInterval.toMillis()) {
                System.out.println(MessageFormat.format("At seconds: {0,number,#}/{1,number,#}: Msgs Sent={2,number,#}, Bytes Sent={3,number,#},Msgs Received={4,number,#}, Bytes Received={5,number,#}",
                        stepStatistics.getRecordingSeconds(),
                        stepStatistics.getDurationSeconds(),
                        stepStatistics.getSentCount(),
                        stepStatistics.getSentBytesCount(),
                        stepStatistics.getReceivedCount(),
                        stepStatistics.getReceivedBytesCount()));

                lastPrintedLiveStats = now;
            }
        }
    }

    @Override
    public void logBenchmarkEnd(String benchmarkId) {
        String query = "UPDATE BENCHMARK SET END_TIME = ?, END_MS = ? WHERE BENCHMARK_ID = ?";
        try (Connection con = tryGetConnection();
             PreparedStatement pst = con.prepareStatement(query)) {

            pst.setTimestamp(1, toTimestamp(Instant.now()));
            pst.setLong(2, Instant.now().getEpochSecond()*1000);
            pst.setObject(3, UUID.fromString(benchmarkId));

            pst.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed logging benchmark start", e);
        }
    }

    @Override
    public void logException(String benchmarkId, Exception e) {

    }

    @Override
    public void logStepStart(String benchmarkId, int step, int durationSeconds, String stepValue) {
        String query = "INSERT INTO STEP(BENCHMARK_ID, STEP, STEP_VALUE, DURATION_SECONDS, START_TIME, START_MS)\n" +
                "VALUES(?,?,?,?,?,?)";
        try (Connection con = tryGetConnection();
             PreparedStatement pst = con.prepareStatement(query)) {

            pst.setObject(1, UUID.fromString(benchmarkId));
            pst.setShort(2, (short)step);
            pst.setString(3, stepValue);
            pst.setInt(4, durationSeconds);
            pst.setTimestamp(5, toTimestamp(Instant.now()));
            pst.setLong(6, Instant.now().getEpochSecond()*1000);

            pst.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed logging benchmark start", e);
        }
    }

    @Override
    public void logStepEnd(String benchmarkId, int step, StepStatistics stepStatistics) {
        String query = "UPDATE STEP " +
                "SET END_TIME = ?," +
                "END_MS = ?," +
                "SENT_COUNT = ?,\n" +
                "SENT_BYTES_COUNT = ?,\n" +
                "RECEIVE_COUNT = ?,\n" +
                "RECEIVE_BYTES_COUNT = ?,\n" +
                "LATENCY_MS_MIN = ?,\n" +
                "LATENCY_MS_50 = ?,\n" +
                "LATENCY_MS_75 = ?,\n" +
                "LATENCY_MS_95 = ?,\n" +
                "LATENCY_MS_99 = ?,\n" +
                "LATENCY_MS_999 = ?,\n" +
                "LATENCY_MS_MAX = ?,\n" +
                "CONFIRM_LATENCY_MS_MIN = ?,\n" +
                "CONFIRM_LATENCY_MS_50 = ?,\n" +
                "CONFIRM_LATENCY_MS_75 = ?,\n" +
                "CONFIRM_LATENCY_MS_95 = ?,\n" +
                "CONFIRM_LATENCY_MS_99 = ?,\n" +
                "CONFIRM_LATENCY_MS_999 = ?,\n" +
                "CONFIRM_LATENCY_MS_MAX = ?,\n" +
                "SEND_RATE_MIN = ?,\n" +
                "SEND_RATE_AVG = ?,\n" +
                "SEND_RATE_MED = ?,\n" +
                "SEND_RATE_STDDEV = ?,\n" +
                "SEND_RATE_MAX = ?,\n" +
                "RECEIVE_RATE_MIN = ?,\n" +
                "RECEIVE_RATE_AVG = ?,\n" +
                "RECEIVE_RATE_MED = ?,\n" +
                "RECEIVE_RATE_STDDEV = ?,\n" +
                "RECEIVE_RATE_MAX = ?,\n" +
                "PER_PUBLISHER_RATE_MIN = ?,\n" +
                "PER_PUBLISHER_RATE_5 = ?,\n" +
                "PER_PUBLISHER_RATE_25 = ?,\n" +
                "PER_PUBLISHER_RATE_50 = ?,\n" +
                "PER_PUBLISHER_RATE_75 = ?,\n" +
                "PER_PUBLISHER_RATE_95 = ?,\n" +
                "PER_PUBLISHER_RATE_MAX = ?,\n" +
                "PER_CONSUMER_RATE_MIN = ?,\n" +
                "PER_CONSUMER_RATE_5 = ?,\n" +
                "PER_CONSUMER_RATE_25 = ?,\n" +
                "PER_CONSUMER_RATE_50 = ?,\n" +
                "PER_CONSUMER_RATE_75 = ?,\n" +
                "PER_CONSUMER_RATE_95 = ?,\n" +
                "PER_CONSUMER_RATE_MAX = ?,\n" +
                "RECORDING_SECONDS = ?\n" +
                "WHERE BENCHMARK_ID = ? AND STEP = ?";
        try (Connection con = tryGetConnection();
             PreparedStatement pst = con.prepareStatement(query)) {

            pst.setTimestamp(1, toTimestamp(Instant.now()));
            pst.setLong(2, Instant.now().getEpochSecond()*1000);
            pst.setLong(3, stepStatistics.getSentCount());
            pst.setLong(4, stepStatistics.getSentBytesCount());
            pst.setLong(5, stepStatistics.getReceivedCount());
            pst.setLong(6, stepStatistics.getReceivedBytesCount());
            pst.setDouble(7, stepStatistics.getLatencies()[0]);
            pst.setDouble(8, stepStatistics.getLatencies()[1]);
            pst.setDouble(9, stepStatistics.getLatencies()[2]);
            pst.setDouble(10, stepStatistics.getLatencies()[3]);
            pst.setDouble(11, stepStatistics.getLatencies()[4]);
            pst.setDouble(12, stepStatistics.getLatencies()[5]);
            pst.setDouble(13, stepStatistics.getLatencies()[6]);
            pst.setDouble(14, stepStatistics.getConfirmLatencies()[0]);
            pst.setDouble(15, stepStatistics.getConfirmLatencies()[1]);
            pst.setDouble(16, stepStatistics.getConfirmLatencies()[2]);
            pst.setDouble(17, stepStatistics.getConfirmLatencies()[3]);
            pst.setDouble(18, stepStatistics.getConfirmLatencies()[4]);
            pst.setDouble(19, stepStatistics.getConfirmLatencies()[5]);
            pst.setDouble(20, stepStatistics.getConfirmLatencies()[6]);
            pst.setDouble(21, stepStatistics.getSendRates()[0]);
            pst.setDouble(22, stepStatistics.getSendRates()[1]);
            pst.setDouble(23, stepStatistics.getSendRates()[2]);
            pst.setDouble(24, stepStatistics.getSendRates()[3]);
            pst.setDouble(25, stepStatistics.getSendRates()[4]);
            pst.setDouble(26, stepStatistics.getReceiveRates()[0]);
            pst.setDouble(27, stepStatistics.getReceiveRates()[1]);
            pst.setDouble(28, stepStatistics.getReceiveRates()[2]);
            pst.setDouble(29, stepStatistics.getReceiveRates()[3]);
            pst.setDouble(30, stepStatistics.getReceiveRates()[4]);

            pst.setDouble(31, stepStatistics.getPerPublisherSendRates()[0]);
            pst.setDouble(32, stepStatistics.getPerPublisherSendRates()[1]);
            pst.setDouble(33, stepStatistics.getPerPublisherSendRates()[2]);
            pst.setDouble(34, stepStatistics.getPerPublisherSendRates()[3]);
            pst.setDouble(35, stepStatistics.getPerPublisherSendRates()[4]);
            pst.setDouble(36, stepStatistics.getPerPublisherSendRates()[5]);
            pst.setDouble(37, stepStatistics.getPerPublisherSendRates()[6]);

            pst.setDouble(38, stepStatistics.getPerConsumerReceiveRates()[0]);
            pst.setDouble(39, stepStatistics.getPerConsumerReceiveRates()[1]);
            pst.setDouble(40, stepStatistics.getPerConsumerReceiveRates()[2]);
            pst.setDouble(41, stepStatistics.getPerConsumerReceiveRates()[3]);
            pst.setDouble(42, stepStatistics.getPerConsumerReceiveRates()[4]);
            pst.setDouble(43, stepStatistics.getPerConsumerReceiveRates()[5]);
            pst.setDouble(44, stepStatistics.getPerConsumerReceiveRates()[6]);

            pst.setInt(45, stepStatistics.getRecordingSeconds());
            pst.setObject(46, UUID.fromString(benchmarkId));
            pst.setShort(47, (short)step);

            pst.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed logging benchmark start", e);
        }
    }

    public List<StepStatistics> getStepStatistics(String runId,
                                           String technology,
                                           String version,
                                           String configTag) {
        List<StepStatistics> stepStatistics = new ArrayList<>();

        String query = "select B.TOPOLOGY_NAME\n" +
                "     ,B.DESCRIPTION\n" +
                "     ,B.DIMENSIONS\n" +
                "     ,B.NODE\n" +
                "     ,B.BENCHMARK_TYPE\n" +
                "     ,B.START_MS\n" +
                "     ,B.END_MS\n" +
                "     ,B.RUN_ORDINAL\n" +
                "     ,S.STEP\n" +
                "     ,S.STEP_VALUE\n" +
                "     ,S.benchmark_id\n" +
                "     ,S.duration_seconds\n" +
                "     ,S.recording_seconds\n" +
                "     ,S.sent_count\n" +
                "     ,S.sent_bytes_count\n" +
                "     ,S.receive_count\n" +
                "     ,S.receive_bytes_count\n" +
                "     ,S.latency_ms_min\n" +
                "     ,S.latency_ms_50\n" +
                "     ,S.latency_ms_75\n" +
                "     ,S.latency_ms_95\n" +
                "     ,S.latency_ms_99\n" +
                "     ,S.latency_ms_999\n" +
                "     ,S.latency_ms_max\n" +
                "     ,S.confirm_latency_ms_min\n" +
                "     ,S.confirm_latency_ms_50\n" +
                "     ,S.confirm_latency_ms_75\n" +
                "     ,S.confirm_latency_ms_95\n" +
                "     ,S.confirm_latency_ms_99\n" +
                "     ,S.confirm_latency_ms_999\n" +
                "     ,S.confirm_latency_ms_max\n" +
                "     ,S.send_rate_min\n" +
                "     ,S.send_rate_avg\n" +
                "     ,S.send_rate_med\n" +
                "     ,S.send_rate_stddev\n" +
                "     ,S.send_rate_max\n" +
                "     ,S.receive_rate_min\n" +
                "     ,S.receive_rate_avg\n" +
                "     ,S.receive_rate_med\n" +
                "     ,S.receive_rate_stddev\n" +
                "     ,S.receive_rate_max\n" +
                "     ,S.start_ms as step_start_ms\n" +
                "     ,S.end_ms as step_end_ms\n" +
                "     ,S.per_publisher_rate_min\n" +
                "     ,S.per_publisher_rate_5\n" +
                "     ,S.per_publisher_rate_25\n" +
                "     ,S.per_publisher_rate_50\n" +
                "     ,S.per_publisher_rate_75\n" +
                "     ,S.per_publisher_rate_95\n" +
                "     ,S.per_publisher_rate_max\n" +
                "     ,S.per_consumer_rate_min\n" +
                "     ,S.per_consumer_rate_5\n" +
                "     ,S.per_consumer_rate_25\n" +
                "     ,S.per_consumer_rate_50\n" +
                "     ,S.per_consumer_rate_75\n" +
                "     ,S.per_consumer_rate_95\n" +
                "     ,S.per_consumer_rate_max\n" +
                "     ,S.start_ms as step_start_ms\n" +
                "     ,S.end_ms as step_end_ms\n" +
                "FROM STEP S\n" +
                "JOIN BENCHMARK B ON S.BENCHMARK_ID = B.BENCHMARK_ID\n" +
                "WHERE B.CONFIG_TAG = ?\n" +
                "AND B.TECHNOLOGY = ?\n" +
                "AND B.BROKER_VERSION = ?\n" +
                "AND B.RUN_ID = ?" +
                "AND B.END_MS IS NOT NULL";

        try (Connection con = tryGetConnection();
             PreparedStatement pst = con.prepareStatement(query)) {

            pst.setString(1, configTag);
            pst.setString(2, technology);
            pst.setString(3, version);
            pst.setObject(4, runId);

            ResultSet rs = pst.executeQuery();

            while (rs.next()) {
                StepStatistics sStats = new StepStatistics();
                sStats.setTopology(rs.getString("topology_name"));
                sStats.setTopologyDescription(rs.getString("description"));
                sStats.setDimensions(rs.getString("dimensions"));
                sStats.setBenchmarkType(rs.getString("benchmark_type"));
                sStats.setNode(rs.getString("node"));
                sStats.setStartMs(rs.getLong("step_start_ms"));
                sStats.setEndMs(rs.getLong("step_end_ms"));
                sStats.setStep(rs.getShort("step"));
                sStats.setStepValue(rs.getString("step_value"));
                sStats.setRunOrdinal(rs.getShort("run_ordinal"));
                sStats.setRecordingSeconds(rs.getInt("recording_seconds"));
                sStats.setSentCount(rs.getLong("sent_count"));
                sStats.setSentBytesCount(rs.getLong("sent_bytes_count"));
                sStats.setReceivedCount(rs.getLong("receive_count"));
                sStats.setReceivedBytesCount(rs.getLong("receive_bytes_count"));
                sStats.setLatencies(new double[] {
                        rs.getDouble("latency_ms_min"),
                        rs.getDouble("latency_ms_50"),
                        rs.getDouble("latency_ms_75"),
                        rs.getDouble("latency_ms_95"),
                        rs.getDouble("latency_ms_99"),
                        rs.getDouble("latency_ms_999"),
                        rs.getDouble("latency_ms_max"),
                });
                sStats.setConfirmLatencies(new double[] {
                        rs.getDouble("confirm_latency_ms_min"),
                        rs.getDouble("confirm_latency_ms_50"),
                        rs.getDouble("confirm_latency_ms_75"),
                        rs.getDouble("confirm_latency_ms_95"),
                        rs.getDouble("confirm_latency_ms_99"),
                        rs.getDouble("confirm_latency_ms_999"),
                        rs.getDouble("confirm_latency_ms_max"),
                });
                sStats.setSendRates(new double[] {
                        rs.getDouble("send_rate_min"),
                        rs.getDouble("send_rate_avg"),
                        rs.getDouble("send_rate_med"),
                        rs.getDouble("send_rate_stddev"),
                        rs.getDouble("send_rate_max")
                });
                sStats.setReceiveRates(new double[] {
                        rs.getDouble("receive_rate_min"),
                        rs.getDouble("receive_rate_avg"),
                        rs.getDouble("receive_rate_med"),
                        rs.getDouble("receive_rate_stddev"),
                        rs.getDouble("receive_rate_max")
                });
                sStats.setPerPublisherSendRates(new double[] {
                        rs.getDouble("per_publisher_rate_min"),
                        rs.getDouble("per_publisher_rate_5"),
                        rs.getDouble("per_publisher_rate_25"),
                        rs.getDouble("per_publisher_rate_50"),
                        rs.getDouble("per_publisher_rate_75"),
                        rs.getDouble("per_publisher_rate_95"),
                        rs.getDouble("per_publisher_rate_max"),
                });
                sStats.setPerConsumerReceiveRates(new double[] {
                        rs.getDouble("per_consumer_rate_min"),
                        rs.getDouble("per_consumer_rate_5"),
                        rs.getDouble("per_consumer_rate_25"),
                        rs.getDouble("per_consumer_rate_50"),
                        rs.getDouble("per_consumer_rate_75"),
                        rs.getDouble("per_consumer_rate_95"),
                        rs.getDouble("per_consumer_rate_max"),
                });

                stepStatistics.add(sStats);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed getting step statistics", e);
        }

        return stepStatistics;
    }

    @Override
    public InstanceConfiguration getInstanceConfiguration(String runId, String technology, String version, String configTag) {
        String query = "SELECT \n" +
                "       INSTANCE,\n" +
                "       VOLUME,\n" +
                "       FILESYSTEM,\n" +
                "       TENANCY,\n" +
                "       CORE_COUNT,\n" +
                "       THREADS_PER_CORE,\n" +
                "       HOSTING\n" +
                "FROM BENCHMARK\n" +
                "WHERE CONFIG_TAG = ?\n" +
                "AND TECHNOLOGY = ?\n" +
                "AND BROKER_VERSION = ?\n" +
                "AND RUN_ID = ?";

        try (Connection con = tryGetConnection();
             PreparedStatement pst = con.prepareStatement(query)) {

            pst.setString(1, configTag);
            pst.setString(2, technology);
            pst.setString(3, version);
            pst.setObject(4, runId);

            ResultSet rs = pst.executeQuery();

            if (rs.next()) {
                InstanceConfiguration ic = new InstanceConfiguration(
                        rs.getString("instance"),
                        rs.getString("volume"),
                        rs.getString("filesystem"),
                        rs.getString("tenancy"),
                        rs.getString("hosting"),
                        rs.getShort("core_count"),
                        rs.getShort("threads_per_core")
                );

                return ic;
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed getting instance configuration", e);
        }

        return null;
    }

    @Override
    public List<BenchmarkMetaData> getBenchmarkMetaData(String runId, String technology, String version, String configTag) {
        List<BenchmarkMetaData> metaDataList = new ArrayList<>();

        String query = "SELECT \n" +
                "       INSTANCE,\n" +
                "       VOLUME,\n" +
                "       FILESYSTEM,\n" +
                "       TENANCY,\n" +
                "       CORE_COUNT,\n" +
                "       THREADS_PER_CORE,\n" +
                "       HOSTING,\n" +
                "       RUN_ORDINAL,\n" +
                "       TOPOLOGY,\n" +
                "       POLICIES,\n" +
                "       ARGUMENTS,\n" +
                "       TAGS\n" +
                "FROM BENCHMARK\n" +
                "WHERE CONFIG_TAG = ?\n" +
                "AND TECHNOLOGY = ?\n" +
                "AND BROKER_VERSION = ?\n" +
                "AND RUN_ID = ?";

        try (Connection con = tryGetConnection();
             PreparedStatement pst = con.prepareStatement(query)) {

            pst.setString(1, configTag);
            pst.setString(2, technology);
            pst.setString(3, version);
            pst.setObject(4, runId);

            ResultSet rs = pst.executeQuery();

            while (rs.next()) {
                InstanceConfiguration ic = new InstanceConfiguration(
                        rs.getString("instance"),
                        rs.getString("volume"),
                        rs.getString("filesystem"),
                        rs.getString("tenancy"),
                        rs.getString("hosting"),
                        rs.getShort("core_count"),
                        rs.getShort("threads_per_core")
                );

                BenchmarkMetaData md = new BenchmarkMetaData();
                md.setArguments(rs.getString("arguments"));
                md.setBenchmarkTag(rs.getString("arguments"));
                md.setConfigTag(configTag);
                md.setInstanceConfig(ic);
                md.setPolicy(rs.getString("policies"));
                md.setRunId(runId);
                md.setRunOrdinal(rs.getInt("run_ordinal"));
                md.setTechnology(technology);
                md.setVersion(version);
                md.setTopology(rs.getString("topology"));
                md.setBenchmarkTag(rs.getString("tags"));

                metaDataList.add(md);
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed retrieving benchmark meta data", e);
        }

        return metaDataList.stream()
                .collect(groupingBy(BenchmarkMetaData::getRunOrdinal))
                .values()
                .stream()
                .map(x -> x.get(0))
                .sorted(Comparator.comparing(BenchmarkMetaData::getRunOrdinal))
                .collect(Collectors.toList());
    }

    @Override
    public void logModelSummary(Summary summary) {
       String query = "INSERT INTO model_summary (" +
                "benchmark_id," +
                "published_count," +
                "consumed_count," +
                "unconsumed_remainder," +
                "redelivered_count," +
                "checked_ordering," +
                "checked_dataloss," +
                "checked_duplicates," +
                "checked_connectivity," +
                "checked_consume_uptime," +
                "include_redelivered_in_checks," +
                "safe_config_used," +
                "ordering_violations," +
                "dataloss_violations," +
                "duplicate_violations," +
                "redelivered_ordering_violations," +
                "redelivered_duplicate_violations," +
                "connection_availability," +
                "disconnection_periods," +
                "max_disconnection_ms," +
                "consume_availability," +
                "no_consume_periods," +
                "max_noconsume_ms)\n" +
               "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection con = tryGetConnection();
             PreparedStatement pst = con.prepareStatement(query)) {

            pst.setObject(1, UUID.fromString(summary.getBenchmarkId()));
            pst.setLong(2, summary.getPublishedCount());
            pst.setLong(3, summary.getConsumedCount());
            pst.setLong(4, summary.getUnconsumedRemainder());
            pst.setLong(5, summary.getRedeliveredCount());
            pst.setBoolean(6, summary.isCheckedOrdering());
            pst.setBoolean(7, summary.isCheckedDataloss());
            pst.setBoolean(8, summary.isCheckedDuplicates());
            pst.setBoolean(9, summary.isCheckedConnectivity());
            pst.setBoolean(10, summary.isCheckedConsumeUptime());
            pst.setBoolean(11, summary.isIncludeRedeliveredInChecks());
            pst.setBoolean(12, summary.isSafeConfiguration());
            pst.setLong(13, summary.getOrderingViolations());
            pst.setLong(14, summary.getDatalossViolations());
            pst.setLong(15, summary.getDuplicateViolations());
            pst.setLong(16, summary.getRedeliveredOrderingViolations());
            pst.setLong(17, summary.getRedeliveredDuplicateViolations());
            pst.setDouble(18, summary.getConnectionAvailability());
            pst.setInt(19, summary.getDisconnectionPeriods());
            pst.setInt(20, summary.getMaxDisconnectionMs());
            pst.setDouble(21, summary.getConsumeAvailability());
            pst.setInt(22, summary.getNoConsumePeriods());
            pst.setInt(23, summary.getMaxNoconsumeMs());

            pst.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed logging model summary", e);
        }
    }

    @Override
    public void logViolations(String benchmarkId, List<Violation> violations) {
        if(!violations.isEmpty()) {
            try (Connection con = tryGetConnection();) {
                for (Violation violation : violations) {
                    if (violation.getViolationType() == ViolationType.Ordering) {
                        String query = "INSERT INTO VIOLATIONS(BENCHMARK_ID, VIOLATION_TYPE, STREAM, SEQ_NO, TS, PRIOR_STREAM, PRIOR_SEQ_NO, PRIOR_TS, VIOLATION_TIME)\n" +
                                "VALUES(?,?,?,?,?,?,?,?,?)";
                        try (PreparedStatement pst = con.prepareStatement(query)) {

                            pst.setObject(1, UUID.fromString(benchmarkId));
                            pst.setString(2, violation.getViolationType().toString());
                            pst.setInt(3, violation.getMessagePayload().getSequence());
                            pst.setLong(4, violation.getMessagePayload().getSequenceNumber());
                            pst.setLong(5, violation.getMessagePayload().getTimestamp());
                            pst.setInt(6, violation.getPriorMessagePayload().getSequence());
                            pst.setLong(7, violation.getPriorMessagePayload().getSequenceNumber());
                            pst.setLong(8, violation.getPriorMessagePayload().getTimestamp());
                            pst.setTimestamp(9, toTimestamp(Instant.now()));

                            pst.executeUpdate();

                        } catch (SQLException e) {
                            throw new RuntimeException("Failed writing invariant violations", e);
                        }
                    } else if (violation.getMessagePayload() != null) {
                        String query = "INSERT INTO VIOLATIONS(BENCHMARK_ID, VIOLATION_TYPE, STREAM, SEQ_NO, SEQ_NO_HIGH, TS, VIOLATION_TIME)\n" +
                                "VALUES(?,?,?,?,?,?,?)";
                        try (PreparedStatement pst = con.prepareStatement(query)) {

                            pst.setObject(1, UUID.fromString(benchmarkId));
                            pst.setString(2, violation.getViolationType().toString());
                            pst.setInt(3, violation.getMessagePayload().getSequence());
                            pst.setLong(4, violation.getMessagePayload().getSequenceNumber());
                            pst.setLong(5, violation.getMessagePayload().getSequenceNumber());
                            pst.setLong(6, violation.getMessagePayload().getTimestamp());
                            pst.setTimestamp(7, toTimestamp(Instant.now()));

                            pst.executeUpdate();

                        } catch (SQLException e) {
                            throw new RuntimeException("Failed writing invariant violations", e);
                        }
                    } else {
                        String query = "INSERT INTO VIOLATIONS(BENCHMARK_ID, VIOLATION_TYPE, STREAM, SEQ_NO, SEQ_NO_HIGH, TS, VIOLATION_TIME)\n" +
                                "VALUES(?,?,?,?,?,?,?)";
                        try (PreparedStatement pst = con.prepareStatement(query)) {

                            pst.setObject(1, UUID.fromString(benchmarkId));
                            pst.setString(2, violation.getViolationType().toString());
                            pst.setInt(3, violation.getSpan().getSequence());
                            pst.setLong(4, violation.getSpan().getLow());
                            pst.setLong(5, violation.getSpan().getHigh());
                            pst.setLong(6, violation.getSpan().getCreated().toEpochMilli());
                            pst.setTimestamp(7, toTimestamp(violation.getSpan().getCreated()));


                            pst.executeUpdate();

                        } catch (SQLException e) {
                            throw new RuntimeException("Failed writing invariant violations", e);
                        }
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed writing invariant violations", e);
            }
        }
    }

    @Override
    public void logConsumeIntervals(String benchmarkId, List<ConsumeInterval> consumeIntervals, int unavailabilityThresholdSeconds, double availability) {
        try (Connection con = tryGetConnection();) {
            for(ConsumeInterval interval : consumeIntervals) {
                String query = "INSERT INTO CONSUME_INTERVALS(BENCHMARK_ID, CONSUMER_ID, VHOST, QUEUE, START_TIME, START_MS, END_TIME, END_MS)\n" +
                        "VALUES(?,?,?,?,?,?,?,?)";

                try (PreparedStatement pst = con.prepareStatement(query)) {

                    pst.setObject(1, UUID.fromString(benchmarkId));
                    pst.setString(2, interval.getStartMessage().getConsumerId());
                    pst.setString(3, interval.getStartMessage().getVhost());
                    pst.setString(4, interval.getStartMessage().getQueue());
                    pst.setTimestamp(5, toTimestamp(Instant.ofEpochMilli(interval.getStartMessage().getReceiveTimestamp())));
                    pst.setLong(6, interval.getStartMessage().getReceiveTimestamp());
                    pst.setTimestamp(7, toTimestamp(Instant.ofEpochMilli(interval.getEndMessage().getReceiveTimestamp())));
                    pst.setLong(8, interval.getEndMessage().getReceiveTimestamp());

                    pst.executeUpdate();

                } catch (SQLException e) {
                    throw new RuntimeException("Failed writing consume intervals", e);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed writing consume intervals", e);
        }
    }

    @Override
    public void logDisconnectedIntervals(String benchmarkId, List<DisconnectedInterval> disconnectedIntervals, int unavailabilityThresholdSeconds, double availability) {
        try (Connection con = tryGetConnection();) {
            for(DisconnectedInterval interval : disconnectedIntervals) {
                String query = "INSERT INTO DISCONNECTED_INTERVALS(BENCHMARK_ID, CLIENT_ID, DURATION_SECONDS, START_TIME, START_MS, END_TIME, END_MS)\n" +
                        "VALUES(?,?,?,?,?,?,?)";

                try (PreparedStatement pst = con.prepareStatement(query)) {

                    pst.setObject(1, UUID.fromString(benchmarkId));
                    pst.setString(2, interval.getClientId());
                    pst.setInt(3, (int)interval.getDuration().getSeconds());
                    pst.setTimestamp(4, toTimestamp(interval.getFrom()));
                    pst.setLong(5, interval.getFrom().toEpochMilli());
                    pst.setTimestamp(6, toTimestamp(interval.getTo()));
                    pst.setLong(7, interval.getTo().toEpochMilli());

                    pst.executeUpdate();

                } catch (SQLException e) {
                    throw new RuntimeException("Failed writing disconnected intervals", e);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed writing disconnected intervals", e);
        }
    }

    public java.sql.Timestamp toTimestamp(Instant instant) {
        return java.sql.Timestamp.valueOf(instant.atZone(ZoneOffset.UTC).toLocalDateTime());
    }

    private Connection tryGetConnection() throws SQLException {
        // when running on Postgres as a small PaaS instance, large deployments can overwhelm the database
        // and multiple connection attempts are required
        int attempts = 0;
        while(attempts < 10) {
            try {
                attempts++;
                Connection con = DriverManager.getConnection(jdbcUrl, user, password);
                return con;
            } catch (PSQLException e) {
                if(attempts < 4) {
                    Random rand = new Random();
                    waitFor(rand.nextInt(2000));
                }
                else {
                    throw new RuntimeException("Failed to connect", e);
                }
            }
        }

        throw new RuntimeException("Failed to connect");
    }

    private void waitFor(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch(InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
