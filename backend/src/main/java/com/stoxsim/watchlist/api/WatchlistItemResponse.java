package com.stoxsim.watchlist.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.market.domain.MarketRegion;

public record WatchlistItemResponse(
    UUID itemId,
    UUID instrumentId,
    String provider,
    String instrumentKey,
    MarketRegion marketRegion,
    MarketExchange exchange,
    String symbol,
    String name,
    InstrumentType instrumentType,
    String currency,
    BigDecimal tickSize,
    BigDecimal lastPrice,
    BigDecimal change,
    BigDecimal changePercent,
    String dataStatus,
    Instant exchangeTimestamp,
    Instant addedAt
) {
}
