package com.stoxsim.market.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.market.cache.MarketDataCache;
import com.stoxsim.market.data.*;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.provider.MarketDataProviderRegistry;
import com.stoxsim.market.provider.upstox.UpstoxMarketDataProperties;
import com.stoxsim.calendar.service.IndiaMarketSessionService;
import org.junit.jupiter.api.Test;
class MarketDataServiceTest {
    TradableInstrument instrument() {
        var i=mock(TradableInstrument.class);
        when(i.getProvider()).thenReturn("TEST"); when(i.getInstrumentKey()).thenReturn("TEST:AAPL");
        when(i.getMarketRegion()).thenReturn(MarketRegion.UNITED_STATES); when(i.getTickSize()).thenReturn(new BigDecimal("0.01"));
        return i;
    }
    @Test void coldProviderOutageProducesAStableExecutableSimulatedQuote() {
        var cache=mock(MarketDataCache.class); when(cache.findQuote(any())).thenReturn(Optional.empty());
        var providers=mock(MarketDataProviderRegistry.class); when(providers.forRegion(any())).thenThrow(new IllegalStateException("offline"));
        var service=new MarketDataService(mock(TradableInstrumentRepository.class),providers,cache,new UpstoxMarketDataProperties(),mock(IndiaMarketSessionService.class));
        var i=instrument(); var first=service.latestQuote(i); var second=service.latestQuote(i);
        assertThat(first.simulated()).isTrue(); assertThat(first.lastPrice()).isPositive();
        assertThat(first.bid()).isEqualByComparingTo(first.lastPrice()); assertThat(first.ask()).isEqualByComparingTo(first.lastPrice());
        assertThat(second.lastPrice()).isEqualByComparingTo(first.lastPrice()); assertThat(service.isStale(first)).isFalse();
    }
    @Test void fallbackPreservesLastValidPriceAndDoesNotReuseOldBidAsk() {
        var service=new MarketDataService(null,null,null,new UpstoxMarketDataProperties(),null);
        var anchor=new Quote(new InstrumentKey("TEST","TEST:AAPL",MarketRegion.UNITED_STATES),new BigDecimal("123.45"),new BigDecimal("1"),new BigDecimal("999"),null,null,null,null,new BigDecimal("120"),10L,Instant.EPOCH,Instant.EPOCH);
        var quote=service.simulatedQuote(instrument(),anchor);
        assertThat(quote.simulated()).isTrue(); assertThat(quote.lastPrice()).isEqualByComparingTo("123.45");
        assertThat(quote.bid()).isEqualByComparingTo("123.45"); assertThat(quote.ask()).isEqualByComparingTo("123.45");
        assertThat(quote.previousClose()).isEqualByComparingTo("120"); assertThat(service.isStale(quote)).isFalse();
    }
}
