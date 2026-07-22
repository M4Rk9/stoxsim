package com.stoxsim.market.websocket;

import java.math.BigDecimal;
import java.time.Instant;

import com.stoxsim.market.data.Quote;

public record MarketQuoteMessage(
    String provider,
    String instrumentKey,
    String marketRegion,
    BigDecimal lastPrice,
    BigDecimal bid,
    BigDecimal ask,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    BigDecimal previousClose,
    Long volume,
    Instant exchangeTimestamp,
    Instant receivedAt
) {
    public static MarketQuoteMessage from(Quote quote) {
        return new MarketQuoteMessage(
            quote.instrument().provider(),
            quote.instrument().value(),
            quote.instrument().marketRegion().name(),
            quote.lastPrice(),
            quote.bid(),
            quote.ask(),
            quote.open(),
            quote.high(),
            quote.low(),
            quote.close(),
            quote.previousClose(),
            quote.volume(),
            quote.exchangeTimestamp(),
            quote.receivedAt()
        );
    }
}
