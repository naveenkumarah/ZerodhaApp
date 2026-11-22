package com.example.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import com.example.risk.RiskStateService;

/** Performance metrics over closed trades. Pure functions, no persistence. */
public final class AnalyticsCalculator {

    private AnalyticsCalculator() {}

    public record ClosedTrade(Instant closedAt, String strategy, BigDecimal pnl) {}

    public record Summary(
            int trades,
            BigDecimal winRate,
            BigDecimal profitFactor,
            BigDecimal expectancy,
            BigDecimal averageWinner,
            BigDecimal averageLoser,
            BigDecimal riskRewardRatio,
            BigDecimal maxDrawdown,
            BigDecimal sharpeRatio,
            BigDecimal totalPnl,
            List<BigDecimal> equityCurve,
            Map<String, BigDecimal> monthlyPnl,
            Map<String, BigDecimal> pnlByStrategy) {}

    public static Summary summarize(List<ClosedTrade> input) {
        List<ClosedTrade> trades = input.stream()
                .sorted(Comparator.comparing(ClosedTrade::closedAt))
                .toList();
        int n = trades.size();
        if (n == 0) {
            return new Summary(0, null, null, null, null, null, null, null, null, BigDecimal.ZERO,
                    List.of(), Map.of(), Map.of());
        }

        List<BigDecimal> pnls = trades.stream().map(ClosedTrade::pnl).toList();
        List<BigDecimal> wins = pnls.stream().filter(p -> p.signum() > 0).toList();
        List<BigDecimal> losses = pnls.stream().filter(p -> p.signum() < 0).toList();

        BigDecimal total = sum(pnls);
        BigDecimal grossProfit = sum(wins);
        BigDecimal grossLoss = sum(losses).abs();

        BigDecimal winRate = BigDecimal.valueOf(wins.size() * 100L)
                .divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);
        BigDecimal profitFactor = grossLoss.signum() == 0 ? null
                : grossProfit.divide(grossLoss, 2, RoundingMode.HALF_UP);
        BigDecimal avgWin = wins.isEmpty() ? BigDecimal.ZERO
                : grossProfit.divide(BigDecimal.valueOf(wins.size()), 2, RoundingMode.HALF_UP);
        BigDecimal avgLoss = losses.isEmpty() ? BigDecimal.ZERO
                : sum(losses).divide(BigDecimal.valueOf(losses.size()), 2, RoundingMode.HALF_UP);
        BigDecimal riskReward = avgLoss.signum() == 0 ? null
                : avgWin.divide(avgLoss.abs(), 2, RoundingMode.HALF_UP);
        BigDecimal expectancy = total.divide(BigDecimal.valueOf(n), 2, RoundingMode.HALF_UP);

        List<BigDecimal> equity = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDd = BigDecimal.ZERO;
        for (BigDecimal p : pnls) {
            running = running.add(p);
            equity.add(running);
            peak = running.max(peak);
            maxDd = maxDd.max(peak.subtract(running));
        }

        Map<String, BigDecimal> monthly = new TreeMap<>();
        trades.forEach(t -> monthly.merge(
                YearMonth.from(t.closedAt().atZone(RiskStateService.MARKET_ZONE)).toString(),
                t.pnl(), BigDecimal::add));

        Map<String, BigDecimal> byStrategy = trades.stream().collect(Collectors.toMap(
                ClosedTrade::strategy, ClosedTrade::pnl, BigDecimal::add, TreeMap::new));

        return new Summary(
                n, winRate, profitFactor, expectancy, avgWin, avgLoss, riskReward,
                maxDd.setScale(2, RoundingMode.HALF_UP), sharpe(pnls), total,
                equity, monthly, byStrategy);
    }

    /** Per-trade Sharpe (mean / sample stdev of trade P&L). Not annualised. */
    static BigDecimal sharpe(List<BigDecimal> pnls) {
        int n = pnls.size();
        if (n < 2) {
            return null;
        }
        double mean = pnls.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double variance = pnls.stream().mapToDouble(p -> Math.pow(p.doubleValue() - mean, 2)).sum() / (n - 1);
        if (variance == 0) {
            return null;
        }
        return BigDecimal.valueOf(mean / Math.sqrt(variance)).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

