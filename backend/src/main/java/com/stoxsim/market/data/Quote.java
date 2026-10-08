package com.stoxsim.market.data;

import java.math.BigDecimal;
import java.time.Instant;

public record Quote(
    InstrumentKey instrument,
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
    boolean simulated
) {
    public Quote(InstrumentKey instrument, BigDecimal lastPrice, BigDecimal bid, BigDecimal ask,
        BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal previousClose,
        Long volume, Instant exchangeTimestamp, Instant receivedAt) {
        this(instrument, lastPrice, bid, ask, open, high, low, close, previousClose, volume, exchangeTimestamp, receivedAt, false);
    }
}
