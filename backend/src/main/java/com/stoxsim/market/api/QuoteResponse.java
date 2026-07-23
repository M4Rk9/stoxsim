package com.stoxsim.market.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.market.data.MarketDataStatus;
import com.stoxsim.market.data.Quote;

public record QuoteResponse(
    String provider,
    String instrumentKey,
    String symbol,
    String exchange,
    String marketRegion,
    String currency,
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
    Instant receivedAt,
    MarketDataStatus dataStatus
) {
    public static QuoteResponse from(
        TradableInstrument instrument,
        Quote quote,
        MarketDataStatus dataStatus
    ) {
        return new QuoteResponse(
            quote.instrument().provider(),
            quote.instrument().value(),
            instrument.getTradingSymbol(),
            instrument.getExchange().name(),
            instrument.getMarketRegion().name(),
            instrument.getCurrency(),
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
            quote.receivedAt(),
            dataStatus
        );
    }

}
