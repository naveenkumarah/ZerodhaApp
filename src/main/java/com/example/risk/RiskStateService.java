package com.example.risk;

import com.example.trading.Trade;
import com.example.trading.TradeRepository;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Derives the risk snapshot from the trade ledger (single source of truth for discipline state). */
@Service
public class RiskStateService {

    public static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Kolkata");

    private final TradeRepository trades;

    public RiskStateService(TradeRepository trades) {
        this.trades = trades;
    }

    public RiskSnapshot snapshot(Long userId, Instant now) {
        LocalDate today = dateOf(now);
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate monthStart = today.withDayOfMonth(1);

        List<Trade> all = trades.findByUserIdOrderByCreatedAtAsc(userId);
        List<Trade> closed = all.stream()
                .filter(t -> t.getStatus() == Trade.Status.CLOSED && t.getPnl() != null && t.getClosedAt() != null)
                .sorted(Comparator.comparing(Trade::getClosedAt))
                .toList();

        int tradesToday = (int) all.stream().filter(t -> dateOf(t.getCreatedAt()).equals(today)).count();

        List<Trade> closedToday = closed.stream().filter(t -> dateOf(t.getClosedAt()).equals(today)).toList();
        int consecutiveLosses = 0;
        for (int i = closedToday.size() - 1; i >= 0 && closedToday.get(i).getPnl().signum() < 0; i--) {
            consecutiveLosses++;
        }

        Trade lastClosed = closed.isEmpty() ? null : closed.get(closed.size() - 1);
        boolean lastWasLoss = lastClosed != null && lastClosed.getPnl().signum() < 0;
        Integer lastQty = lastClosed == null ? null : lastClosed.getQty();

        return new RiskSnapshot(
                tradesToday,
                sumSince(closed, today),
                sumSince(closed, weekStart),
                sumSince(closed, monthStart),
                consecutiveLosses,
                lastWasLoss,
                lastQty);
    }

    private static BigDecimal sumSince(List<Trade> closed, LocalDate start) {
        return closed.stream()
                .filter(t -> !dateOf(t.getClosedAt()).isBefore(start))
                .map(Trade::getPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static LocalDate dateOf(Instant instant) {
        return instant.atZone(MARKET_ZONE).toLocalDate();
    }
}

