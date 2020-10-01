package com.rabbitmq.orchestrator.model.clients;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class LoadGenConfiguration {
    String mode;
    int gracePeriodSeconds;
    int warmUpSeconds;
    List<Check> checks;

    public LoadGenConfiguration(String mode,
                                int gracePeriodSeconds,
                                int warmUpSeconds,
                                List<Check> checks) {
        this.mode = mode;
        this.gracePeriodSeconds = gracePeriodSeconds;
        this.warmUpSeconds = warmUpSeconds;
        this.checks = checks;
    }

    public LoadGenConfiguration(String mode,
                                int warmUpSeconds) {
        this.mode = mode;
        this.gracePeriodSeconds = 0;
        this.warmUpSeconds = warmUpSeconds;
        this.checks = Arrays.asList(Check.All);
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getGracePeriodSeconds() {
        return gracePeriodSeconds;
    }

    public void setGracePeriodSeconds(int gracePeriodSeconds) {
        this.gracePeriodSeconds = gracePeriodSeconds;
    }

    public int getWarmUpSeconds() {
        return warmUpSeconds;
    }

    public void setWarmUpSeconds(int warmUpSeconds) {
        this.warmUpSeconds = warmUpSeconds;
    }

    public String getChecksStr() {
        return String.join(",", checks.stream().map(x -> String.valueOf(x)).collect(Collectors.toList()));
    }

    public List<Check> getChecks() {
        return checks;
    }

    public void setChecks(List<Check> checks) {
        this.checks = checks;
    }
}
