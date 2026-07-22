package com.stoxsim.market.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.provider.upstox.UpstoxInstrumentMapper;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.market.api.IndexQuoteResponse;
import com.stoxsim.market.api.IndexQuoteResponse.DataStatus;
import com.stoxsim.market.data.Quote;

@Service
public class IndexQuoteService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final List<IndexDefinition> INDEXES = List.of(
        new IndexDefinition("NIFTY_50", "NIFTY 50", "NSE_INDEX|Nifty 50"),
        new IndexDefinition("NIFTY_BANK", "NIFTY BANK", "NSE_INDEX|Nifty Bank"),
        new IndexDefinition("FINNIFTY", "FINNIFTY", "NSE_INDEX|Nifty Fin Service"),
        new IndexDefinition("NIFTY_IT", "NIFTY IT", "NSE_INDEX|Nifty IT"),
        new IndexDefinition("INDIA_VIX", "INDIA VIX", "NSE_INDEX|India VIX"),
        new IndexDefinition("SENSEX", "SENSEX", "BSE_INDEX|SENSEX")
    );

    private final TradableInstrumentRepository instruments;
    private final MarketDataService marketData;

    public IndexQuoteService(
        TradableInstrumentRepository instruments,
        MarketDataService marketData
    ) {
        this.instruments = instruments;
        this.marketData = marketData;
    }

    public List<IndexQuoteResponse> current() {
        List<String> keys = INDEXES.stream().map(IndexDefinition::instrumentKey).toList();
        Map<String, TradableInstrument> indexed = instruments
            .findAllByProviderAndInstrumentKeyIn(UpstoxInstrumentMapper.PROVIDER, keys)
            .stream()
            .filter(TradableInstrument::isActive)
            .collect(Collectors.toMap(TradableInstrument::getInstrumentKey, Function.identity()));

        return INDEXES.stream()
            .map(definition -> response(definition, indexed.get(definition.instrumentKey())))
            .toList();
    }

    private IndexQuoteResponse response(
        IndexDefinition definition,
        TradableInstrument instrument
    ) {
        if (instrument == null) {
            return unavailable(definition);
        }

        try {
            Quote quote = marketData.latestQuote(instrument);
            if (quote.lastPrice() == null || quote.lastPrice().signum() <= 0) {
                return unavailable(definition);
            }
            BigDecimal change = quote.previousClose() == null
                ? null
                : money(quote.lastPrice().subtract(quote.previousClose()));
            BigDecimal changePercent = change == null
                || quote.previousClose().signum() == 0
                ? null
                : change.multiply(HUNDRED)
                    .divide(quote.previousClose(), 4, RoundingMode.HALF_UP);
            return new IndexQuoteResponse(
                definition.code(),
                definition.label(),
                instrument.getExchange().name(),
                definition.instrumentKey(),
                money(quote.lastPrice()),
                change,
                changePercent,
                quote.previousClose() == null ? null : money(quote.previousClose()),
                marketData.isStale(quote) ? DataStatus.STALE : DataStatus.LIVE,
                quote.exchangeTimestamp() == null ? quote.receivedAt() : quote.exchangeTimestamp()
            );
        } catch (RuntimeException exception) {
            return unavailable(definition);
        }
    }

    private IndexQuoteResponse unavailable(IndexDefinition definition) {
        return new IndexQuoteResponse(
            definition.code(),
            definition.label(),
            definition.instrumentKey().startsWith("BSE_") ? "BSE" : "NSE",
            definition.instrumentKey(),
            null,
            null,
            null,
            null,
            DataStatus.UNAVAILABLE,
            null
        );
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private record IndexDefinition(String code, String label, String instrumentKey) {
    }
}
