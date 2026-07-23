package com.stoxsim.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.market.api.MarketMoverResponse;
import com.stoxsim.market.api.MarketMoversResponse;
import com.stoxsim.market.cache.MarketMoversCache;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.MarketDataStatus;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.provider.MarketDataProvider;
import com.stoxsim.market.provider.MarketDataProviderRegistry;
import com.stoxsim.market.provider.upstox.UpstoxMarketDataProperties;

@Service
public class MarketMoverService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarketMoverService.class);
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int BATCH_SIZE = 500;
    private static final int RESULT_LIMIT = 8;

    private final TradableInstrumentRepository instruments;
    private final MarketDataProviderRegistry providers;
    private final MarketMoversCache moversCache;
    private final MarketDataService marketData;
    private final UpstoxMarketDataProperties upstoxProperties;
    private final AtomicBoolean refreshing = new AtomicBoolean();

    public MarketMoverService(
        TradableInstrumentRepository instruments,
        MarketDataProviderRegistry providers,
        MarketMoversCache moversCache,
        MarketDataService marketData,
        UpstoxMarketDataProperties upstoxProperties
    ) {
        this.instruments = instruments;
        this.providers = providers;
        this.moversCache = moversCache;
        this.marketData = marketData;
        this.upstoxProperties = upstoxProperties;
    }

    public MarketMoversResponse current() {
        return moversCache.find()
            .map(this::withCurrentStatus)
            .orElseGet(this::refresh);
    }

    @Scheduled(initialDelay = 45_000, fixedDelay = 300_000)
    public MarketMoversResponse refresh() {
        if (!refreshing.compareAndSet(false, true)) {
            return moversCache.find()
                .map(this::withCurrentStatus)
                .orElseGet(MarketMoversResponse::unavailable);
        }
        try {
            return refreshSnapshot();
        } finally {
            refreshing.set(false);
        }
    }

    private MarketMoversResponse refreshSnapshot() {
        if (!upstoxProperties.hasAnalyticsToken()) {
            LOGGER.info("Market movers refresh is disabled until an Upstox token is configured");
            return moversCache.find()
                .map(this::withCurrentStatus)
                .orElseGet(MarketMoversResponse::unavailable);
        }
        var existing = moversCache.find();
        if (marketData.marketStatus(MarketRegion.INDIA, MarketExchange.NSE)
            == MarketDataStatus.CLOSED
            && existing.isPresent()) {
            return withCurrentStatus(existing.get());
        }
        List<TradableInstrument> universe = instruments
            .findAllByMarketRegionAndExchangeAndInstrumentTypeAndActiveTrueOrderByTradingSymbol(
                MarketRegion.INDIA,
                MarketExchange.NSE,
                InstrumentType.EQUITY
            );
        if (universe.isEmpty()) {
            LOGGER.info("Market movers refresh is waiting for the instrument master");
            return moversCache.find()
                .map(this::withCurrentStatus)
                .orElseGet(MarketMoversResponse::unavailable);
        }

        MarketDataProvider provider = providers.forRegion(MarketRegion.INDIA);
        List<MarketMoverResponse> candidates = new ArrayList<>();
        for (int offset = 0; offset < universe.size(); offset += BATCH_SIZE) {
            List<TradableInstrument> batch = universe.subList(
                offset,
                Math.min(offset + BATCH_SIZE, universe.size())
            );
            collectBatch(provider, batch, candidates);
        }

        if (candidates.isEmpty()) {
            LOGGER.warn("Market movers refresh returned no usable NSE equity quotes");
            return moversCache.find()
                .map(this::withCurrentStatus)
                .orElseGet(MarketMoversResponse::unavailable);
        }

        Comparator<MarketMoverResponse> byChange = Comparator.comparing(
            MarketMoverResponse::changePercent
        );
        List<MarketMoverResponse> gainers = candidates.stream()
            .filter(candidate -> candidate.changePercent().signum() > 0)
            .sorted(byChange.reversed())
            .limit(RESULT_LIMIT)
            .toList();
        List<MarketMoverResponse> losers = candidates.stream()
            .filter(candidate -> candidate.changePercent().signum() < 0)
            .sorted(byChange)
            .limit(RESULT_LIMIT)
            .toList();
        MarketDataStatus status = marketData.marketStatus(
            MarketRegion.INDIA,
            MarketExchange.NSE
        );
        MarketMoversResponse response = new MarketMoversResponse(
            "NSE_EQUITIES",
            Instant.now(),
            status,
            gainers,
            losers
        );
        moversCache.store(response);
        LOGGER.info(
            "Market movers refreshed from {} NSE equity quotes",
            candidates.size()
        );
        return response;
    }

    private MarketMoversResponse withCurrentStatus(MarketMoversResponse snapshot) {
        MarketDataStatus status = marketData.marketStatus(
            MarketRegion.INDIA,
            MarketExchange.NSE
        );
        if (status == MarketDataStatus.LIVE
            && (snapshot.generatedAt() == null
                || snapshot.generatedAt().isBefore(Instant.now().minusSeconds(600)))) {
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

    private void collectBatch(
        MarketDataProvider provider,
        List<TradableInstrument> batch,
        List<MarketMoverResponse> candidates
    ) {
        Map<String, TradableInstrument> byKey = batch.stream().collect(Collectors.toMap(
            TradableInstrument::getInstrumentKey,
            Function.identity(),
            (first, ignored) -> first
        ));
        LinkedHashSet<InstrumentKey> keys = batch.stream()
            .map(this::key)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        try {
            for (Quote quote : provider.getQuotes(keys)) {
                TradableInstrument instrument = byKey.get(quote.instrument().value());
                MarketMoverResponse candidate = toMover(instrument, quote);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                "Could not refresh a market movers batch containing {} instruments",
                batch.size(),
                exception
            );
        }
    }

    private MarketMoverResponse toMover(
        TradableInstrument instrument,
        Quote quote
    ) {
        if (instrument == null
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
            marketData.status(instrument, quote),
            quote.exchangeTimestamp() == null ? quote.receivedAt() : quote.exchangeTimestamp()
        );
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
