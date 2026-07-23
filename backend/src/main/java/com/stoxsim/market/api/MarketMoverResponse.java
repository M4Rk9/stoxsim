package com.stoxsim.market.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.stoxsim.market.data.MarketDataStatus;

public record MarketMoverResponse(
    String instrumentKey,
    String symbol,
    String name,
    String exchange,
    BigDecimal lastPrice,
    BigDecimal change,
    BigDecimal changePercent,
    Long volume,
    MarketDataStatus dataStatus,
    Instant priceTimestamp
) {
}
