package com.example.trading;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByUserIdOrderByCreatedAtAsc(Long userId);

    List<Trade> findByUserIdAndSymbolAndStatus(Long userId, String symbol, Trade.Status status);

    Optional<Trade> findByIdAndUserId(Long id, Long userId);

    List<Trade> findByUserIdAndCreatedAtAfter(Long userId, Instant since);
}

