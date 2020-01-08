package com.jackvanlightly.rabbittesttool.comparer;

import java.util.ArrayList;
import java.util.List;

public class Measurement {
    private double minValue;
    private double maxValue;
    private double avgValue;
    private double stdDevValue;
    private double valueRange;
    private double valueRangePercent;
    private List<Double> values;

    public Measurement() {
        this.values = new ArrayList<>();
    }

    public void addValue(double value) {
        this.values.add(value);
    }

    public void computeStats() {
        if(!values.isEmpty()) {
            minValue = values.stream().min(Double::compareTo).get();
            maxValue = values.stream().max(Double::compareTo).get();
            avgValue = mean(values);
            stdDevValue = stdDev(values);
            valueRange = maxValue - minValue;
            valueRangePercent = (valueRange / maxValue) * 100.0;
        }
    }

    private double stdDev(List<Double> v) {
        return Math.sqrt(variance(v));
    }

    private double variance(List<Double> v) {
        double mu = mean(v);
        double sumsq = 0.0;
        for (int i = 0; i < v.size(); i++)
            sumsq += sqr(mu - v.get(i));
        return sumsq / (v.size());
    }

    private double sqr(double x) {
        return x * x;
    }

    private double mean(List<Double> v) {
        double tot = 0.0;
        for (int i = 0; i < v.size(); i++)
            tot += v.get(i);
        return tot / v.size();
    }

    public double getMinValue() {
        return minValue;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public double getAvgValue() {
        return avgValue;
    }

    public double getStdDevValue() {
        return stdDevValue;
    }

    public double getValueRange() {
        return valueRange;
    }

    public double getValueRangePercent() {
        return valueRangePercent;
    }

    public List<Double> getValues() {
        return values;
    }
}
