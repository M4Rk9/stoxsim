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
    private final IndiaMarketSessionService indiaSessions;

    public MarketDataService(
        TradableInstrumentRepository instruments,
        MarketDataProviderRegistry providers,
        MarketDataCache cache,
        UpstoxMarketDataProperties upstoxProperties,
        IndiaMarketSessionService indiaSessions
    ) {
        this.instruments = instruments;
        this.providers = providers;
        this.cache = cache;
        this.upstoxProperties = upstoxProperties;
        this.indiaSessions = indiaSessions;
    }

    public QuoteResponse getQuote(
        MarketRegion marketRegion,
        MarketExchange exchange,
        String symbol
    ) {
        TradableInstrument instrument = findInstrument(marketRegion, exchange, symbol);
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
        if (cached.isPresent() && !shouldRefresh(instrument, cached.get())) {
            return cached.get();
        }
        try {
            var fresh = providers.forRegion(instrument.getMarketRegion()).getQuote(key);
            cache.storeQuote(fresh);
            return fresh;
        } catch (RuntimeException exception) {
            return cached.orElseThrow(() -> exception);
        }
    }

    public boolean isStale(Quote quote) {
        Instant staleCutoff = Instant.now().minusSeconds(upstoxProperties.getStaleAfterSeconds());
        return quote.receivedAt() == null || quote.receivedAt().isBefore(staleCutoff);
    }

    public MarketDataStatus status(TradableInstrument instrument, Quote quote) {
        if (quote == null || quote.lastPrice() == null || quote.lastPrice().signum() <= 0) {
            return MarketDataStatus.UNAVAILABLE;
        }
        if (!isRegularSession(instrument)) {
            return MarketDataStatus.CLOSED;
        }
        return isStale(quote) ? MarketDataStatus.STALE : MarketDataStatus.LIVE;
    }

    public MarketDataStatus marketStatus(
        MarketRegion marketRegion,
        MarketExchange exchange
    ) {
        if (marketRegion == MarketRegion.INDIA
            && indiaSessions.current(exchange).executable()) {
            return MarketDataStatus.LIVE;
        }
        return MarketDataStatus.CLOSED;
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

        TradableInstrument instrument = findInstrument(marketRegion, exchange, symbol);
        InstrumentKey key = key(instrument);
        List<Candle> candles = cache
            .findCandles(key, interval, from.toString(), to.toString())
            .orElseGet(() -> {
                var fresh = providers.forRegion(marketRegion)
                    .getCandles(key, interval, from, to);
                cache.storeCandles(key, interval, from.toString(), to.toString(), fresh);
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

    private boolean shouldRefresh(TradableInstrument instrument, Quote quote) {
        if (quote.receivedAt() == null) {
            return true;
        }
        int refreshSeconds = isRegularSession(instrument)
            ? upstoxProperties.getQuoteTtlSeconds()
            : upstoxProperties.getClosedQuoteRefreshSeconds();
        return quote.receivedAt().isBefore(Instant.now().minusSeconds(refreshSeconds));
    }

    private boolean isRegularSession(TradableInstrument instrument) {
        return instrument.getMarketRegion() == MarketRegion.INDIA
            && indiaSessions.current(instrument.getExchange()).executable();
    }
}
