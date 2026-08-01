package com.stoxsim.market.provider.alpaca;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.stoxsim.market.data.Candle;
import com.stoxsim.market.data.CandleInterval;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.MarketTick;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.data.SubscriptionMode;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.provider.MarketDataProvider;
import com.stoxsim.market.provider.upstox.MarketDataUnavailableException;

@Component
public class AlpacaMarketDataProvider implements MarketDataProvider {

    private final AlpacaRestClient client;
    private final AlpacaQuoteMapper mapper;
    private final AlpacaPollingMarketStream stream;

    public AlpacaMarketDataProvider(
        AlpacaRestClient client,
        AlpacaQuoteMapper mapper,
        AlpacaPollingMarketStream stream
    ) {
        this.client = client;
        this.mapper = mapper;
        this.stream = stream;
    }

    @Override
    public MarketRegion marketRegion() {
        return MarketRegion.UNITED_STATES;
    }

    @Override
    public Quote getQuote(InstrumentKey instrument) {
        List<Quote> quotes = getQuotes(Set.of(instrument));
        if (quotes.isEmpty()) {
            throw new MarketDataUnavailableException(
                "Alpaca returned no quote for " + instrument.value()
            );
        }
        return quotes.getFirst();
    }

    @Override
    public List<Quote> getQuotes(Set<InstrumentKey> instruments) {
        if (instruments.isEmpty()) {
            return List.of();
        }
        instruments.forEach(this::validate);
        LinkedHashSet<String> symbols = instruments.stream()
            .map(InstrumentKey::value)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, tools.jackson.databind.JsonNode> snapshots = client.getSnapshots(symbols);
        Instant receivedAt = Instant.now();
        List<Quote> quotes = new ArrayList<>();
        for (InstrumentKey instrument : instruments) {
            var snapshot = snapshots.get(instrument.value());
            if (snapshot == null) {
                continue;
            }
            Quote quote = mapper.mapQuote(instrument, snapshot, receivedAt);
            if (quote != null) {
                quotes.add(quote);
            }
        }
        if (quotes.isEmpty()) {
            throw new MarketDataUnavailableException(
                "Alpaca returned no usable quotes for the requested US instruments"
            );
        }
        return List.copyOf(quotes);
    }

    @Override
    public List<Candle> getCandles(
        InstrumentKey instrument,
        CandleInterval interval,
        LocalDate from,
        LocalDate to
    ) {
        validate(instrument);
        return mapper.mapBars(client.getBars(
            instrument.value(),
            timeframe(interval),
            from,
            to
        ));
    }

    @Override
    public void subscribe(
        Set<InstrumentKey> instruments,
        SubscriptionMode mode,
        Consumer<MarketTick> listener
    ) {
        instruments.forEach(this::validate);
        stream.subscribe(instruments, mode, listener);
    }

    @Override
    public void unsubscribe(Set<InstrumentKey> instruments) {
        instruments.forEach(this::validate);
        stream.unsubscribe(instruments);
    }

    private void validate(InstrumentKey instrument) {
        if (instrument.marketRegion() != MarketRegion.UNITED_STATES) {
            throw new IllegalArgumentException(
                "Alpaca only supplies United States market data"
            );
        }
        if (!"ALPACA".equalsIgnoreCase(instrument.provider())) {
            throw new IllegalArgumentException(
                "Unexpected provider " + instrument.provider()
            );
        }
    }

    private String timeframe(CandleInterval interval) {
        return switch (interval) {
            case ONE_MINUTE -> "1Min";
            case THREE_MINUTES -> "3Min";
            case FIFTEEN_MINUTES -> "15Min";
            case ONE_HOUR -> "1Hour";
            case ONE_DAY -> "1Day";
            case ONE_WEEK -> "1Week";
            case ONE_MONTH -> "1Month";
        };
    }
}
