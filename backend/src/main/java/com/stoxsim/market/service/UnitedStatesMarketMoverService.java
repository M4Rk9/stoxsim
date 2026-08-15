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

@Service
public class UnitedStatesMarketMoverService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        UnitedStatesMarketMoverService.class
    );
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int RESULT_LIMIT = 10;
    private static final int QUOTE_BATCH_SIZE = 100;
    private static final String UNIVERSE = "US_MARKET";

    private final TradableInstrumentRepository instruments;
    private final MarketDataProviderRegistry providers;
    private final MarketDataService marketData;
    private final AlpacaMarketDataProperties properties;
    private final UnitedStatesMarketMoversCache cache;
    private final Sp500UniverseService sp500;
    private final AtomicBoolean refreshing = new AtomicBoolean();

    public UnitedStatesMarketMoverService(
        TradableInstrumentRepository instruments,
        MarketDataProviderRegistry providers,
        MarketDataService marketData,
        AlpacaMarketDataProperties properties,
        UnitedStatesMarketMoversCache cache,
        Sp500UniverseService sp500
    ) {
        this.instruments = instruments;
        this.providers = providers;
        this.marketData = marketData;
        this.properties = properties;
        this.cache = cache;
        this.sp500 = sp500;
    }

    public MarketMoversResponse current() {
        return cache.find()
            .filter(snapshot -> UNIVERSE.equals(snapshot.universe()))
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

            Set<String> constituentSymbols = sp500.symbols();
            List<TradableInstrument> universe = instruments
                .findAllByProviderAndInstrumentKeyIn(
                    AlpacaInstrumentMapper.PROVIDER,
                    constituentSymbols
                )
                .stream()
                .filter(TradableInstrument::isActive)
                .filter(instrument -> sp500.contains(instrument.getTradingSymbol()))
                .toList();

            if (universe.isEmpty()) {
                LOGGER.info("USA movers are waiting for S&P 500 instruments in the Alpaca catalogue");
                return cachedOrUnavailable();
            }

            Map<String, Quote> quotes = loadQuotes(universe);
            MarketDataStatus status = marketData.marketStatus(
                MarketRegion.UNITED_STATES,
                MarketExchange.NASDAQ
            );
            List<MarketMoverResponse> candidates = new ArrayList<>();
            for (TradableInstrument instrument : universe) {
                Quote quote = quotes.get(
                    instrument.getTradingSymbol().toUpperCase(Locale.ROOT)
                );
                MarketMoverResponse mover = toMover(instrument, quote, status);
                if (mover != null) {
                    candidates.add(mover);
                }
            }

            if (candidates.isEmpty()) {
                LOGGER.warn("Alpaca returned no usable S&P 500 mover snapshots");
                return cachedOrUnavailable();
            }

            Comparator<MarketMoverResponse> byChange = Comparator.comparing(
                MarketMoverResponse::changePercent
            );
            MarketMoversResponse response = new MarketMoversResponse(
                UNIVERSE,
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
            cache.store(response);
            LOGGER.info(
                "USA movers refreshed from {} usable S&P 500 quotes across {} catalogue instruments",
                candidates.size(),
                universe.size()
            );
            return response;
        } finally {
            refreshing.set(false);
        }
    }

    private Map<String, Quote> loadQuotes(List<TradableInstrument> requested) {
        MarketDataProvider provider = providers.forRegion(MarketRegion.UNITED_STATES);
        Map<String, Quote> quotes = new java.util.LinkedHashMap<>();
        for (int offset = 0; offset < requested.size(); offset += QUOTE_BATCH_SIZE) {
            List<TradableInstrument> batch = requested.subList(
                offset,
                Math.min(offset + QUOTE_BATCH_SIZE, requested.size())
            );
            Set<InstrumentKey> keys = batch.stream()
                .map(this::key)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            try {
                provider.getQuotes(keys).forEach(quote -> quotes.put(
                    quote.instrument().value().toUpperCase(Locale.ROOT),
                    quote
                ));
            } catch (RuntimeException exception) {
                LOGGER.warn(
                    "Could not retrieve an S&P 500 mover snapshot batch containing {} instruments",
                    batch.size(),
                    exception
                );
            }
        }
        return Map.copyOf(quotes);
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
            .filter(snapshot -> UNIVERSE.equals(snapshot.universe()))
            .map(this::withCurrentStatus)
            .orElseGet(() -> MarketMoversResponse.unavailable(UNIVERSE));
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
