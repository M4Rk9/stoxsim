package com.stoxsim.market.api;

import java.time.LocalDate;
import java.util.List;

import com.stoxsim.market.data.Candle;
import com.stoxsim.market.data.CandleInterval;

public record CandleSeriesResponse(
    String provider,
    String instrumentKey,
    String symbol,
    CandleInterval interval,
    LocalDate from,
    LocalDate to,
    List<Candle> candles
) {
}
