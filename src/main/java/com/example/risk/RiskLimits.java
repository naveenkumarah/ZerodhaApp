package com.example.risk;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Discipline rules that are global across users (configured under app.discipline). */
@ConfigurationProperties(prefix = "app.discipline")
public record RiskLimits(int maxTradesPerDay, int maxConsecutiveLosses, BigDecimal minRewardRisk,
                         int revengeWindowMinutes) {}

