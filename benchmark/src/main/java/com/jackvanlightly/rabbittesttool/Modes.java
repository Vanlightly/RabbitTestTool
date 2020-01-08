package com.jackvanlightly.rabbittesttool;

public class Modes {
    public static final String Benchmark = "benchmark";
    public static final String Model = "model";
    public static final String Comparison = "comparison";

    public static String getModes() {
        return Benchmark + "," + Model + "," + Comparison;
    }
}
