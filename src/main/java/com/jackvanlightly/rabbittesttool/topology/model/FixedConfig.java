package com.jackvanlightly.rabbittesttool.topology.model;

public class FixedConfig {
    private int durationSeconds;
    private int stepRampUpSeconds;
    private StepOverride stepOverride;

    public int getDurationSeconds() {
        if(stepOverride != null && stepOverride.hasStepSeconds())
            return stepOverride.getStepSeconds();

        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public int getStepRampUpSeconds() {
        return stepRampUpSeconds;
    }

    public void setStepRampUpSeconds(int stepRampUpSeconds) {
        this.stepRampUpSeconds = stepRampUpSeconds;
    }

    public int getStepRepeat() {
        if(stepOverride != null && stepOverride.hasStepRepeat())
            return stepOverride.getStepRepeat();

        return 1;
    }

    public void setStepOverride(StepOverride stepOverride) {
        this.stepOverride = stepOverride;
    }
}
