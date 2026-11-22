package com.example.analytics;

import com.example.analytics.AnalyticsCalculator.ClosedTrade;
import com.example.analytics.AnalyticsCalculator.Summary;
import com.example.trading.Trade;
import com.example.trading.TradeRepository;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final TradeRepository trades;

    public AnalyticsController(TradeRepository trades) {
        this.trades = trades;
    }

    @GetMapping("/summary")
    public Summary summary(@AuthenticationPrincipal Long userId) {
        List<ClosedTrade> closed = trades.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(t -> t.getStatus() == Trade.Status.CLOSED && t.getPnl() != null && t.getClosedAt() != null)
                .map(t -> new ClosedTrade(t.getClosedAt(), t.getStrategy(), t.getPnl()))
                .toList();
        return AnalyticsCalculator.summarize(closed);
    }
}

