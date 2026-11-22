package com.example.margin;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/** Real-time margin view: available vs used, with SPAN / exposure / premium breakdown and blocking checks. */
@Component
public class MarginCalculator {

    public record Breakdown(BigDecimal spanMargin, BigDecimal exposureMargin, BigDecimal optionPremium) {}

    public record Analysis(BigDecimal availableMargin, BigDecimal usedMargin, BigDecimal spanMargin,
                           BigDecimal exposureMargin, BigDecimal optionPremium, BigDecimal netMargin,
                           boolean sufficient) {}

    /** Reads available live balance and utilised debits from Kite's /user/margins payload. */
    public BigDecimal availableFromKite(JsonNode marginsData) {
        return decimal(marginsData.path("equity").path("available").path("live_balance"));
    }

    public BigDecimal usedFromKite(JsonNode marginsData) {
        return decimal(marginsData.path("equity").path("utilised").path("debits"));
    }

    public Analysis analyze(BigDecimal available, BigDecimal used, Breakdown b) {
        BigDecimal net = b.spanMargin().add(b.exposureMargin()).add(b.optionPremium());
        return new Analysis(available, used, b.spanMargin(), b.exposureMargin(), b.optionPremium(), net,
                available.compareTo(net) >= 0);
    }

    private static BigDecimal decimal(JsonNode node) {
        return node.isNumber() ? node.decimalValue().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    }
}

