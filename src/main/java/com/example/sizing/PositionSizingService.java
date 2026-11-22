package com.example.sizing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Position sizing. Core formula: quantity = riskBudget / riskPerShare, then capped by max exposure
 * and rounded down to whole lots.
 */
@Service
public class PositionSizingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public enum Method { FIXED_RISK, PERCENTAGE_RISK, VOLATILITY, ATR, KELLY }

    public record Request(
            Method method,
            BigDecimal capital,
            BigDecimal riskPercent,
            BigDecimal fixedRiskAmount,
            BigDecimal entryPrice,
            BigDecimal stopLoss,
            BigDecimal atr,
            BigDecimal atrMultiplier,
            BigDecimal volatilityPercent,
            BigDecimal winRate,
            BigDecimal avgWin,
            BigDecimal avgLoss,
            Integer lotSize,
            BigDecimal maxExposurePercent) {}

    public record Result(
            Method method,
            int quantity,
            BigDecimal riskPerShare,
            BigDecimal riskAmount,
            BigDecimal positionValue,
            BigDecimal exposurePercent) {}

    public Result calculate(Request r) {
        require(r.method() != null, "method is required");
        require(r.capital() != null && r.capital().signum() > 0, "capital must be positive");
        require(r.entryPrice() != null && r.entryPrice().signum() > 0, "entryPrice must be positive");

        BigDecimal budget = switch (r.method()) {
            case FIXED_RISK -> {
                require(r.fixedRiskAmount() != null && r.fixedRiskAmount().signum() > 0,
                        "fixedRiskAmount must be positive");
                yield r.fixedRiskAmount();
            }
            case KELLY -> {
                require(r.riskPercent() != null && r.riskPercent().signum() > 0, "riskPercent must be positive");
                BigDecimal kelly = kellyFraction(r.winRate(), r.avgWin(), r.avgLoss());
                // Half-Kelly, never more than the configured per-trade risk.
                BigDecimal fraction = kelly.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO
                        : kelly.divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP)
                                .min(r.riskPercent().divide(HUNDRED, 6, RoundingMode.HALF_UP));
                yield r.capital().multiply(fraction);
            }
            default -> {
                require(r.riskPercent() != null && r.riskPercent().signum() > 0, "riskPercent must be positive");
                yield r.capital().multiply(r.riskPercent()).divide(HUNDRED, 4, RoundingMode.HALF_UP);
            }
        };

        BigDecimal riskPerShare = switch (r.method()) {
            case ATR -> {
                require(r.atr() != null && r.atr().signum() > 0, "atr must be positive");
                yield r.atr().multiply(multiplier(r.atrMultiplier()));
            }
            case VOLATILITY -> {
                require(r.volatilityPercent() != null && r.volatilityPercent().signum() > 0,
                        "volatilityPercent must be positive");
                yield r.entryPrice().multiply(r.volatilityPercent()).divide(HUNDRED, 4, RoundingMode.HALF_UP)
                        .multiply(multiplier(r.atrMultiplier()));
            }
            default -> {
                require(r.stopLoss() != null, "stopLoss is required");
                BigDecimal distance = r.entryPrice().subtract(r.stopLoss()).abs();
                require(distance.signum() > 0, "entryPrice and stopLoss must differ");
                yield distance;
            }
        };

        long qty = budget.divide(riskPerShare, 0, RoundingMode.FLOOR).longValueExact();

        if (r.maxExposurePercent() != null) {
            BigDecimal maxNotional = r.capital().multiply(r.maxExposurePercent()).divide(HUNDRED, 4, RoundingMode.HALF_UP);
            qty = Math.min(qty, maxNotional.divide(r.entryPrice(), 0, RoundingMode.FLOOR).longValueExact());
        }

        if (r.lotSize() != null && r.lotSize() > 1) {
            qty = (qty / r.lotSize()) * r.lotSize();
        }
        int quantity = Math.toIntExact(Math.max(qty, 0));

        BigDecimal riskAmount = riskPerShare.multiply(BigDecimal.valueOf(quantity));
        BigDecimal positionValue = r.entryPrice().multiply(BigDecimal.valueOf(quantity));
        return new Result(
                r.method(),
                quantity,
                riskPerShare.setScale(2, RoundingMode.HALF_UP),
                riskAmount.setScale(2, RoundingMode.HALF_UP),
                positionValue.setScale(2, RoundingMode.HALF_UP),
                positionValue.multiply(HUNDRED).divide(r.capital(), 2, RoundingMode.HALF_UP));
    }

    /** Kelly fraction f* = W - (1 - W) / R, where R = avgWin / avgLoss. Returns <= 0 when there is no edge. */
    public static BigDecimal kellyFraction(BigDecimal winRate, BigDecimal avgWin, BigDecimal avgLoss) {
        require(winRate != null && winRate.signum() >= 0 && winRate.compareTo(BigDecimal.ONE) <= 0,
                "winRate must be between 0 and 1");
        require(avgWin != null && avgWin.signum() > 0, "avgWin must be positive");
        require(avgLoss != null && avgLoss.signum() > 0, "avgLoss must be positive (magnitude)");
        BigDecimal reward = avgWin.divide(avgLoss, 8, RoundingMode.HALF_UP);
        return winRate.subtract(BigDecimal.ONE.subtract(winRate).divide(reward, 8, RoundingMode.HALF_UP));
    }

    private static BigDecimal multiplier(BigDecimal m) {
        return m == null ? BigDecimal.ONE : m;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}

