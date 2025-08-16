package com.lorevault.api.util;

/**
 * Lightweight vector math utilities for retrieval scoring.
 * All methods are null-safe and tolerate length mismatches.
 */
public final class VectorMath {

    private static final double COS_EPS = 1e-12;

    private VectorMath() {}

    public static double dot(double[] a, double[] b) {
        if (a == null || b == null) return 0.0;
        int len = Math.min(a.length, b.length);
        double sum = 0.0;
        for (int i = 0; i < len; i++) sum += a[i] * b[i];
        return sum;
    }

    public static double norm(double[] v) {
        if (v == null) return 0.0;
        double sum = 0.0;
        for (double x : v) sum += x * x;
        return Math.sqrt(sum);
    }

    public static double cosineSimilarity(double[] a, double[] b) {
        double denom = norm(a) * norm(b);
        if (denom == 0.0) return 0.0;
        double value = dot(a, b) / denom;
        // Clamp numerical drift outside [-1,1]
        if (value > 1.0 && value <= 1.0 + COS_EPS) value = 1.0;
        else if (value < -1.0 && value >= -1.0 - COS_EPS) value = -1.0;
        return value;
    }

    public static double l2Distance(double[] a, double[] b) {
        if (a == null || b == null) return Double.NaN;
        int len = Math.min(a.length, b.length);
        double sum = 0.0;
        for (int i = 0; i < len; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return Math.sqrt(sum);
    }
}
