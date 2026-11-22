package com.example.risk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "risk_profiles")
@Getter
@Setter
@NoArgsConstructor
public class RiskProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal capital = BigDecimal.ZERO;

    /** Percent of capital risked per trade. */
    @Column(name = "risk_percentage", nullable = false, precision = 6, scale = 2)
    private BigDecimal riskPercentage = new BigDecimal("1");

    @Column(name = "daily_loss_limit", nullable = false, precision = 6, scale = 2)
    private BigDecimal dailyLossLimit = new BigDecimal("3");

    @Column(name = "weekly_loss_limit", nullable = false, precision = 6, scale = 2)
    private BigDecimal weeklyLossLimit = new BigDecimal("6");

    @Column(name = "monthly_loss_limit", nullable = false, precision = 6, scale = 2)
    private BigDecimal monthlyLossLimit = new BigDecimal("10");

    public RiskProfile(Long userId) {
        this.userId = userId;
    }
}

