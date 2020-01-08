package com.jackvanlightly.rabbittesttool.metrics;

import com.jackvanlightly.rabbittesttool.CmdArguments;
import io.micrometer.core.instrument.Clock;
import io.micrometer.influx.InfluxConfig;
import io.micrometer.influx.InfluxMeterRegistry;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;

public class InfluxMetrics {

    private MeterRegistry registry;

//    public List<Option> options() {
//        List<Option> options = new ArrayList<>();
//        options.add(new Option("miu", "metrics-influx-uri", true, "the Influx URI"));
//        options.add(new Option("miusr", "metrics-influx-user", true, "The influx user"));
//        options.add(new Option("mipwd", "metrics-influx-password", true, "The influx password"));
//        options.add(new Option("midb", "metrics-influx-database", true, "The influx database name"));
//        options.add(new Option("mii", "metrics-influx-interval", true, "The seconds interval"));
//        return options;
//    }

    public void configure(CmdArguments arguments) {

        InfluxConfig config = new InfluxConfig() {
            @Override
            public Duration step() {
                return Duration.ofSeconds(arguments.getInt("--metrics-influx-interval", 10));
            }

            @Override
            public String db() {
                return arguments.getStr("--metrics-influx-database", "amqp");
            }

            @Override
            public String uri() {
                return arguments.getStr("--metrics-influx-uri", "http://localhost:8086");
            }

            @Override
            public String userName() {
                return arguments.getStr("--metrics-influx-user", "amqp");
            }

            @Override
            public String password() {
                return arguments.getStr("--metrics-influx-password", "amqp");
            }

            @Override
            public String get(String k) {
                return null; // accept the rest of the defaults
            }
        };
        registry = new InfluxMeterRegistry(config, Clock.SYSTEM);
    }

    public MeterRegistry getRegistry() {
        return registry;
    }

    public void close() {
        if (registry != null) {
            registry.close();
        }
    }

    @Override
    public String toString() {
        return "Influx Metrics";
    }

}
