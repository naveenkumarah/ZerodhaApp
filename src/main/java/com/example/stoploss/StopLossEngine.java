package com.example.stoploss;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Initial stop placement and R-multiple trailing rules. Stops only ever move in the trade's favour.
 *
 * Rules: 1R -> move to cost; 2R -> lock 1R; 3R -> trail EMA50; 5R -> trail SuperTrend.
 * EMA50 / SuperTrend values are supplied by the caller from the market-data feed.
 */
@Service
public class StopLossEngine {

    public enum Direction { LONG, SHORT }

    public enum Method { FIXED_POINTS, PERCENTAGE, ATR }

    public record Adjustment(BigDecimal stopLoss, BigDecimal rMultiple, String rule,
                             boolean modified, boolean exitTriggered) {}

    public record EvaluateRequest(Direction direction, BigDecimal entryPrice, BigDecimal initialStop,
                                  BigDecimal currentStop, BigDecimal lastPrice,
                                  BigDecimal ema50, BigDecimal superTrend) {}

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public BigDecimal initialStop(Direction direction, BigDecimal entry, Method method, BigDecimal value,
                                  BigDecimal atr) {
        require(entry != null && entry.signum() > 0, "entry must be positive");
        require(value != null && value.signum() > 0, "value must be positive");
        BigDecimal distance = switch (method) {
            case FIXED_POINTS -> value;
            case PERCENTAGE -> entry.multiply(value).divide(HUNDRED, 4, RoundingMode.HALF_UP);
            case ATR -> {
                require(atr != null && atr.signum() > 0, "atr must be positive for ATR stops");
                yield atr.multiply(value);
            }
        };
        BigDecimal stop = direction == Direction.LONG ? entry.subtract(distance) : entry.add(distance);
        return stop.setScale(2, RoundingMode.HALF_UP);
    }

    public Adjustment evaluate(EvaluateRequest r) {
        require(r.direction() != null && r.entryPrice() != null && r.initialStop() != null
                && r.currentStop() != null && r.lastPrice() != null, "missing required fields");
        BigDecimal risk = r.entryPrice().subtract(r.initialStop()).abs();
        require(risk.signum() > 0, "initialStop must differ from entry");

        boolean longSide = r.direction() == Direction.LONG;
        BigDecimal favourable = longSide ? r.lastPrice().subtract(r.entryPrice())
                : r.entryPrice().subtract(r.lastPrice());
        BigDecimal rMultiple = favourable.divide(risk, 2, RoundingMode.HALF_UP);
        double rr = rMultiple.doubleValue();

        BigDecimal stop = r.currentStop();
        String rule = "NONE";

        if (rr >= 1) {
            stop = best(longSide, stop, r.entryPrice());
            rule = "MOVE_TO_COST";
        }
        if (rr >= 2) {
            BigDecimal lock = longSide ? r.entryPrice().add(risk) : r.entryPrice().subtract(risk);
            stop = best(longSide, stop, lock);
            rule = "LOCK_1R";
        }
        if (rr >= 3 && r.ema50() != null) {
            stop = best(longSide, stop, r.ema50());
            rule = "TRAIL_EMA50";
        }
        if (rr >= 5 && r.superTrend() != null) {
            stop = best(longSide, stop, r.superTrend());
            rule = "TRAIL_SUPERTREND";
        }

        boolean modified = stop.compareTo(r.currentStop()) != 0;
        boolean exit = longSide ? r.lastPrice().compareTo(stop) <= 0 : r.lastPrice().compareTo(stop) >= 0;
        return new Adjustment(stop.setScale(2, RoundingMode.HALF_UP), rMultiple, rule, modified, exit);
    }

    private static BigDecimal best(boolean longSide, BigDecimal current, BigDecimal candidate) {
        if (longSide) {
            return candidate.compareTo(current) > 0 ? candidate : current;
        }
        return candidate.compareTo(current) < 0 ? candidate : current;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}

