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
import com.upstox.api.MarketQuoteSymbolLtpV3;
import com.upstox.api.OhlcV3;
import com.stoxsim.market.data.Candle;
import com.stoxsim.market.data.CandleInterval;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.MarketTick;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.data.SubscriptionMode;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.provider.MarketDataProvider;

import io.swagger.client.api.MarketQuoteV3Api;

@Component
public class UpstoxMarketDataProvider implements MarketDataProvider {

    private final UpstoxClientFactory clientFactory;
    private final UpstoxMarketStream stream;
    private final UpstoxHistoricalCandleClient historicalCandles;

    public UpstoxMarketDataProvider(
        UpstoxClientFactory clientFactory,
        UpstoxMarketStream stream,
        UpstoxHistoricalCandleClient historicalCandles
    ) {
        this.clientFactory = clientFactory;
        this.stream = stream;
        this.historicalCandles = historicalCandles;
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
            var api = new MarketQuoteV3Api(clientFactory.createClient());
            var ohlcResponse = api.getMarketQuoteOHLC("1d", requested);
            var ltpResponse = api.getLtp(requested);

            Map<String, MarketQuoteOHLCV3> ohlcByKey = indexOhlc(
                ohlcResponse == null ? null : ohlcResponse.getData()
            );
            Map<String, MarketQuoteSymbolLtpV3> ltpByKey = indexLtp(
                ltpResponse == null ? null : ltpResponse.getData()
            );
            if (ohlcByKey.isEmpty() && ltpByKey.isEmpty()) {
                throw new MarketDataUnavailableException(
                    "Upstox returned no quotes for the requested instruments"
                );
            }

            Instant receivedAt = Instant.now();
            List<Quote> quotes = new ArrayList<>(instruments.size());
            for (InstrumentKey instrument : instruments) {
                MarketQuoteOHLCV3 ohlc = ohlcByKey.get(instrument.value());
                MarketQuoteSymbolLtpV3 ltp = ltpByKey.get(instrument.value());
                if (ohlc == null && instruments.size() == 1 && ohlcByKey.size() == 1) {
                    ohlc = ohlcByKey.values().iterator().next();
                }
                if (ltp == null && instruments.size() == 1 && ltpByKey.size() == 1) {
                    ltp = ltpByKey.values().iterator().next();
                }
                Double lastPrice = ltp != null && ltp.getLastPrice() != null
                    ? ltp.getLastPrice()
                    : ohlc == null ? null : ohlc.getLastPrice();
                if (lastPrice != null) {
                    quotes.add(toQuote(instrument, ohlc, ltp, lastPrice, receivedAt));
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
        return historicalCandles.getCandles(
            instrument.value(),
            upstoxInterval.unit(),
            upstoxInterval.interval(),
            from,
            to
        );
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

    private Map<String, MarketQuoteOHLCV3> indexOhlc(
        Map<String, MarketQuoteOHLCV3> response
    ) {
        Map<String, MarketQuoteOHLCV3> indexed = new LinkedHashMap<>();
        if (response == null) {
            return indexed;
        }
        response.forEach((responseKey, value) -> {
            if (value == null) {
                return;
            }
            indexed.put(responseKey, value);
            if (value.getInstrumentToken() != null) {
                indexed.put(value.getInstrumentToken(), value);
            }
        });
        return indexed;
    }

    private Map<String, MarketQuoteSymbolLtpV3> indexLtp(
        Map<String, MarketQuoteSymbolLtpV3> response
    ) {
        Map<String, MarketQuoteSymbolLtpV3> indexed = new LinkedHashMap<>();
        if (response == null) {
            return indexed;
        }
        response.forEach((responseKey, value) -> {
            if (value == null) {
                return;
            }
            indexed.put(responseKey, value);
            if (value.getInstrumentToken() != null) {
                indexed.put(value.getInstrumentToken(), value);
            }
        });
        return indexed;
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
        MarketQuoteOHLCV3 ohlc,
        MarketQuoteSymbolLtpV3 ltp,
        Double lastPrice,
        Instant receivedAt
    ) {
        OhlcV3 live = ohlc == null ? null : ohlc.getLiveOhlc();
        Long volume = ltp != null && ltp.getVolume() != null
            ? ltp.getVolume()
            : live == null ? null : live.getVolume();
        return new Quote(
            instrument,
            BigDecimal.valueOf(lastPrice),
            null,
            null,
            live == null ? null : decimal(live.getOpen()),
            live == null ? null : decimal(live.getHigh()),
            live == null ? null : decimal(live.getLow()),
            live == null ? null : decimal(live.getClose()),
            ltp == null ? null : decimal(ltp.getCp()),
            volume,
            live == null || live.getTs() == null
                ? null
                : Instant.ofEpochMilli(live.getTs()),
            receivedAt
        );
    }
}
