package com.jackvanlightly.rabbittesttool.comparer;

public class Comparison {
    private String measurement;
    private Measurement measurement1;
    private Measurement measurement2;

    private double minValueDiff;
    private double minValueDiffPercent;
    private double maxValueDiff;
    private double maxValueDiffPercent;
    private double avgValueDiff;
    private double avgValueDiffPercent;
    private double stdDevValueDiff;
    private double stdDevValueDiffPercent;

    public Comparison(String measurement) {
        this.measurement = measurement;
        this.measurement1 = new Measurement();
        this.measurement2 = new Measurement();
    }

    public void addMeasurementValue(int measurement, double value) {
        if(measurement == 1)
            this.measurement1.addValue(value);
        else if(measurement == 2)
            this.measurement2.addValue(value);
        else
            throw new RuntimeException("Only supports two measurement comparisons");
    }

    public void compare() {
        measurement1.computeStats();
        measurement2.computeStats();

        minValueDiff = measurement2.getMinValue() - measurement1.getMinValue();
        if(minValueDiff == 0)
            minValueDiffPercent = 0;
        else
            minValueDiffPercent = (minValueDiff / measurement1.getMinValue()) * 100.0;

        maxValueDiff = measurement2.getMaxValue() - measurement1.getMaxValue();
        if(maxValueDiff == 0)
            maxValueDiffPercent = 0;
        else
            maxValueDiffPercent = (maxValueDiff / measurement1.getMaxValue()) * 100.0;

        avgValueDiff = measurement2.getAvgValue() - measurement1.getAvgValue();
        if(avgValueDiff == 0)
            avgValueDiffPercent = 0;
        else
            avgValueDiffPercent = (avgValueDiff / measurement1.getAvgValue()) * 100.0;

        stdDevValueDiff = measurement2.getStdDevValue() - measurement1.getStdDevValue();
        if(stdDevValueDiff == 0)
            stdDevValueDiffPercent = 0;
        else
            stdDevValueDiffPercent = (stdDevValueDiff / measurement1.getStdDevValue()) * 100.0;
    }

    public String getMeasurementName() {
        return measurement;
    }

    public Measurement getMeasurement1() {
        return measurement1;
    }

    public Measurement getMeasurement2() {
        return measurement2;
    }

    public double getMinValueDiff() {
        return minValueDiff;
    }

    public double getMinValueDiffPercent() {
        return minValueDiffPercent;
    }

    public double getMaxValueDiff() {
        return maxValueDiff;
    }

    public double getMaxValueDiffPercent() {
        return maxValueDiffPercent;
    }

    public double getAvgValueDiff() {
        return avgValueDiff;
    }

    public double getAvgValueDiffPercent() {
        return avgValueDiffPercent;
    }

    public double getStdDevValueDiff() {
        return stdDevValueDiff;
    }

    public double getStdDevValueDiffPercent() {
        return stdDevValueDiffPercent;
    }
}
