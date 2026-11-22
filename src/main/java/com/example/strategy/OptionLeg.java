package com.example.strategy;

import java.math.BigDecimal;

/** One option leg. quantity is in units (lots x lotSize). */
public record OptionLeg(Type type, Side side, BigDecimal strike, BigDecimal premium, int quantity) {

    public enum Type { CE, PE }

    public enum Side { BUY, SELL }

    public int sign() {
        return side == Side.BUY ? 1 : -1;
    }

    public BigDecimal intrinsicAt(BigDecimal spot) {
        BigDecimal diff = type == Type.CE ? spot.subtract(strike) : strike.subtract(spot);
        return diff.signum() > 0 ? diff : BigDecimal.ZERO;
    }

    /** Expiry P&L of this leg at underlying price spot (signed for BUY/SELL). */
    public BigDecimal pnlAt(BigDecimal spot) {
        return intrinsicAt(spot).subtract(premium).multiply(BigDecimal.valueOf((long) sign() * quantity));
    }
}

