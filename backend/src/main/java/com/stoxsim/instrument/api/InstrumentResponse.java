package com.stoxsim.instrument.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.market.domain.MarketRegion;

public record InstrumentResponse(
    UUID id,
    String provider,
    String instrumentKey,
    MarketRegion marketRegion,
    MarketExchange exchange,
    String segment,
    String tradingSymbol,
    String name,
    String isin,
    InstrumentType instrumentType,
    String currency,
    int lotSize,
    BigDecimal tickSize
) {
    public static InstrumentResponse from(TradableInstrument instrument) {
        return new InstrumentResponse(
            instrument.getId(),
            instrument.getProvider(),
            instrument.getInstrumentKey(),
            instrument.getMarketRegion(),
            instrument.getExchange(),
            instrument.getSegment(),
            instrument.getTradingSymbol(),
            instrument.getName(),
            instrument.getIsin(),
            instrument.getInstrumentType(),
            instrument.getCurrency(),
            instrument.getLotSize(),
            instrument.getTickSize()
        );
    }
}
