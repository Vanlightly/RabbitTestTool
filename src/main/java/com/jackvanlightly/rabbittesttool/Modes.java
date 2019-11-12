package com.jackvanlightly.rabbittesttool;

public class Modes {
    public static final String SimpleBenchmark = "simple-benchmark";
    public static final String LoggedBenchmark = "logged-benchmark";
    public static final String Model = "model";
    public static final String Comparison = "comparison";
    public static final String RecoveryTime = "recovery-time";

    public static String getModes() {
        return SimpleBenchmark + "," + LoggedBenchmark + "," + Model + "," + Comparison + "," + RecoveryTime;
    }
}
