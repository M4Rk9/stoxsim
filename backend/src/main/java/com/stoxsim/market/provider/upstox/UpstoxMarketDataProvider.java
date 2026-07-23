package com.stoxsim.market.provider.upstox;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.upstox.ApiException;
import com.upstox.api.MarketQuoteOHLCV3;
import com.upstox.api.OhlcV3;
import com.stoxsim.market.data.Candle;
import com.stoxsim.market.data.CandleInterval;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.MarketTick;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.data.SubscriptionMode;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.provider.MarketDataProvider;

import io.swagger.client.api.HistoryV3Api;
import io.swagger.client.api.MarketQuoteV3Api;

@Component
public class UpstoxMarketDataProvider implements MarketDataProvider {

    private final UpstoxClientFactory clientFactory;
    private final UpstoxMarketStream stream;

    public UpstoxMarketDataProvider(
        UpstoxClientFactory clientFactory,
        UpstoxMarketStream stream
    ) {
        this.clientFactory = clientFactory;
        this.stream = stream;
    }

    @Override
    public MarketRegion marketRegion() {
        return MarketRegion.INDIA;
    }

    @Override
    public Quote getQuote(InstrumentKey instrument) {
        List<Quote> quotes = getQuotes(Set.of(instrument));
        if (quotes.isEmpty()) {
            throw new MarketDataUnavailableException(
                "Upstox returned no quote for " + instrument.value()
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
        try {
            String requested = instruments.stream()
                .map(InstrumentKey::value)
                .collect(Collectors.joining(","));
            var response = new MarketQuoteV3Api(clientFactory.createClient())
                .getMarketQuoteOHLC("1d", requested);
            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                throw new MarketDataUnavailableException(
                    "Upstox returned no quotes for the requested instruments"
                );
            }

            Map<String, MarketQuoteOHLCV3> byInstrumentKey = new LinkedHashMap<>();
            response.getData().forEach((responseKey, value) -> {
                byInstrumentKey.put(responseKey, value);
                if (value != null && value.getInstrumentToken() != null) {
                    byInstrumentKey.put(value.getInstrumentToken(), value);
                }
            });

            Instant receivedAt = Instant.now();
            List<Quote> quotes = new ArrayList<>(instruments.size());
            for (InstrumentKey instrument : instruments) {
                MarketQuoteOHLCV3 data = byInstrumentKey.get(instrument.value());
                if (data == null && instruments.size() == 1 && response.getData().size() == 1) {
                    data = response.getData().values().iterator().next();
                }
                if (data != null && data.getLastPrice() != null) {
                    quotes.add(toQuote(instrument, data, receivedAt));
                }
            }
            return List.copyOf(quotes);
        } catch (ApiException exception) {
            throw new MarketDataUnavailableException(
                "Could not retrieve Upstox quotes",
                exception
            );
        }
    }

    @Override
    public List<Candle> getCandles(
        InstrumentKey instrument,
        CandleInterval interval,
        LocalDate from,
        LocalDate to
    ) {
        validate(instrument);
        UpstoxCandleInterval upstoxInterval = UpstoxCandleInterval.from(interval);
        try {
            var history = new HistoryV3Api(clientFactory.createClient());
            history.setHeadersOverrides(clientFactory.authorizationHeaders());
            var response = history.getHistoricalCandleData1(
                instrument.value(),
                upstoxInterval.unit(),
                upstoxInterval.interval(),
                to.toString(),
                from.toString()
            );
            if (response == null || response.getData() == null) {
                return List.of();
            }
            return UpstoxCandleMapper.map(response.getData().getCandles());
        } catch (ApiException exception) {
            throw new MarketDataUnavailableException(
                "Could not retrieve Upstox candles for " + instrument.value(),
                exception
            );
        }
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
        if (instrument.marketRegion() != MarketRegion.INDIA) {
            throw new IllegalArgumentException("Upstox only supplies India market data");
        }
        if (!"UPSTOX".equalsIgnoreCase(instrument.provider())) {
            throw new IllegalArgumentException("Unexpected provider " + instrument.provider());
        }
    }

    private BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }

    private Quote toQuote(
        InstrumentKey instrument,
        MarketQuoteOHLCV3 data,
        Instant receivedAt
    ) {
        OhlcV3 live = data.getLiveOhlc();
        OhlcV3 previous = data.getPrevOhlc();
        return new Quote(
            instrument,
            BigDecimal.valueOf(data.getLastPrice()),
            null,
            null,
            live == null ? null : decimal(live.getOpen()),
            live == null ? null : decimal(live.getHigh()),
            live == null ? null : decimal(live.getLow()),
            live == null ? null : decimal(live.getClose()),
            previous == null ? null : decimal(previous.getClose()),
            live == null ? null : live.getVolume(),
            live == null || live.getTs() == null
                ? null
                : Instant.ofEpochMilli(live.getTs()),
            receivedAt
        );
    }
}
