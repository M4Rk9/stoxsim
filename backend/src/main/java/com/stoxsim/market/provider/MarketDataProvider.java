package com.stoxsim.market.provider;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.stoxsim.market.data.Candle;
import com.stoxsim.market.data.CandleInterval;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.MarketTick;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.data.SubscriptionMode;
import com.stoxsim.market.domain.MarketRegion;

public interface MarketDataProvider {

    MarketRegion marketRegion();

    Quote getQuote(InstrumentKey instrument);

    List<Candle> getCandles(
        InstrumentKey instrument,
        CandleInterval interval,
        LocalDate from,
        LocalDate to
    );

    void subscribe(
        Set<InstrumentKey> instruments,
        SubscriptionMode mode,
        Consumer<MarketTick> listener
    );

    void unsubscribe(Set<InstrumentKey> instruments);
}
