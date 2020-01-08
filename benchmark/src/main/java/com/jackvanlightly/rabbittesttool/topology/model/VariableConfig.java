package com.jackvanlightly.rabbittesttool.topology.model;

import java.util.*;

public class VariableConfig {
    private VariableDimension dimension;
    private VariableDimension[] multiDimensions;
    private List<Double> values;
    private List<Double[]> multiValues;
    private ValueType valueType;
    private int stepDurationSeconds;
    private int stepRampUpSeconds;
    private String group;
    private StepOverride stepOverride;

    public VariableConfig() {
        values = new ArrayList<>();
        multiValues = new ArrayList<>();
        valueType = ValueType.Value;
    }

    public VariableDimension getDimension() {
        return dimension;
    }

    public VariableDimension[] getMultiDimensions() {
        return multiDimensions;
    }

    public boolean hasMultiDimension(VariableDimension variableDimension) {
        for(int i=0; i<multiDimensions.length; i++) {
            if(multiDimensions[i] == variableDimension)
                return true;
        }

        return false;
    }

    public void setMultiDimensions(VariableDimension[] multiDimensions) {
        this.multiDimensions = multiDimensions;
    }

    public void setDimension(VariableDimension dimension) {
        this.dimension = dimension;
    }

    public List<Double> getValues() {
        return values;
    }

    public void setValues(List<Double> values) {
        this.values = values;
    }

    public List<Double[]> getMultiValues() {
        return multiValues;
    }

    public void setMultiValues(List<Double[]> multiValues) {
        this.multiValues = multiValues;
    }

    public ValueType getValueType() {
        return valueType;
    }

    public void setValueType(ValueType valueType) {
        this.valueType = valueType;
    }

    public Double getMaxScale() {
        if(this.values.isEmpty())
            return 0.0;

        return this.values.stream().max(Double::compareTo).get();
    }

    public Map<VariableDimension, Double> getMaxScales() {
        Map<VariableDimension, Double> maxScales = new HashMap<VariableDimension, Double>();
        if(this.multiValues.isEmpty())
            return maxScales;

        for(int i=0; i<this.multiDimensions.length; i++) {
            int vd = i;
            Double max = this.multiValues.stream()
                    .map(x -> x[vd])
                    .max(Double::compareTo)
                    .get();
            maxScales.put(this.multiDimensions[i], max);

        }
        return maxScales;
    }

    public Double getMaxScale(VariableDimension dimension) {
        Map<VariableDimension, Double> maxScales = getMaxScales();
        if(maxScales.containsKey(dimension))
            return maxScales.get(dimension);

        return 0.0;
    }

    public int getStepDurationSeconds() {
        if(stepOverride != null && stepOverride.hasStepSeconds())
            return stepOverride.getStepSeconds();

        return stepDurationSeconds;
    }

    public void setStepDurationSeconds(int stepDurationSeconds) {
        this.stepDurationSeconds = stepDurationSeconds;
    }

    public int getStepRampUpSeconds() {
        return stepRampUpSeconds;
    }

    public void setStepRampUpSeconds(int stepRampUpSeconds) {
        this.stepRampUpSeconds = stepRampUpSeconds;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public int getStepCount() {
        return Math.max(this.values.size(), this.multiValues.size());
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
