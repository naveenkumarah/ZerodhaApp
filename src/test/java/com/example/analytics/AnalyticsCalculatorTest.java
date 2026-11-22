package com.example.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.analytics.AnalyticsCalculator.ClosedTrade;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalyticsCalculatorTest {

    @Test
    void computesCoreMetricsFromClosedTrades() {
        Instant t = Instant.parse("2026-11-02T10:00:00Z");
        var summary = AnalyticsCalculator.summarize(List.of(
                new ClosedTrade(t, "IRON_CONDOR", new BigDecimal("1000")),
                new ClosedTrade(t.plusSeconds(3600), "BULL_PUT", new BigDecimal("-500")),
                new ClosedTrade(t.plusSeconds(7200), "IRON_CONDOR", new BigDecimal("2000")),
                new ClosedTrade(t.plusSeconds(10800), "BULL_PUT", new BigDecimal("-1000"))));

        assertThat(summary.trades()).isEqualTo(4);
        assertThat(summary.winRate()).isEqualByComparingTo("50.00");
        assertThat(summary.profitFactor()).isEqualByComparingTo("2.00");
        assertThat(summary.expectancy()).isEqualByComparingTo("375.00");
        assertThat(summary.averageWinner()).isEqualByComparingTo("1500.00");
        assertThat(summary.averageLoser()).isEqualByComparingTo("-750.00");
        assertThat(summary.riskRewardRatio()).isEqualByComparingTo("2.00");
        assertThat(summary.maxDrawdown()).isEqualByComparingTo("1000.00");
        assertThat(summary.totalPnl()).isEqualByComparingTo("1500");
        assertThat(summary.pnlByStrategy()).containsEntry("IRON_CONDOR", new BigDecimal("3000"));
    }

    @Test
    void emptyHistoryReturnsZeroedSummary() {
        var summary = AnalyticsCalculator.summarize(List.of());
        assertThat(summary.trades()).isZero();
        assertThat(summary.winRate()).isNull();
    }
}

