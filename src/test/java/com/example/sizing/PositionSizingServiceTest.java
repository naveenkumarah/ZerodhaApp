package com.example.sizing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.sizing.PositionSizingService.Method;
import com.example.sizing.PositionSizingService.Request;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PositionSizingServiceTest {

    private final PositionSizingService service = new PositionSizingService();

    @Test
    void percentageRiskFollowsCapitalTimesRiskOverStopDistance() {
        // Capital 10,00,000 x 1% = 10,000 budget; entry 250, SL 240 -> 10 per share -> 1000 shares.
        var result = service.calculate(new Request(Method.PERCENTAGE_RISK,
                new BigDecimal("1000000"), new BigDecimal("1"), null,
                new BigDecimal("250"), new BigDecimal("240"),
                null, null, null, null, null, null, null, null));

        assertThat(result.quantity()).isEqualTo(1000);
        assertThat(result.riskPerShare()).isEqualByComparingTo("10.00");
        assertThat(result.riskAmount()).isEqualByComparingTo("10000.00");
        assertThat(result.positionValue()).isEqualByComparingTo("250000.00");
    }

    @Test
    void maxExposureCapsQuantity() {
        // 10% exposure cap on 10,00,000 = 1,00,000 notional / 250 = 400 shares.
        var result = service.calculate(new Request(Method.PERCENTAGE_RISK,
                new BigDecimal("1000000"), new BigDecimal("1"), null,
                new BigDecimal("250"), new BigDecimal("240"),
                null, null, null, null, null, null, null, new BigDecimal("10")));

        assertThat(result.quantity()).isEqualTo(400);
    }

    @Test
    void quantityIsRoundedDownToLotSize() {
        // 1000 shares rounded down to lot size 75 -> 975.
        var result = service.calculate(new Request(Method.PERCENTAGE_RISK,
                new BigDecimal("1000000"), new BigDecimal("1"), null,
                new BigDecimal("250"), new BigDecimal("240"),
                null, null, null, null, null, null, 75, null));

        assertThat(result.quantity()).isEqualTo(975);
    }

    @Test
    void atrSizingUsesAtrTimesMultiplier() {
        // ATR 5 x 2 = 10 per share; budget 10,000 -> 1000 shares.
        var result = service.calculate(new Request(Method.ATR,
                new BigDecimal("1000000"), new BigDecimal("1"), null,
                new BigDecimal("250"), null,
                new BigDecimal("5"), new BigDecimal("2"), null, null, null, null, null, null));

        assertThat(result.quantity()).isEqualTo(1000);
    }

    @Test
    void kellyFractionMatchesFormula() {
        // W = 0.6, R = 2 -> f = 0.6 - 0.4 / 2 = 0.4
        assertThat(PositionSizingService.kellyFraction(
                new BigDecimal("0.6"), new BigDecimal("2"), new BigDecimal("1")))
                .isEqualByComparingTo("0.4");
    }

    @Test
    void kellyIsCappedAtConfiguredRiskPercent() {
        // Half-Kelly = 20% of capital, capped at 1% risk budget -> 10,000 budget -> 1000 shares.
        var result = service.calculate(new Request(Method.KELLY,
                new BigDecimal("1000000"), new BigDecimal("1"), null,
                new BigDecimal("250"), new BigDecimal("240"),
                null, null, null,
                new BigDecimal("0.6"), new BigDecimal("2"), new BigDecimal("1"), null, null));

        assertThat(result.quantity()).isEqualTo(1000);
    }

    @Test
    void negativeKellyMeansNoTrade() {
        var result = service.calculate(new Request(Method.KELLY,
                new BigDecimal("1000000"), new BigDecimal("1"), null,
                new BigDecimal("250"), new BigDecimal("240"),
                null, null, null,
                new BigDecimal("0.3"), new BigDecimal("1"), new BigDecimal("1"), null, null));

        assertThat(result.quantity()).isZero();
    }

    @Test
    void stopEqualToEntryIsRejected() {
        assertThatThrownBy(() -> service.calculate(new Request(Method.PERCENTAGE_RISK,
                new BigDecimal("1000000"), new BigDecimal("1"), null,
                new BigDecimal("250"), new BigDecimal("250"),
                null, null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

