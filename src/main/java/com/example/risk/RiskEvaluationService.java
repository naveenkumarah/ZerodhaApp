package com.example.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Discipline enforcement. Pure rule evaluation: no I/O, so it can be tested in isolation.
 * Capital protection is evaluated before any profit consideration.
 */
@Service
public class RiskEvaluationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal TWO_THIRDS = new BigDecimal("0.6667");

    private final RiskLimits limits;

    public RiskEvaluationService(RiskLimits limits) {
        this.limits = limits;
    }

    public record Assessment(RiskStatus status, List<String> locks, List<String> warnings) {}

    public record TradeDecision(boolean allowed, RiskStatus status, List<String> blockers, List<String> warnings) {}

    /** Green = safe, Yellow = approaching a limit, Red = trading locked. */
    public Assessment assess(RiskProfile profile, RiskSnapshot s) {
        List<String> locks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        int maxTrades = limits.maxTradesPerDay();
        if (s.tradesToday() >= maxTrades) {
            locks.add("Max " + maxTrades + " trades per day reached");
        } else if (s.tradesToday() == maxTrades - 1) {
            warnings.add("One trade remaining today");
        }

        int maxLosses = limits.maxConsecutiveLosses();
        if (s.consecutiveLosses() >= maxLosses) {
            locks.add("Revenge protection: " + s.consecutiveLosses() + " consecutive losses today");
        } else if (s.consecutiveLosses() > 0 && s.consecutiveLosses() == maxLosses - 1) {
            warnings.add("One more loss locks trading");
        }

        BigDecimal capital = profile.getCapital();
        if (capital == null || capital.signum() <= 0) {
            locks.add("Capital is not configured in the risk profile");
        } else {
            checkLoss("Daily", s.dailyPnl(), capital, profile.getDailyLossLimit(), locks, warnings);
            checkLoss("Weekly", s.weeklyPnl(), capital, profile.getWeeklyLossLimit(), locks, warnings);
            checkLoss("Monthly", s.monthlyPnl(), capital, profile.getMonthlyLossLimit(), locks, warnings);
        }

        RiskStatus status = !locks.isEmpty() ? RiskStatus.RED
                : !warnings.isEmpty() ? RiskStatus.YELLOW
                : RiskStatus.GREEN;
        return new Assessment(status, locks, warnings);
    }

    /** Full pre-trade gate: discipline state, checklist, risk per trade, reward:risk, escalation and margin. */
    public TradeDecision evaluateTrade(RiskProfile profile, RiskSnapshot s, TradeProposal p,
                                       ChecklistAnswers checklist,
                                       BigDecimal availableMargin, BigDecimal requiredMargin) {
        Assessment assessment = assess(profile, s);
        List<String> blockers = new ArrayList<>(assessment.locks());

        checklist.missingItems().forEach(item -> blockers.add("Checklist: " + item));

        if (p.qty() <= 0) {
            blockers.add("Quantity must be positive");
        }

        if (p.stopLoss() == null) {
            blockers.add("Stop loss must be defined");
        } else {
            BigDecimal riskPerShare = p.entryPrice().subtract(p.stopLoss()).abs();
            if (riskPerShare.signum() == 0) {
                blockers.add("Stop loss cannot equal entry price");
            } else {
                if (p.target() == null) {
                    blockers.add("Target must be defined");
                } else {
                    BigDecimal rr = p.target().subtract(p.entryPrice()).abs()
                            .divide(riskPerShare, 2, RoundingMode.HALF_UP);
                    if (rr.compareTo(limits.minRewardRisk()) < 0) {
                        blockers.add("Reward:risk " + rr + " is below minimum " + limits.minRewardRisk());
                    }
                }
                BigDecimal riskAmount = riskPerShare.multiply(BigDecimal.valueOf(p.qty()));
                BigDecimal maxRisk = profile.getCapital().multiply(profile.getRiskPercentage())
                        .divide(HUNDRED, 2, RoundingMode.HALF_UP);
                if (profile.getCapital().signum() > 0 && riskAmount.compareTo(maxRisk) > 0) {
                    blockers.add("Position risk INR " + riskAmount + " exceeds per-trade limit INR " + maxRisk);
                }
            }
        }

        if (s.lastTradeWasLoss() && s.lastTradeQty() != null && p.qty() > s.lastTradeQty()) {
            blockers.add("Lot size cannot increase after a losing trade (last qty " + s.lastTradeQty() + ")");
        }

        if (requiredMargin != null && availableMargin != null
                && requiredMargin.compareTo(availableMargin) > 0) {
            blockers.add("Insufficient margin: required INR " + requiredMargin + ", available INR " + availableMargin);
        }

        return new TradeDecision(blockers.isEmpty(), assessment.status(), blockers, assessment.warnings());
    }

    private static void checkLoss(String label, BigDecimal pnl, BigDecimal capital, BigDecimal limitPct,
                                  List<String> locks, List<String> warnings) {
        if (pnl == null || pnl.signum() >= 0) {
            return;
        }
        BigDecimal lossPct = pnl.negate().multiply(HUNDRED).divide(capital, 2, RoundingMode.HALF_UP);
        if (lossPct.compareTo(limitPct) >= 0) {
            locks.add(label + " loss " + lossPct + "% breached limit of " + limitPct + "%");
        } else if (lossPct.compareTo(limitPct.multiply(TWO_THIRDS)) >= 0) {
            warnings.add(label + " loss " + lossPct + "% approaching limit of " + limitPct + "%");
        }
    }
}

