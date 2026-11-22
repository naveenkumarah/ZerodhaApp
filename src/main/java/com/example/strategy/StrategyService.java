package com.example.strategy;

import com.example.strategy.OptionLeg.Side;
import com.example.strategy.OptionLeg.Type;
import com.example.strategy.StrategyRequest.LegInput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;

/** Builds and analyses predefined option strategies. Validates leg topology before analysis. */
@Service
public class StrategyService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    /** Fallback margin estimate (fraction of notional) used only when the risk is unlimited. */
    private static final BigDecimal ESTIMATED_MARGIN_RATE = new BigDecimal("0.15");

    public StrategyAnalysis ironCondor(StrategyRequest r) {
        requireLegCount(r, 4, "IRON_CONDOR");
        LegInput sc = pick(r, Type.CE, Side.SELL);
        LegInput lc = pick(r, Type.CE, Side.BUY);
        LegInput sp = pick(r, Type.PE, Side.SELL);
        LegInput lp = pick(r, Type.PE, Side.BUY);
        require(lc.strike().compareTo(sc.strike()) > 0, "Long call must be above short call");
        require(sc.strike().compareTo(sp.strike()) > 0, "Short call must be above short put");
        require(sp.strike().compareTo(lp.strike()) > 0, "Long put must be below short put");
        require(sc.strike().compareTo(r.spot()) >= 0 && sp.strike().compareTo(r.spot()) <= 0,
                "Short strikes must be out-of-the-money");
        return analyze("IRON_CONDOR", r);
    }

    /** Same four-leg topology as an iron condor; the difference is the wider, far-OTM protective wings. */
    public StrategyAnalysis hedgedStrangle(StrategyRequest r) {
        requireLegCount(r, 4, "HEDGED_STRANGLE");
        LegInput sc = pick(r, Type.CE, Side.SELL);
        LegInput lc = pick(r, Type.CE, Side.BUY);
        LegInput sp = pick(r, Type.PE, Side.SELL);
        LegInput lp = pick(r, Type.PE, Side.BUY);
        require(lc.strike().compareTo(sc.strike()) > 0, "Far call hedge must be above short call");
        require(lp.strike().compareTo(sp.strike()) < 0, "Far put hedge must be below short put");
        require(sc.strike().compareTo(r.spot()) > 0 && sp.strike().compareTo(r.spot()) < 0,
                "Short strangle strikes must be out-of-the-money");
        return analyze("HEDGED_STRANGLE", r);
    }

    public StrategyAnalysis ironFly(StrategyRequest r) {
        requireLegCount(r, 4, "IRON_FLY");
        LegInput sc = pick(r, Type.CE, Side.SELL);
        LegInput sp = pick(r, Type.PE, Side.SELL);
        LegInput lc = pick(r, Type.CE, Side.BUY);
        LegInput lp = pick(r, Type.PE, Side.BUY);
        require(sc.strike().compareTo(sp.strike()) == 0, "Short call and put must share the ATM strike");
        require(sc.strike().subtract(r.spot()).abs().compareTo(r.spot().multiply(new BigDecimal("0.02"))) <= 0,
                "Short straddle strike must be within 2% of spot (ATM)");
        require(lc.strike().compareTo(sc.strike()) > 0, "Long call wing must be above ATM");
        require(lp.strike().compareTo(sp.strike()) < 0, "Long put wing must be below ATM");
        return analyze("IRON_FLY", r);
    }

    public StrategyAnalysis bullPut(StrategyRequest r) {
        requireLegCount(r, 2, "BULL_PUT");
        LegInput sp = pick(r, Type.PE, Side.SELL);
        LegInput lp = pick(r, Type.PE, Side.BUY);
        require(lp.strike().compareTo(sp.strike()) < 0, "Long put must be below short put");
        require(sp.strike().compareTo(r.spot()) <= 0, "Short put should be at or below spot");
        return analyze("BULL_PUT_SPREAD", r);
    }

    public StrategyAnalysis bearCall(StrategyRequest r) {
        requireLegCount(r, 2, "BEAR_CALL");
        LegInput sc = pick(r, Type.CE, Side.SELL);
        LegInput lc = pick(r, Type.CE, Side.BUY);
        require(lc.strike().compareTo(sc.strike()) > 0, "Long call must be above short call");
        require(sc.strike().compareTo(r.spot()) >= 0, "Short call should be at or above spot");
        return analyze("BEAR_CALL_SPREAD", r);
    }

    private StrategyAnalysis analyze(String name, StrategyRequest r) {
        int qty = r.lots() * r.lotSize();
        List<OptionLeg> legs = r.legs().stream()
                .map(l -> new OptionLeg(l.type(), l.side(), l.strike(), l.premium(), qty))
                .toList();
        PayoffAnalyzer.PayoffResult pr = PayoffAnalyzer.analyze(legs);
        BigDecimal spot = r.spot();

        BigDecimal margin = pr.maxLoss() != null
                ? pr.maxLoss().abs()
                : spot.multiply(BigDecimal.valueOf(qty)).multiply(ESTIMATED_MARGIN_RATE).setScale(2, RoundingMode.HALF_UP);

        BigDecimal roi = null;
        if (pr.maxProfit() != null && margin.signum() > 0) {
            roi = pr.maxProfit().multiply(HUNDRED).divide(margin, 2, RoundingMode.HALF_UP);
        }
        BigDecimal capitalUsed = (r.capital() != null && margin.signum() > 0)
                ? margin.multiply(HUNDRED).divide(r.capital(), 2, RoundingMode.HALF_UP)
                : null;

        double sigma = r.ivPercent().doubleValue() / 100.0 * Math.sqrt(r.daysToExpiry() / 365.0);
        BigDecimal pop = probabilityOfProfit(legs, pr.breakevens(), spot.doubleValue(), sigma);

        return new StrategyAnalysis(
                name,
                spot,
                qty,
                pr.netPremium(),
                pr.maxProfit(),
                pr.maxLoss(),
                pr.unlimitedProfit(),
                pr.unlimitedLoss(),
                margin,
                roi,
                pop,
                capitalUsed,
                pr.breakevens(),
                Greeks.of(legs, spot, r.ivPercent(), r.daysToExpiry()),
                pr.curve());
    }

    /**
     * Probability that expiry price lands in a profitable interval, assuming lognormal terminal price
     * with sigma = IV * sqrt(T). Drift is ignored (approximation).
     */
    static BigDecimal probabilityOfProfit(List<OptionLeg> legs, List<BigDecimal> breakevens,
                                          double spot, double sigma) {
        if (sigma <= 0) {
            return null;
        }
        List<Double> points = new java.util.ArrayList<>();
        points.add(0.0);
        breakevens.forEach(b -> points.add(b.doubleValue()));

        double total = 0;
        for (int i = 0; i < points.size(); i++) {
            double a = points.get(i);
            boolean last = i == points.size() - 1;
            double b = last ? Double.POSITIVE_INFINITY : points.get(i + 1);
            double mid = last ? a * 1.5 + 1 : (a + b) / 2;
            if (PayoffAnalyzer.totalPnl(legs, BigDecimal.valueOf(mid)).signum() > 0) {
                total += cdfAtPrice(b, spot, sigma) - cdfAtPrice(a, spot, sigma);
            }
        }
        return BigDecimal.valueOf(total * 100).setScale(2, RoundingMode.HALF_UP);
    }

    private static double cdfAtPrice(double price, double spot, double sigma) {
        if (price <= 0) {
            return 0;
        }
        if (Double.isInfinite(price)) {
            return 1;
        }
        return NormalDistribution.cdf(Math.log(price / spot) / sigma);
    }

    private static void requireLegCount(StrategyRequest r, int expected, String name) {
        require(r.legs().size() == expected, name + " requires exactly " + expected + " legs");
    }

    private static LegInput pick(StrategyRequest r, Type type, Side side) {
        List<LegInput> matches = r.legs().stream()
                .filter(l -> l.type() == type && l.side() == side)
                .toList();
        require(matches.size() == 1, "Expected exactly one " + side + " " + type + " leg");
        return matches.get(0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}

