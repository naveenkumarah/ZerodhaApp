package com.example.journal;

import com.example.risk.RiskLimits;
import com.example.risk.RiskStateService;
import com.example.trading.Trade;
import com.example.trading.TradeRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Behavioural analysis of trade history: emotional entries, overtrading, revenge trading.
 * Deterministic rules (no external model), so results are explainable and testable.
 */
public final class JournalAnalyzer {

    private JournalAnalyzer() {}

    public record TradeFact(Instant openedAt, BigDecimal pnl, Emotion emotionBefore) {}

    public record Report(int disciplineScore, int emotionalTrades, int overtradingDays,
                         int revengeTrades, List<String> suggestions) {}

    public static Report analyze(List<TradeFact> input, RiskLimits limits) {
        List<TradeFact> trades = input.stream().sorted(Comparator.comparing(TradeFact::openedAt)).toList();
        Duration revengeWindow = Duration.ofMinutes(limits.revengeWindowMinutes());

        int emotional = (int) trades.stream().filter(t -> isEmotional(t.emotionBefore())).count();

        Map<LocalDate, Long> tradesPerDay = trades.stream().collect(Collectors.groupingBy(
                t -> RiskStateService.dateOf(t.openedAt()), HashMap::new, Collectors.counting()));
        int overtradingDays = (int) tradesPerDay.values().stream()
                .filter(c -> c > limits.maxTradesPerDay()).count();

        int revenge = 0;
        for (int i = 1; i < trades.size(); i++) {
            TradeFact prev = trades.get(i - 1);
            TradeFact cur = trades.get(i);
            boolean afterLoss = prev.pnl() != null && prev.pnl().signum() < 0;
            boolean quickReentry = Duration.between(prev.openedAt(), cur.openedAt()).compareTo(revengeWindow) <= 0;
            if (afterLoss && quickReentry) {
                revenge++;
            }
        }

        int score = Math.max(0, Math.min(100, 100 - 10 * emotional - 15 * overtradingDays - 20 * revenge));

        List<String> suggestions = new ArrayList<>();
        if (emotional > 0) {
            suggestions.add(emotional + " trade(s) entered under fear, greed or impulse. Require checklist approval and wait for a rule-based setup.");
        }
        if (overtradingDays > 0) {
            suggestions.add("Overtrading on " + overtradingDays + " day(s). Enforce a hard cap of " + limits.maxTradesPerDay() + " trades per day.");
        }
        if (revenge > 0) {
            suggestions.add(revenge + " re-entry(ies) within " + limits.revengeWindowMinutes() + " minutes of a loss. Take a mandatory cool-down after each loss.");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("Discipline maintained. Keep following the rules.");
        }
        return new Report(score, emotional, overtradingDays, revenge, suggestions);
    }

    static boolean isEmotional(Emotion e) {
        return e == Emotion.FEAR || e == Emotion.GREED || e == Emotion.IMPULSIVE;
    }

    /** Helper to build facts from trades plus the latest journal emotion per trade. */
    public static List<TradeFact> facts(List<Trade> trades, Map<Long, Emotion> emotionByTradeId) {
        return trades.stream()
                .map(t -> new TradeFact(t.getCreatedAt(), t.getPnl(), emotionByTradeId.get(t.getId())))
                .toList();
    }
}

