package com.example.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Expiry payoff analysis for any combination of option legs.
 * Payoff is piecewise linear with kinks at strikes, so extremes and breakevens are found exactly
 * from the strikes and zero, plus the unbounded tail implied by the net call exposure.
 */
public final class PayoffAnalyzer {

    private static final int CURVE_STEPS = 60;

    private PayoffAnalyzer() {}

    public record PayoffPoint(BigDecimal price, BigDecimal pnl) {}

    /** netPremium > 0 means net credit. maxProfit/maxLoss are null when unlimited. */
    public record PayoffResult(BigDecimal netPremium, BigDecimal maxProfit, BigDecimal maxLoss,
                               boolean unlimitedProfit, boolean unlimitedLoss,
                               List<BigDecimal> breakevens, List<PayoffPoint> curve) {}

    public static BigDecimal totalPnl(List<OptionLeg> legs, BigDecimal spot) {
        BigDecimal total = BigDecimal.ZERO;
        for (OptionLeg leg : legs) {
            total = total.add(leg.pnlAt(spot));
        }
        return total;
    }

    public static PayoffResult analyze(List<OptionLeg> legs) {
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("At least one leg is required");
        }

        BigDecimal netPremium = BigDecimal.ZERO;
        for (OptionLeg leg : legs) {
            netPremium = netPremium.add(leg.premium().multiply(BigDecimal.valueOf((long) -leg.sign() * leg.quantity())));
        }

        // Candidate points: 0 and every strike (TreeSet dedupes by compareTo, so 100 == 100.00).
        TreeSet<BigDecimal> candidates = new TreeSet<>();
        candidates.add(BigDecimal.ZERO);
        legs.forEach(l -> candidates.add(l.strike()));
        List<BigDecimal> xs = new ArrayList<>(candidates);

        BigDecimal max = null;
        BigDecimal min = null;
        for (BigDecimal x : xs) {
            BigDecimal p = totalPnl(legs, x);
            max = (max == null || p.compareTo(max) > 0) ? p : max;
            min = (min == null || p.compareTo(min) < 0) ? p : min;
        }

        // Slope beyond the highest strike: only call legs contribute.
        long slope = legs.stream().filter(l -> l.type() == OptionLeg.Type.CE)
                .mapToLong(l -> (long) l.sign() * l.quantity()).sum();
        boolean unlimitedProfit = slope > 0;
        boolean unlimitedLoss = slope < 0;

        TreeSet<BigDecimal> breakevens = new TreeSet<>();
        for (int i = 0; i < xs.size() - 1; i++) {
            BigDecimal a = xs.get(i);
            BigDecimal b = xs.get(i + 1);
            BigDecimal pa = totalPnl(legs, a);
            BigDecimal pb = totalPnl(legs, b);
            if (pa.signum() == 0) {
                breakevens.add(a);
            } else if (pa.signum() * pb.signum() < 0) {
                BigDecimal t = pa.negate().divide(pb.subtract(pa), 10, RoundingMode.HALF_UP);
                breakevens.add(a.add(b.subtract(a).multiply(t)).setScale(2, RoundingMode.HALF_UP));
            }
        }
        BigDecimal last = xs.get(xs.size() - 1);
        BigDecimal pLast = totalPnl(legs, last);
        if (pLast.signum() == 0) {
            breakevens.add(last);
        } else if (slope != 0 && pLast.signum() * slope < 0) {
            BigDecimal root = last.subtract(pLast.divide(BigDecimal.valueOf(slope), 10, RoundingMode.HALF_UP));
            breakevens.add(root.setScale(2, RoundingMode.HALF_UP));
        }

        BigDecimal lo = xs.get(0).compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO
                : xs.get(0).multiply(new BigDecimal("0.8"));
        BigDecimal hi = last.multiply(new BigDecimal("1.2"));
        BigDecimal span = hi.subtract(lo);
        List<PayoffPoint> curve = new ArrayList<>();
        for (int i = 0; i <= CURVE_STEPS; i++) {
            BigDecimal price = lo.add(span.multiply(BigDecimal.valueOf(i))
                    .divide(BigDecimal.valueOf(CURVE_STEPS), 4, RoundingMode.HALF_UP)).setScale(2, RoundingMode.HALF_UP);
            curve.add(new PayoffPoint(price, totalPnl(legs, price).setScale(2, RoundingMode.HALF_UP)));
        }

        return new PayoffResult(
                netPremium.setScale(2, RoundingMode.HALF_UP),
                unlimitedProfit ? null : max.setScale(2, RoundingMode.HALF_UP),
                unlimitedLoss ? null : min.setScale(2, RoundingMode.HALF_UP),
                unlimitedProfit,
                unlimitedLoss,
                List.copyOf(breakevens),
                curve);
    }
}

