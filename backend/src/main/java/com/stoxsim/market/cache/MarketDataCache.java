package com.stoxsim.market.cache;

import java.util.List;
import java.util.Optional;

import com.stoxsim.market.data.Candle;
import com.stoxsim.market.data.CandleInterval;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.Quote;

public interface MarketDataCache {

    Optional<Quote> findQuote(InstrumentKey instrument);

    void storeQuote(Quote quote);

    Optional<List<Candle>> findCandles(
        InstrumentKey instrument,
        CandleInterval interval,
        String from,
        String to
    );

    void storeCandles(
        InstrumentKey instrument,
        CandleInterval interval,
        String from,
        String to,
        List<Candle> candles
    );
}
