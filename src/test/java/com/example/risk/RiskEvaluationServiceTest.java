package com.example.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiskEvaluationServiceTest {

    private RiskEvaluationService risk;
    private RiskProfile profile;
    private final ChecklistAnswers allYes = new ChecklistAnswers(true, true, true, true, true);
    private final TradeProposal proposal = new TradeProposal("NIFTY", 100,
            new BigDecimal("250"), new BigDecimal("240"), new BigDecimal("270"));

    @BeforeEach
    void setUp() {
        risk = new RiskEvaluationService(new RiskLimits(3, 2, new BigDecimal("2"), 30));
        profile = new RiskProfile(1L);
        profile.setCapital(new BigDecimal("1000000"));   // defaults: 1% risk, 3/6/10% loss limits
    }

    @Test
    void cleanAccountWithValidTradeIsAllowed() {
        var decision = risk.evaluateTrade(profile, clean(), proposal, allYes,
                new BigDecimal("500000"), new BigDecimal("200000"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.status()).isEqualTo(RiskStatus.GREEN);
        assertThat(decision.blockers()).isEmpty();
    }

    @Test
    void thirdTradeLocksDay() {
        var decision = risk.evaluateTrade(profile, snapshot(3, "0", 0, false, null), proposal, allYes,
                null, null);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.status()).isEqualTo(RiskStatus.RED);
    }

    @Test
    void secondTradeIsYellowButAllowed() {
        var decision = risk.evaluateTrade(profile, snapshot(2, "0", 0, false, null), proposal, allYes,
                null, null);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.status()).isEqualTo(RiskStatus.YELLOW);
    }

    @Test
    void dailyLossLimitLocksTrading() {
        // 3.5% loss on 10,00,000 breaches the 3% daily limit.
        var decision = risk.evaluateTrade(profile, snapshot(1, "-35000", 0, false, null), proposal, allYes,
                null, null);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.status()).isEqualTo(RiskStatus.RED);
    }

    @Test
    void twoConsecutiveLossesLockTrading() {
        var decision = risk.evaluateTrade(profile, snapshot(2, "-10000", 2, true, 100), proposal, allYes,
                null, null);

        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void incompleteChecklistBlocksOrder() {
        var partial = new ChecklistAnswers(true, false, true, true, true);
        var decision = risk.evaluateTrade(profile, clean(), proposal, partial, null, null);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.blockers()).contains("Checklist: Support/resistance not marked");
    }

    @Test
    void lotSizeCannotIncreaseAfterLoss() {
        var decision = risk.evaluateTrade(profile, snapshot(1, "-5000", 0, true, 50), proposal, allYes,
                null, null);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.blockers()).anyMatch(b -> b.contains("cannot increase"));
    }

    @Test
    void rewardRiskBelowTwoIsBlocked() {
        var weakTarget = new TradeProposal("NIFTY", 100, new BigDecimal("250"), new BigDecimal("240"),
                new BigDecimal("260"));
        var decision = risk.evaluateTrade(profile, clean(), weakTarget, allYes, null, null);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.blockers()).anyMatch(b -> b.startsWith("Reward:risk"));
    }

    @Test
    void riskAboveOnePercentIsBlocked() {
        // 1200 shares x INR 10 risk = INR 12,000, above the 1% limit of INR 10,000.
        var oversized = new TradeProposal("NIFTY", 1200, new BigDecimal("250"), new BigDecimal("240"),
                new BigDecimal("270"));
        var decision = risk.evaluateTrade(profile, clean(), oversized, allYes, null, null);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.blockers()).anyMatch(b -> b.startsWith("Position risk"));
    }

    @Test
    void insufficientMarginIsBlocked() {
        var decision = risk.evaluateTrade(profile, clean(), proposal, allYes,
                new BigDecimal("500000"), new BigDecimal("600000"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.blockers()).anyMatch(b -> b.startsWith("Insufficient margin"));
    }

    private static RiskSnapshot clean() {
        return snapshot(0, "0", 0, false, null);
    }

    private static RiskSnapshot snapshot(int trades, String dailyPnl, int consecutive, boolean lastLoss,
                                         Integer lastQty) {
        return new RiskSnapshot(trades, new BigDecimal(dailyPnl), BigDecimal.ZERO, BigDecimal.ZERO,
                consecutive, lastLoss, lastQty);
    }
}

