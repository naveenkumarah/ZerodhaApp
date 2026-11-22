package com.example.risk;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiskProfileService {

    public record Update(
            @NotNull @Positive BigDecimal capital,
            @NotNull @Positive BigDecimal riskPercentage,
            @NotNull @Positive BigDecimal dailyLossLimit,
            @NotNull @Positive BigDecimal weeklyLossLimit,
            @NotNull @Positive BigDecimal monthlyLossLimit) {}

    private final RiskProfileRepository profiles;

    public RiskProfileService(RiskProfileRepository profiles) {
        this.profiles = profiles;
    }

    @Transactional
    public RiskProfile getOrCreate(Long userId) {
        return profiles.findByUserId(userId).orElseGet(() -> profiles.save(new RiskProfile(userId)));
    }

    @Transactional
    public RiskProfile update(Long userId, Update u) {
        if (u.riskPercentage().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("riskPercentage must be <= 100");
        }
        if (u.dailyLossLimit().compareTo(u.weeklyLossLimit()) > 0
                || u.weeklyLossLimit().compareTo(u.monthlyLossLimit()) > 0) {
            throw new IllegalArgumentException("Loss limits must satisfy daily <= weekly <= monthly");
        }
        RiskProfile p = getOrCreate(userId);
        p.setCapital(u.capital());
        p.setRiskPercentage(u.riskPercentage());
        p.setDailyLossLimit(u.dailyLossLimit());
        p.setWeeklyLossLimit(u.weeklyLossLimit());
        p.setMonthlyLossLimit(u.monthlyLossLimit());
        return profiles.save(p);
    }
}

