package com.stoxsim.market.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.market.api.CandleSeriesResponse;
import com.stoxsim.market.api.QuoteResponse;
import com.stoxsim.market.cache.MarketDataCache;
import com.stoxsim.market.data.Candle;
import com.stoxsim.market.data.CandleInterval;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.provider.MarketDataProviderRegistry;
import com.stoxsim.market.provider.upstox.UpstoxMarketDataProperties;

@Service
public class MarketDataService {

    private final TradableInstrumentRepository instruments;
    private final MarketDataProviderRegistry providers;
    private final MarketDataCache cache;
    private final UpstoxMarketDataProperties upstoxProperties;

    public MarketDataService(
        TradableInstrumentRepository instruments,
        MarketDataProviderRegistry providers,
        MarketDataCache cache,
        UpstoxMarketDataProperties upstoxProperties
    ) {
        this.instruments = instruments;
        this.providers = providers;
        this.cache = cache;
        this.upstoxProperties = upstoxProperties;
    }

    public QuoteResponse getQuote(
        MarketRegion marketRegion,
        MarketExchange exchange,
        String symbol
    ) {
        TradableInstrument instrument = findInstrument(marketRegion, exchange, symbol);
        InstrumentKey key = key(instrument);
        var quote = cache.findQuote(key).orElseGet(() -> {
            var fresh = providers.forRegion(marketRegion).getQuote(key);
            cache.storeQuote(fresh);
            return fresh;
        });

        Instant staleCutoff = Instant.now().minusSeconds(upstoxProperties.getStaleAfterSeconds());
        boolean stale = quote.receivedAt() == null || quote.receivedAt().isBefore(staleCutoff);
        return QuoteResponse.from(
            instrument,
            quote,
            stale ? QuoteResponse.DataStatus.STALE : QuoteResponse.DataStatus.LIVE
        );
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
}
