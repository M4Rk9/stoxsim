package com.stoxsim.instrument.service;

import java.math.BigDecimal;

import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.market.domain.MarketRegion;

public record InstrumentSnapshot(
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
    BigDecimal tickSize,
    String securityType
) {
}
