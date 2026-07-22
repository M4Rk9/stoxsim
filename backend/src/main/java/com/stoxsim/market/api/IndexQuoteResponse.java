package com.stoxsim.market.api;

import java.math.BigDecimal;
import java.time.Instant;

public record IndexQuoteResponse(
    String code,
    String label,
    String exchange,
    String instrumentKey,
    BigDecimal value,
    BigDecimal change,
    BigDecimal changePercent,
    BigDecimal previousClose,
    DataStatus dataStatus,
    Instant exchangeTimestamp
) {
    public enum DataStatus {
        LIVE,
        STALE,
        UNAVAILABLE
    }
}
