package com.example.strategy;

import java.util.List;

/** Standard normal distribution helpers (Abramowitz-Stegun erf approximation, |error| < 1.5e-7). */
public final class NormalDistribution {

    private NormalDistribution() {}

    public static double cdf(double x) {
        double z = Math.abs(x) / Math.sqrt(2);
        double t = 1.0 / (1.0 + 0.3275911 * z);
        double poly = t * (0.254829592 + t * (-0.284496736 + t * (1.421413741
                + t * (-1.453152027 + t * 1.061405429))));
        double erf = 1 - poly * Math.exp(-z * z);
        return 0.5 * (1 + Math.signum(x) * erf);
    }

    public static double pdf(double x) {
        return Math.exp(-0.5 * x * x) / Math.sqrt(2 * Math.PI);
    }
}

