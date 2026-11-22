package com.example.strategy;

import java.math.BigDecimal;
import java.util.List;

public record StrategyAnalysis(
        String strategy,
        BigDecimal spot,
        int quantity,
        BigDecimal netPremium,
        BigDecimal maxProfit,
        BigDecimal maxLoss,
        boolean unlimitedProfit,
        boolean unlimitedLoss,
        BigDecimal marginRequired,
        BigDecimal roiPercent,
        BigDecimal probabilityOfProfit,
        BigDecimal capitalUsedPercent,
        List<BigDecimal> breakevens,
        Greeks greeks,
        List<PayoffAnalyzer.PayoffPoint> payoff) {}

