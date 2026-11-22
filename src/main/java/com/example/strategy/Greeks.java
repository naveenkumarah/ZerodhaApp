package com.example.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Black-Scholes Greeks aggregated across legs, signed by side and weighted by quantity. */
public record Greeks(BigDecimal delta, BigDecimal gamma, BigDecimal theta, BigDecimal vega) {

    /** Risk-free rate used for pricing. */
    private static final double RATE = 0.065;

    public static Greeks of(List<OptionLeg> legs, BigDecimal spot, BigDecimal ivPercent, int daysToExpiry) {
        double s = spot.doubleValue();
        double sigma = ivPercent.doubleValue() / 100.0;
        double t = daysToExpiry / 365.0;
        double sqrtT = Math.sqrt(t);

        double delta = 0;
        double gamma = 0;
        double theta = 0;
        double vega = 0;
        for (OptionLeg leg : legs) {
            double k = leg.strike().doubleValue();
            double d1 = (Math.log(s / k) + (RATE + 0.5 * sigma * sigma) * t) / (sigma * sqrtT);
            double d2 = d1 - sigma * sqrtT;
            double pdfD1 = NormalDistribution.pdf(d1);
            double discount = k * Math.exp(-RATE * t);
            double w = (double) leg.sign() * leg.quantity();

            double legDelta;
            double legTheta;
            if (leg.type() == OptionLeg.Type.CE) {
                legDelta = NormalDistribution.cdf(d1);
                legTheta = (-s * pdfD1 * sigma / (2 * sqrtT) - RATE * discount * NormalDistribution.cdf(d2)) / 365.0;
            } else {
                legDelta = NormalDistribution.cdf(d1) - 1;
                legTheta = (-s * pdfD1 * sigma / (2 * sqrtT) + RATE * discount * NormalDistribution.cdf(-d2)) / 365.0;
            }
            delta += w * legDelta;
            gamma += w * pdfD1 / (s * sigma * sqrtT);
            theta += w * legTheta;
            vega += w * s * pdfD1 * sqrtT / 100.0;
        }
        return new Greeks(scale(delta, 4), scale(gamma, 6), scale(theta, 2), scale(vega, 2));
    }

    private static BigDecimal scale(double value, int places) {
        return BigDecimal.valueOf(value).setScale(places, RoundingMode.HALF_UP);
    }
}

