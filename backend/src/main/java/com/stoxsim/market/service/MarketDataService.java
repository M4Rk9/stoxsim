package com.stoxsim.market.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.calendar.service.IndiaMarketSessionService;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.market.api.CandleSeriesResponse;
import com.stoxsim.market.api.QuoteResponse;
import com.stoxsim.market.cache.MarketDataCache;
import com.stoxsim.market.data.Candle;
import com.stoxsim.market.data.CandleInterval;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.MarketDataStatus;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.provider.MarketDataProviderRegistry;
import com.stoxsim.market.provider.upstox.UpstoxMarketDataProperties;

@Service
public class MarketDataService {

    private final TradableInstrumentRepository instruments;
    private final MarketDataProviderRegistry providers;
    private final MarketDataCache cache;
    private final UpstoxMarketDataProperties upstoxProperties;
    private final IndiaMarketSessionService sessions;

    public MarketDataService(
        TradableInstrumentRepository instruments,
        MarketDataProviderRegistry providers,
        MarketDataCache cache,
        UpstoxMarketDataProperties upstoxProperties,
        IndiaMarketSessionService sessions
    ) {
        this.instruments = instruments;
        this.providers = providers;
        this.cache = cache;
        this.upstoxProperties = upstoxProperties;
        this.sessions = sessions;
    }

    public QuoteResponse getQuote(
        MarketRegion marketRegion,
        MarketExchange exchange,
        String symbol
    ) {
        TradableInstrument instrument = findInstrument(
            marketRegion,
            exchange,
            symbol
        );
        Quote quote = latestQuote(instrument);
        return QuoteResponse.from(
            instrument,
            quote,
            status(instrument, quote)
        );
    }

    public Quote latestQuote(TradableInstrument instrument) {
        InstrumentKey key = key(instrument);
        var cached = cache.findQuote(key);
        if (cached.filter(this::validPrice).isPresent() && !shouldRefresh(instrument, cached.get())) {
            return cached.get();
        }
        try {
            var fresh = providers.forRegion(instrument.getMarketRegion()).getQuote(key);
            if (validPrice(fresh) && !isStale(fresh)) {
                cache.storeQuote(fresh);
                return fresh;
            }
            if (validPrice(fresh) && cached.filter(this::validPrice).isEmpty()) cached = java.util.Optional.of(fresh);
        } catch (RuntimeException exception) {
            // The simulator remains executable during a provider outage.
        }
        Quote simulated = simulatedQuote(instrument, cached.filter(this::validPrice).orElse(null));
        cache.storeQuote(simulated);
        return simulated;
    }

    private boolean validPrice(Quote quote) {
        return quote != null && quote.lastPrice() != null && quote.lastPrice().signum() > 0;
    }

    Quote simulatedQuote(TradableInstrument instrument, Quote anchor) {
        // Hold the last valid price; if none exists use a stable, instrument-specific
        // virtual starting price. Never pass off the fallback as an exchange quote.
        var price = anchor == null ? java.math.BigDecimal.valueOf(
            (instrument.getMarketRegion() == MarketRegion.INDIA ? 100 : 25)
            + Math.floorMod(instrument.getInstrumentKey().hashCode(), 400)) : anchor.lastPrice();
        var tick = instrument.getTickSize();
        if (tick != null && tick.signum() > 0) price = price.divide(tick, 0, java.math.RoundingMode.HALF_UP).max(java.math.BigDecimal.ONE).multiply(tick);
        var now = Instant.now();
        var previous = anchor == null || anchor.previousClose() == null ? price : anchor.previousClose();
        return new Quote(key(instrument), price, price, price, price, price, price, price, previous,
            anchor == null ? 0L : anchor.volume(), now, now, true);
    }

    public boolean isStale(Quote quote) {
        Instant staleCutoff = Instant.now().minusSeconds(
            upstoxProperties.getStaleAfterSeconds()
        );
        return quote.receivedAt() == null
            || quote.receivedAt().isBefore(staleCutoff);
    }

    public MarketDataStatus status(
        TradableInstrument instrument,
        Quote quote
    ) {
        if (quote == null
            || quote.lastPrice() == null
            || quote.lastPrice().signum() <= 0) {
            return MarketDataStatus.UNAVAILABLE;
        }
        if (!isRegularSession(instrument)) {
            return MarketDataStatus.CLOSED;
        }
        return MarketDataStatus.LIVE;
    }

    public MarketDataStatus marketStatus(
        MarketRegion marketRegion,
        MarketExchange exchange
    ) {
        validateExchangeRegion(marketRegion, exchange);
        return sessions.current(exchange).executable()
            ? MarketDataStatus.LIVE
            : MarketDataStatus.CLOSED;
    }

    public CandleSeriesResponse getCandles(
        MarketRegion marketRegion,
        MarketExchange exchange,
        String symbol,
        CandleInterval interval,
        LocalDate from,
        LocalDate to
    ) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "from must be on or before to"
            );
        }

        TradableInstrument instrument = findInstrument(
            marketRegion,
            exchange,
            symbol
        );
        InstrumentKey key = key(instrument);
        List<Candle> candles = cache
            .findCandles(key, interval, from.toString(), to.toString())
            .filter(cached -> !cached.isEmpty())
            .orElseGet(() -> {
                var fresh = providers
                    .forRegion(marketRegion)
                    .getCandles(key, interval, from, to);
                if (!fresh.isEmpty()) {
                    cache.storeCandles(
                        key,
                        interval,
                        from.toString(),
                        to.toString(),
                        fresh
                    );
                }
                return fresh;
            });
        return new CandleSeriesResponse(
            instrument.getProvider(),
            instrument.getInstrumentKey(),
            instrument.getTradingSymbol(),
            interval,
            from,
            to,
            candles
        );
    }

    private TradableInstrument findInstrument(
        MarketRegion marketRegion,
        MarketExchange exchange,
        String symbol
    ) {
        return instruments
            .findByMarketRegionAndExchangeAndTradingSymbolIgnoreCaseAndActiveTrue(
                marketRegion,
                exchange,
                symbol
            )
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Instrument not found"
            ));
    }

    private InstrumentKey key(TradableInstrument instrument) {
        return new InstrumentKey(
            instrument.getProvider(),
            instrument.getInstrumentKey(),
            instrument.getMarketRegion()
        );
    }

    private boolean shouldRefresh(
        TradableInstrument instrument,
        Quote quote
    ) {
        if (quote.receivedAt() == null) {
            return true;
        }
        int refreshSeconds = isRegularSession(instrument)
            ? upstoxProperties.getQuoteTtlSeconds()
            : upstoxProperties.getClosedQuoteRefreshSeconds();
        return quote.receivedAt().isBefore(
            Instant.now().minusSeconds(refreshSeconds)
        );
    }

    private boolean isRegularSession(TradableInstrument instrument) {
        return sessions.current(instrument.getExchange()).executable();
    }

    private void validateExchangeRegion(
        MarketRegion marketRegion,
        MarketExchange exchange
    ) {
        boolean usExchange = sessions.isUnitedStatesExchange(exchange);
        if (marketRegion == MarketRegion.UNITED_STATES && !usExchange) {
            throw new IllegalArgumentException(
                "United States market status requires a US exchange"
            );
        }
        if (marketRegion == MarketRegion.INDIA && usExchange) {
            throw new IllegalArgumentException(
                "India market status requires NSE or BSE"
            );
        }
    }
}
