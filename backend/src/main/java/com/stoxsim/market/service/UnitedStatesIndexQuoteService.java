package com.stoxsim.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.provider.alpaca.AlpacaInstrumentMapper;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.market.api.IndexQuoteResponse;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.MarketDataStatus;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.provider.MarketDataProviderRegistry;
import com.stoxsim.market.provider.alpaca.AlpacaMarketDataProperties;

@Service
public class UnitedStatesIndexQuoteService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
        UnitedStatesIndexQuoteService.class
    );
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final List<BenchmarkDefinition> BENCHMARKS = List.of(
        new BenchmarkDefinition("SP500", "S&P 500 · SPY", "SPY"),
        new BenchmarkDefinition("NASDAQ_100", "Nasdaq-100 · QQQ", "QQQ"),
        new BenchmarkDefinition("DOW_JONES", "Dow Jones · DIA", "DIA"),
        new BenchmarkDefinition("RUSSELL_2000", "Russell 2000 · IWM", "IWM"),
        new BenchmarkDefinition("TOTAL_US", "Total US Market · VTI", "VTI"),
        new BenchmarkDefinition("VIX_FUTURES", "VIX Futures · VXX", "VXX")
    );

    private final TradableInstrumentRepository instruments;
    private final MarketDataProviderRegistry providers;
    private final MarketDataService marketData;
    private final AlpacaMarketDataProperties properties;

    public UnitedStatesIndexQuoteService(
        TradableInstrumentRepository instruments,
        MarketDataProviderRegistry providers,
        MarketDataService marketData,
        AlpacaMarketDataProperties properties
    ) {
        this.instruments = instruments;
        this.providers = providers;
        this.marketData = marketData;
        this.properties = properties;
    }

    public List<IndexQuoteResponse> current() {
        if (!properties.hasCredentials()) {
            LOGGER.warn("USA benchmark snapshots are unavailable because Alpaca credentials are missing");
            return BENCHMARKS.stream().map(this::unavailable).toList();
        }

        List<String> symbols = BENCHMARKS.stream()
            .map(BenchmarkDefinition::symbol)
            .toList();
        Map<String, TradableInstrument> bySymbol = instruments
            .findAllByProviderAndInstrumentKeyIn(
                AlpacaInstrumentMapper.PROVIDER,
                symbols
            )
            .stream()
            .filter(TradableInstrument::isActive)
            .collect(Collectors.toMap(
                instrument -> instrument.getTradingSymbol().toUpperCase(),
                Function.identity(),
                (first, ignored) -> first
            ));

        if (bySymbol.size() < BENCHMARKS.size()) {
            LOGGER.info(
                "USA benchmark snapshots are waiting for the Alpaca instrument catalogue: {}/{} symbols available",
                bySymbol.size(),
                BENCHMARKS.size()
            );
        }

        LinkedHashSet<InstrumentKey> keys = bySymbol.values().stream()
            .map(this::key)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, Quote> quotes;
        try {
            quotes = providers
                .forRegion(MarketRegion.UNITED_STATES)
                .getQuotes(keys)
                .stream()
                .collect(Collectors.toMap(
                    quote -> quote.instrument().value().toUpperCase(),
                    Function.identity(),
                    (first, ignored) -> first
                ));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not retrieve Alpaca USA benchmark snapshots", exception);
            quotes = Map.of();
        }

        Map<String, Quote> availableQuotes = quotes;
        return BENCHMARKS.stream()
            .map(definition -> response(
                definition,
                bySymbol.get(definition.symbol()),
                availableQuotes.get(definition.symbol())
            ))
            .toList();
    }

    private IndexQuoteResponse response(
        BenchmarkDefinition definition,
        TradableInstrument instrument,
        Quote quote
    ) {
        if (instrument == null
            || quote == null
            || quote.lastPrice() == null
            || quote.lastPrice().signum() <= 0) {
            return unavailable(definition);
        }
        BigDecimal previousClose = quote.previousClose();
        BigDecimal change = previousClose == null
            ? null
            : money(quote.lastPrice().subtract(previousClose));
        BigDecimal changePercent = change == null
            || previousClose.signum() == 0
            ? null
            : change.multiply(HUNDRED)
                .divide(previousClose, 4, RoundingMode.HALF_UP);
        return new IndexQuoteResponse(
            definition.code(),
            definition.label(),
            instrument.getExchange().name(),
            instrument.getInstrumentKey(),
            money(quote.lastPrice()),
            change,
            changePercent,
            previousClose == null ? null : money(previousClose),
            marketData.status(instrument, quote),
            quote.exchangeTimestamp() == null
                ? quote.receivedAt()
                : quote.exchangeTimestamp()
        );
    }

    private IndexQuoteResponse unavailable(BenchmarkDefinition definition) {
        return new IndexQuoteResponse(
            definition.code(),
            definition.label(),
            "US",
            definition.symbol(),
            null,
            null,
            null,
            null,
            MarketDataStatus.UNAVAILABLE,
            null
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

    private record BenchmarkDefinition(
        String code,
        String label,
        String symbol
    ) {
    }
}
