package com.example.risk;

import java.math.BigDecimal;

public record TradeProposal(
        String symbol,
        int qty,
        BigDecimal entryPrice,
        BigDecimal stopLoss,
        BigDecimal target) {}

