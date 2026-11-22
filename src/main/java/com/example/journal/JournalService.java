package com.example.journal;

import com.example.common.BusinessRuleException;
import com.example.risk.RiskLimits;
import com.example.trading.Trade;
import com.example.trading.TradeRepository;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JournalService {

    public record AddRequest(
            @NotNull Long tradeId,
            Emotion emotionBefore,
            Emotion emotionAfter,
            @Size(max = 4000) String notes,
            @Size(max = 4000) String mistakes,
            @Size(max = 4000) String lessons,
            @Size(max = 512) String entryScreenshotUrl,
            @Size(max = 512) String exitScreenshotUrl) {}

    private final JournalRepository journals;
    private final TradeRepository trades;
    private final RiskLimits limits;

    public JournalService(JournalRepository journals, TradeRepository trades, RiskLimits limits) {
        this.journals = journals;
        this.trades = trades;
        this.limits = limits;
    }

    @Transactional
    public JournalEntry add(Long userId, AddRequest r) {
        trades.findByIdAndUserId(r.tradeId(), userId)
                .orElseThrow(() -> new BusinessRuleException("Trade " + r.tradeId() + " not found"));
        JournalEntry e = new JournalEntry();
        e.setUserId(userId);
        e.setTradeId(r.tradeId());
        e.setEmotionBefore(r.emotionBefore());
        e.setEmotionAfter(r.emotionAfter());
        e.setNotes(r.notes());
        e.setMistakes(r.mistakes());
        e.setLessons(r.lessons());
        e.setEntryScreenshotUrl(r.entryScreenshotUrl());
        e.setExitScreenshotUrl(r.exitScreenshotUrl());
        return journals.save(e);
    }

    public List<JournalEntry> history(Long userId) {
        return journals.findByUserIdOrderByIdDesc(userId);
    }

    public JournalAnalyzer.Report analysis(Long userId) {
        List<JournalEntry> entries = journals.findByUserIdOrderByIdDesc(userId);
        // Entries are newest-first, so the first entry per trade is the latest emotion record.
        Map<Long, Emotion> emotionByTrade = entries.stream()
                .filter(e -> e.getEmotionBefore() != null)
                .collect(Collectors.toMap(JournalEntry::getTradeId, JournalEntry::getEmotionBefore, (a, b) -> a));
        List<Trade> userTrades = trades.findByUserIdOrderByCreatedAtAsc(userId);
        return JournalAnalyzer.analyze(JournalAnalyzer.facts(userTrades, emotionByTrade), limits);
    }
}

