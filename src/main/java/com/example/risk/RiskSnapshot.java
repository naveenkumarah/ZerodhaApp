package com.example.risk;

import java.math.BigDecimal;

/** Account state derived from the trade ledger, used by the risk engine. */
public record RiskSnapshot(
        int tradesToday,
        BigDecimal dailyPnl,
        BigDecimal weeklyPnl,
        BigDecimal monthlyPnl,
        int consecutiveLosses,
        boolean lastTradeWasLoss,
        Integer lastTradeQty) {}

