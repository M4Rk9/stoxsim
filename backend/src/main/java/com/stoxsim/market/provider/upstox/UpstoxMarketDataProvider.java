package com.stoxsim.market.provider.upstox;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.upstox.ApiException;
import com.upstox.api.MarketQuoteSymbolLtpV3;
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
        validate(instrument);
        try {
            var response = new MarketQuoteV3Api(clientFactory.createClient()).getLtp(instrument.value());
            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                throw new MarketDataUnavailableException(
                    "Upstox returned no quote for " + instrument.value()
                );
            }
            MarketQuoteSymbolLtpV3 data = response.getData().get(instrument.value());
            if (data == null) {
                data = response.getData().values().stream()
                    .filter(value -> instrument.value().equals(value.getInstrumentToken()))
                    .findFirst()
                    .orElseGet(() -> response.getData().size() == 1
                        ? response.getData().values().iterator().next()
                        : null);
            }
            if (data == null || data.getLastPrice() == null) {
                throw new MarketDataUnavailableException(
                    "Upstox quote was incomplete for " + instrument.value()
                );
            }
            Instant receivedAt = Instant.now();
            return new Quote(
                instrument,
                BigDecimal.valueOf(data.getLastPrice()),
                null,
                null,
                null,
                null,
                null,
                null,
                decimal(data.getCp()),
                data.getVolume(),
                null,
                receivedAt
            );
        } catch (ApiException exception) {
            throw new MarketDataUnavailableException(
                "Could not retrieve Upstox quote for " + instrument.value(),
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
            var response = new HistoryV3Api(clientFactory.createClient()).getHistoricalCandleData1(
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
}
