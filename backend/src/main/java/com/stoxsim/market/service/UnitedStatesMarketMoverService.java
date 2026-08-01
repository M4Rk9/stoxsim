package com.stoxsim.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.provider.alpaca.AlpacaInstrumentMapper;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.market.api.MarketMoverResponse;
import com.stoxsim.market.api.MarketMoversResponse;
import com.stoxsim.market.cache.UnitedStatesMarketMoversCache;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.MarketDataStatus;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.provider.MarketDataProvider;
import com.stoxsim.market.provider.MarketDataProviderRegistry;
import com.stoxsim.market.provider.alpaca.AlpacaMarketDataProperties;
import com.stoxsim.market.provider.alpaca.AlpacaMarketMoverMapper;
import com.stoxsim.market.provider.alpaca.AlpacaMarketMoverMapper.MoverSeed;
import com.stoxsim.market.provider.alpaca.AlpacaRestClient;

@Service
public class UnitedStatesMarketMoverService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        UnitedStatesMarketMoverService.class
    );
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int RESULT_LIMIT = 10;
    private static final List<String> LARGE_CAP_SYMBOLS = List.of(
        "AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "TSLA",
        "AVGO", "LLY", "JPM", "WMT", "V", "MA", "ORCL", "XOM",
        "COST", "NFLX", "HD", "PG", "JNJ", "ABBV", "BAC", "KO",
        "CRM", "CSCO", "CVX", "IBM", "AMD", "GE", "PM", "MRK",
        "MCD", "ADBE", "PEP", "TMO", "DIS", "LIN", "WFC", "QCOM",
        "TXN", "CAT", "AMGN", "ISRG", "NOW", "GS", "INTU", "RTX",
        "BKNG", "SPGI", "UNH", "T", "VZ", "COP", "AMAT", "PANW",
        "MU", "PFE"
    );

    private final TradableInstrumentRepository instruments;
    private final MarketDataProviderRegistry providers;
    private final MarketDataService marketData;
    private final AlpacaRestClient client;
    private final AlpacaMarketMoverMapper mapper;
    private final AlpacaMarketDataProperties properties;
    private final UnitedStatesMarketMoversCache cache;
    private final AtomicBoolean refreshing = new AtomicBoolean();

    public UnitedStatesMarketMoverService(
        TradableInstrumentRepository instruments,
        MarketDataProviderRegistry providers,
        MarketDataService marketData,
        AlpacaRestClient client,
        AlpacaMarketMoverMapper mapper,
        AlpacaMarketDataProperties properties,
        UnitedStatesMarketMoversCache cache
    ) {
        this.instruments = instruments;
        this.providers = providers;
        this.marketData = marketData;
        this.client = client;
        this.mapper = mapper;
        this.properties = properties;
        this.cache = cache;
    }

    public MarketMoversResponse current() {
        return cache.find()
            .map(this::withCurrentStatus)
            .orElseGet(this::refresh);
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public MarketMoversResponse refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return cachedOrUnavailable();
        }
        try {
            if (!properties.hasCredentials()) {
                return cachedOrUnavailable();
            }
            try {
                MarketMoversResponse screened = fromAlpacaScreener();
                if (!screened.gainers().isEmpty() || !screened.losers().isEmpty()) {
                    cache.store(screened);
                    return screened;
                }
            } catch (RuntimeException exception) {
                LOGGER.info(
                    "Alpaca SIP movers are unavailable; using the US large-cap snapshot fallback"
                );
            }
            MarketMoversResponse fallback = fromLargeCapSnapshots();
            if (!fallback.gainers().isEmpty() || !fallback.losers().isEmpty()) {
                cache.store(fallback);
            }
            return fallback;
        } finally {
            refreshing.set(false);
        }
    }

    private MarketMoversResponse fromAlpacaScreener() {
        var root = client.getMovers(RESULT_LIMIT);
        List<MoverSeed> gainers = mapper.map(root, "gainers");
        List<MoverSeed> losers = mapper.map(root, "losers");
        if (gainers.isEmpty() && losers.isEmpty()) {
            throw new IllegalStateException("Alpaca returned no market movers");
        }
        return enrichSeeds("US_MARKET", gainers, losers);
    }

    private MarketMoversResponse enrichSeeds(
        String universe,
        List<MoverSeed> gainers,
        List<MoverSeed> losers
    ) {
        LinkedHashSet<String> symbols = new LinkedHashSet<>();
        gainers.forEach(seed -> symbols.add(seed.symbol()));
        losers.forEach(seed -> symbols.add(seed.symbol()));
        Map<String, TradableInstrument> bySymbol = instruments
            .findAllByProviderAndInstrumentKeyIn(
                AlpacaInstrumentMapper.PROVIDER,
                symbols
            )
            .stream()
            .filter(TradableInstrument::isActive)
            .collect(Collectors.toMap(
                instrument -> instrument.getTradingSymbol().toUpperCase(Locale.ROOT),
                Function.identity(),
                (first, ignored) -> first
            ));

        Map<String, Quote> quotes = loadQuotes(bySymbol.values());
        MarketDataStatus status = marketData.marketStatus(
            MarketRegion.UNITED_STATES,
            MarketExchange.NASDAQ
        );
        List<MarketMoverResponse> mappedGainers = gainers.stream()
            .map(seed -> toMover(seed, bySymbol.get(seed.symbol()), quotes.get(seed.symbol()), status))
            .filter(java.util.Objects::nonNull)
            .limit(RESULT_LIMIT)
            .toList();
        List<MarketMoverResponse> mappedLosers = losers.stream()
            .map(seed -> toMover(seed, bySymbol.get(seed.symbol()), quotes.get(seed.symbol()), status))
            .filter(java.util.Objects::nonNull)
            .limit(RESULT_LIMIT)
            .toList();
        return new MarketMoversResponse(
            universe,
            Instant.now(),
            status,
            mappedGainers,
            mappedLosers
        );
    }

    private MarketMoversResponse fromLargeCapSnapshots() {
        List<TradableInstrument> universe = instruments
            .findAllByProviderAndInstrumentKeyIn(
                AlpacaInstrumentMapper.PROVIDER,
                LARGE_CAP_SYMBOLS
            )
            .stream()
            .filter(TradableInstrument::isActive)
            .toList();
        if (universe.isEmpty()) {
            return cachedOrUnavailable();
        }
        Map<String, Quote> quotes = loadQuotes(universe);
        MarketDataStatus status = marketData.marketStatus(
            MarketRegion.UNITED_STATES,
            MarketExchange.NASDAQ
        );
        List<MarketMoverResponse> candidates = new ArrayList<>();
        for (TradableInstrument instrument : universe) {
            Quote quote = quotes.get(instrument.getTradingSymbol().toUpperCase(Locale.ROOT));
            MarketMoverResponse mover = toMover(instrument, quote, status);
            if (mover != null) {
                candidates.add(mover);
            }
        }
        Comparator<MarketMoverResponse> byChange = Comparator.comparing(
            MarketMoverResponse::changePercent
        );
        return new MarketMoversResponse(
            "US_LARGE_CAP",
            Instant.now(),
            status,
            candidates.stream()
                .filter(candidate -> candidate.changePercent().signum() > 0)
                .sorted(byChange.reversed())
                .limit(RESULT_LIMIT)
                .toList(),
            candidates.stream()
                .filter(candidate -> candidate.changePercent().signum() < 0)
                .sorted(byChange)
                .limit(RESULT_LIMIT)
                .toList()
        );
    }

    private Map<String, Quote> loadQuotes(
        java.util.Collection<TradableInstrument> requested
    ) {
        if (requested.isEmpty()) {
            return Map.of();
        }
        MarketDataProvider provider = providers.forRegion(
            MarketRegion.UNITED_STATES
        );
        Set<InstrumentKey> keys = requested.stream()
            .map(this::key)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        try {
            return provider.getQuotes(keys).stream().collect(Collectors.toMap(
                quote -> quote.instrument().value().toUpperCase(Locale.ROOT),
                Function.identity(),
                (first, ignored) -> first
            ));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not retrieve USA mover snapshots", exception);
            return Map.of();
        }
    }

    private MarketMoverResponse toMover(
        MoverSeed seed,
        TradableInstrument instrument,
        Quote quote,
        MarketDataStatus status
    ) {
        if (instrument == null) {
            return null;
        }
        BigDecimal lastPrice = quote != null && quote.lastPrice() != null
            ? quote.lastPrice()
            : seed.price();
        BigDecimal change = seed.change();
        BigDecimal percent = seed.percentChange();
        if (change == null
            && quote != null
            && quote.previousClose() != null
            && lastPrice != null) {
            change = lastPrice.subtract(quote.previousClose());
        }
        if (percent == null
            && change != null
            && quote != null
            && quote.previousClose() != null
            && quote.previousClose().signum() != 0) {
            percent = change.multiply(HUNDRED)
                .divide(quote.previousClose(), 4, RoundingMode.HALF_UP);
        }
        if (lastPrice == null || change == null || percent == null) {
            return null;
        }
        return new MarketMoverResponse(
            instrument.getInstrumentKey(),
            instrument.getTradingSymbol(),
            instrument.getName(),
            instrument.getExchange().name(),
            money(lastPrice),
            money(change),
            percent.setScale(4, RoundingMode.HALF_UP),
            quote == null ? null : quote.volume(),
            status,
            quote == null
                ? Instant.now()
                : quote.exchangeTimestamp() == null
                    ? quote.receivedAt()
                    : quote.exchangeTimestamp()
        );
    }

    private MarketMoverResponse toMover(
        TradableInstrument instrument,
        Quote quote,
        MarketDataStatus status
    ) {
        if (quote == null
            || quote.lastPrice() == null
            || quote.previousClose() == null
            || quote.previousClose().signum() <= 0) {
            return null;
        }
        BigDecimal change = quote.lastPrice().subtract(quote.previousClose());
        BigDecimal percent = change.multiply(HUNDRED)
            .divide(quote.previousClose(), 4, RoundingMode.HALF_UP);
        return new MarketMoverResponse(
            instrument.getInstrumentKey(),
            instrument.getTradingSymbol(),
            instrument.getName(),
            instrument.getExchange().name(),
            money(quote.lastPrice()),
            money(change),
            percent,
            quote.volume(),
            status,
            quote.exchangeTimestamp() == null
                ? quote.receivedAt()
                : quote.exchangeTimestamp()
        );
    }

    private MarketMoversResponse withCurrentStatus(
        MarketMoversResponse snapshot
    ) {
        MarketDataStatus status = marketData.marketStatus(
            MarketRegion.UNITED_STATES,
            MarketExchange.NASDAQ
        );
        if (status == MarketDataStatus.LIVE
            && (snapshot.generatedAt() == null
                || snapshot.generatedAt().isBefore(
                    Instant.now().minusSeconds(180)
                ))) {
            status = MarketDataStatus.STALE;
        }
        MarketDataStatus currentStatus = status;
        return new MarketMoversResponse(
            snapshot.universe(),
            snapshot.generatedAt(),
            currentStatus,
            snapshot.gainers().stream()
                .map(mover -> withStatus(mover, currentStatus))
                .toList(),
            snapshot.losers().stream()
                .map(mover -> withStatus(mover, currentStatus))
                .toList()
        );
    }

    private MarketMoverResponse withStatus(
        MarketMoverResponse mover,
        MarketDataStatus status
    ) {
        return new MarketMoverResponse(
            mover.instrumentKey(),
            mover.symbol(),
            mover.name(),
            mover.exchange(),
            mover.lastPrice(),
            mover.change(),
            mover.changePercent(),
            mover.volume(),
            status,
            mover.priceTimestamp()
        );
    }

    private MarketMoversResponse cachedOrUnavailable() {
        return cache.find()
            .map(this::withCurrentStatus)
            .orElseGet(() -> MarketMoversResponse.unavailable("US_MARKET"));
    }

    private InstrumentKey key(TradableInstrument instrument) {
        return new InstrumentKey(
            instrument.getProvider(),
            instrument.getInstrumentKey(),
            instrument.getMarketRegion()
        );
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
