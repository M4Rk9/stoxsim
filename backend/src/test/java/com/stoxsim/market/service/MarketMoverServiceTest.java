package com.stoxsim.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.instrument.service.InstrumentSnapshot;
import com.stoxsim.market.cache.MarketMoversCache;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.MarketDataStatus;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.provider.MarketDataProvider;
import com.stoxsim.market.provider.MarketDataProviderRegistry;
import com.stoxsim.market.provider.upstox.UpstoxMarketDataProperties;

@ExtendWith(MockitoExtension.class)
class MarketMoverServiceTest {

    @Mock private TradableInstrumentRepository instruments;
    @Mock private MarketDataProviderRegistry providers;
    @Mock private MarketDataProvider provider;
    @Mock private MarketMoversCache moversCache;
    @Mock private MarketDataService marketData;
    @Mock private UpstoxMarketDataProperties upstoxProperties;

    @Test
    void ranksOnlyNifty100PositiveAndNegativeMoves() {
        List<TradableInstrument> universe = List.of(
            equity("RELIANCE", "Reliance Industries"),
            equity("TCS", "Tata Consultancy Services"),
            equity("INFY", "Infosys"),
            equity("ITC", "ITC"),
            equity("AAA", "Not a NIFTY 100 constituent")
        );
        when(instruments
            .findAllByMarketRegionAndExchangeAndInstrumentTypeAndActiveTrueOrderByTradingSymbol(
                MarketRegion.INDIA,
                MarketExchange.NSE,
                InstrumentType.EQUITY
            ))
            .thenReturn(universe);
        when(providers.forRegion(MarketRegion.INDIA)).thenReturn(provider);
        when(provider.getQuotes(anySet())).thenReturn(List.of(
            quote("RELIANCE", "112", "100"),
            quote("TCS", "105", "100"),
            quote("INFY", "92", "100"),
            quote("ITC", "98", "100")
        ));
        when(marketData.status(any(), any())).thenReturn(MarketDataStatus.CLOSED);
        when(marketData.marketStatus(MarketRegion.INDIA, MarketExchange.NSE))
            .thenReturn(MarketDataStatus.CLOSED);
        when(moversCache.find()).thenReturn(Optional.empty());
        when(upstoxProperties.hasAnalyticsToken()).thenReturn(true);

        var response = new MarketMoverService(
            instruments,
            providers,
            moversCache,
            marketData,
            upstoxProperties
        ).refresh();

        assertThat(response.universe()).isEqualTo("NIFTY_100");
        assertThat(response.dataStatus()).isEqualTo(MarketDataStatus.CLOSED);
        assertThat(response.gainers())
            .extracting(mover -> mover.symbol())
            .containsExactly("RELIANCE", "TCS");
        assertThat(response.losers())
            .extracting(mover -> mover.symbol())
            .containsExactly("INFY", "ITC");
        assertThat(response.gainers())
            .extracting(mover -> mover.symbol())
            .doesNotContain("AAA");
    }

    private TradableInstrument equity(String symbol, String name) {
        return new TradableInstrument(
            new InstrumentSnapshot(
                "UPSTOX",
                "NSE_EQ|" + symbol,
                MarketRegion.INDIA,
                MarketExchange.NSE,
                "NSE_EQ",
                symbol,
                name,
                null,
                InstrumentType.EQUITY,
                "INR",
                1,
                new BigDecimal("0.05"),
                "NORMAL"
            ),
            UUID.randomUUID(),
            Instant.now()
        );
    }

    private Quote quote(String symbol, String last, String previousClose) {
        Instant now = Instant.now();
        return new Quote(
            new InstrumentKey("UPSTOX", "NSE_EQ|" + symbol, MarketRegion.INDIA),
            new BigDecimal(last),
            null,
            null,
            null,
            null,
            null,
            null,
            new BigDecimal(previousClose),
            1000L,
            now,
            now
        );
    }
}
