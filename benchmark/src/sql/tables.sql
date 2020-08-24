create table benchmark
(
	benchmark_id uuid not null
		constraint benchmark_pk1
			primary key,
	run_id varchar(100) not null,
	topology_name varchar(200) not null,
	benchmark_type varchar(20) not null,
	dimensions varchar(1000) not null,
	description varchar(1000) not null,
	run_tag varchar(10) not null,
	config_tag varchar(50) not null,
	node varchar(300) not null,
	technology varchar(100) not null,
	broker_version varchar(50) not null,
	instance varchar(100) not null,
	volume varchar(100) not null,
	filesystem varchar(10) not null,
	tenancy varchar(50) not null,
	core_count smallint not null,
	threads_per_core smallint not null,
	hosting varchar(50) not null,
	start_time timestamp not null,
	start_ms bigint not null,
	end_time timestamp,
	end_ms bigint,
	topology json,
	run_ordinal integer,
	policies json,
	tags varchar(1000),
	arguments varchar
);

CREATE TABLE STEP (
    BENCHMARK_ID UUID NOT NULL,
    STEP SMALLINT NOT NULL,
    STEP_VALUE VARCHAR(200) NULL,
    DURATION_SECONDS INT NOT NULL,
    RECORDING_SECONDS INT NULL,
    SENT_COUNT BIGINT NULL,
    SENT_BYTES_COUNT BIGINT NULL,
    RECEIVE_COUNT BIGINT NULL,
    RECEIVE_BYTES_COUNT BIGINT NULL,
    LATENCY_MS_MIN FLOAT(2) NULL,
    LATENCY_MS_50 FLOAT(2) NULL,
    LATENCY_MS_75 FLOAT(2) NULL,
    LATENCY_MS_95 FLOAT(2) NULL,
    LATENCY_MS_99 FLOAT(2) NULL,
    LATENCY_MS_999 FLOAT(2) NULL,
    LATENCY_MS_MAX FLOAT(2) NULL,
    CONFIRM_LATENCY_MS_MIN FLOAT(2) NULL,
    CONFIRM_LATENCY_MS_50 FLOAT(2) NULL,
    CONFIRM_LATENCY_MS_75 FLOAT(2) NULL,
    CONFIRM_LATENCY_MS_95 FLOAT(2) NULL,
    CONFIRM_LATENCY_MS_99 FLOAT(2) NULL,
    CONFIRM_LATENCY_MS_999 FLOAT(2) NULL,
    CONFIRM_LATENCY_MS_MAX FLOAT(2) NULL,
    SEND_RATE_MIN FLOAT(2) NULL,
    SEND_RATE_AVG FLOAT(2) NULL,
    SEND_RATE_MED FLOAT(2) NULL,
    SEND_RATE_STDDEV FLOAT(2) NULL,
    SEND_RATE_MAX FLOAT(2) NULL,
    RECEIVE_RATE_MIN FLOAT(2) NULL,
    RECEIVE_RATE_AVG FLOAT(2) NULL,
    RECEIVE_RATE_MED FLOAT(2) NULL,
    RECEIVE_RATE_STDDEV FLOAT(2) NULL,
    RECEIVE_RATE_MAX FLOAT(2) NULL,
    PER_PUBLISHER_RATE_MIN FLOAT(2) NULL,
    PER_PUBLISHER_RATE_5 FLOAT(2) NULL,
    PER_PUBLISHER_RATE_25 FLOAT(2) NULL,
    PER_PUBLISHER_RATE_50 FLOAT(2) NULL,
    PER_PUBLISHER_RATE_75 FLOAT(2) NULL,
    PER_PUBLISHER_RATE_95 FLOAT(2) NULL,
    PER_PUBLISHER_RATE_MAX FLOAT(2) NULL,
    PER_CONSUMER_RATE_MIN FLOAT(2) NULL,
    PER_CONSUMER_RATE_5 FLOAT(2) NULL,
    PER_CONSUMER_RATE_25 FLOAT(2) NULL,
    PER_CONSUMER_RATE_50 FLOAT(2) NULL,
    PER_CONSUMER_RATE_75 FLOAT(2) NULL,
    PER_CONSUMER_RATE_95 FLOAT(2) NULL,
    PER_CONSUMER_RATE_MAX FLOAT(2) NULL,
    START_TIME TIMESTAMP NOT NULL,
    START_MS BIGINT NOT NULL,
    END_TIME TIMESTAMP NULL,
    END_MS BIGINT NULL,
    PRIMARY KEY(BENCHMARK_ID, STEP)
);

CREATE TABLE VIOLATIONS (
    BENCHMARK_ID UUID NOT NULL,
    VIOLATION_ID SERIAL,
    VIOLATION_TYPE VARCHAR(100) NOT NULL,
    STREAM INT NOT NULL,
    SEQ_NO BIGINT NOT NULL,
    TS BIGINT NOT NULL,
    PRIOR_STREAM INT NULL,
    PRIOR_SEQ_NO BIGINT NULL,
    PRIOR_TS BIGINT NULL,
    PRIMARY KEY(BENCHMARK_ID, VIOLATION_ID)
);

CREATE TABLE CONSUME_INTERVALS (
    BENCHMARK_ID UUID NOT NULL,
    INTERVAL_ID SERIAL,
    CONSUMER_ID varchar(100),
    VHOST varchar(100) not null,
    QUEUE varchar(100) not null,
    START_TIME timestamp not null,
	START_MS bigint not null,
	END_TIME timestamp null,
	END_MS bigint null,
	PRIMARY KEY(BENCHMARK_ID, INTERVAL_ID)
);

CREATE TABLE public.disconnected_intervals
(
    benchmark_id uuid NOT NULL,
    interval_id SERIAL,
    client_id character varying(200) NOT NULL,
    duration_seconds integer NOT NULL,
    start_time timestamp NOT NULL,
    start_ms bigint NOT NULL,
    end_time timestamp NOT NULL,
    end_ms bigint NOT NULL,
    CONSTRAINT disconnected_intervals_pkey PRIMARY KEY (benchmark_id, interval_id)
);

create table public.model_summary
(
	benchmark_id uuid not null
		constraint model_summary_pk1
			primary key,
	published_count bigint not null,
	consumed_count bigint not null,
	unconsumed_remainder bigint not null,
	redelivered_count bigint not null,
	checked_ordering boolean not null,
	checked_dataloss boolean not null,
	checked_duplicates boolean not null,
	checked_connectivity boolean not null,
	checked_consume_uptime boolean not null,
	include_redelivered_in_checks boolean not null,
	safe_config_used boolean not null,
	ordering_violations bigint not null,
	dataloss_violations bigint not null,
	duplicate_violations bigint not null,
	redelivered_ordering_violations bigint not null,
	redelivered_duplicate_violations bigint not null,
    connection_availability numeric(5, 2) not null,
    disconnection_periods integer not null,
    max_disconnection_ms integer not null,
    consume_availability numeric(5, 2) not null,
    no_consume_periods integer not null,
    max_noconsume_ms integer not null
);