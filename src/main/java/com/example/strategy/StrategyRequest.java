package com.example.strategy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record StrategyRequest(
        @NotBlank String underlying,
        @NotNull @Positive BigDecimal spot,
        @NotNull @Positive Integer lotSize,
        @NotNull @Positive Integer lots,
        @NotNull @Positive Integer daysToExpiry,
        @NotNull @Positive BigDecimal ivPercent,
        @Positive BigDecimal capital,
        @NotNull @Size(min = 2, max = 4) @Valid List<LegInput> legs) {

    public record LegInput(
            @NotNull OptionLeg.Type type,
            @NotNull OptionLeg.Side side,
            @NotNull @Positive BigDecimal strike,
            @NotNull @PositiveOrZero BigDecimal premium) {}
}

