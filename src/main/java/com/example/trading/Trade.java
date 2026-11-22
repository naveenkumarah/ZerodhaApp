package com.example.trading;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "trades")
@Getter
@Setter
@NoArgsConstructor
public class Trade {

    public enum Side { BUY, SELL }

    public enum Status { OPEN, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String symbol;

    @Column(name = "entry_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal entryPrice;

    @Column(nullable = false)
    private Integer qty;

    @Column(name = "stop_loss", precision = 19, scale = 4)
    private BigDecimal stopLoss;

    @Column(precision = 19, scale = 4)
    private BigDecimal target;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false, length = 4)
    private Side tradeType;

    @Column(nullable = false, length = 32)
    private String strategy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(precision = 19, scale = 2)
    private BigDecimal pnl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}

