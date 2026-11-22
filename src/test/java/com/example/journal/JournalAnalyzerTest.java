package com.example.journal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.journal.JournalAnalyzer.TradeFact;
import com.example.risk.RiskLimits;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class JournalAnalyzerTest {

    private static final RiskLimits LIMITS = new RiskLimits(3, 2, new BigDecimal("2"), 30);
    private static final Instant BASE = Instant.parse("2026-11-02T04:30:00Z"); // 10:00 IST

    @Test
    void detectsEmotionOvertradingAndRevenge() {
        List<TradeFact> facts = List.of(
                new TradeFact(BASE, new BigDecimal("-500"), Emotion.GREED),
                new TradeFact(BASE.plusSeconds(600), null, Emotion.IMPULSIVE),   // 10 min after loss
                new TradeFact(BASE.plusSeconds(1200), new BigDecimal("300"), Emotion.DISCIPLINED),
                new TradeFact(BASE.plusSeconds(1800), null, null));              // 4th trade of the day

        JournalAnalyzer.Report report = JournalAnalyzer.analyze(facts, LIMITS);

        assertThat(report.emotionalTrades()).isEqualTo(2);
        assertThat(report.overtradingDays()).isEqualTo(1);
        assertThat(report.revengeTrades()).isEqualTo(1);
        // 100 - 2*10 - 1*15 - 1*20 = 45
        assertThat(report.disciplineScore()).isEqualTo(45);
        assertThat(report.suggestions()).hasSize(3);
    }

    @Test
    void disciplinedSessionScoresFullMarks() {
        List<TradeFact> facts = List.of(
                new TradeFact(BASE, new BigDecimal("400"), Emotion.DISCIPLINED),
                new TradeFact(BASE.plusSeconds(7200), null, Emotion.DISCIPLINED));

        JournalAnalyzer.Report report = JournalAnalyzer.analyze(facts, LIMITS);

        assertThat(report.disciplineScore()).isEqualTo(100);
        assertThat(report.suggestions()).containsExactly("Discipline maintained. Keep following the rules.");
    }
}

