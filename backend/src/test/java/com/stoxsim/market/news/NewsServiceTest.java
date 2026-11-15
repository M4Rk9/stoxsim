package com.stoxsim.market.news;

import com.stoxsim.instrument.domain.*;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.provider.alpaca.AlpacaRestClient;
import com.stoxsim.market.provider.upstox.UpstoxNewsClient;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class NewsServiceTest {
    @Test void disabledNewsNeverCallsEitherProvider() {
        var repo = mock(TradableInstrumentRepository.class);
        var listing = mock(TradableInstrument.class);
        when(repo.findByMarketRegionAndExchangeAndTradingSymbolIgnoreCaseAndActiveTrue(MarketRegion.INDIA,MarketExchange.NSE,"TCS"))
            .thenReturn(Optional.of(listing));
        var upstox = mock(UpstoxNewsClient.class); var alpaca = mock(AlpacaRestClient.class);
        var service = new NewsService(repo,new NewsProperties(),upstox,alpaca);
        assertThat(service.get(MarketRegion.INDIA,MarketExchange.NSE,"TCS").status()).isEqualTo(NewsResponse.Status.DISABLED);
        verifyNoInteractions(upstox,alpaca);
    }
    @Test void providerFailuresAreUnavailableAndDoNotExposeExceptionDetails() {
        var repo = mock(TradableInstrumentRepository.class); var listing = mock(TradableInstrument.class);
        when(repo.findByMarketRegionAndExchangeAndTradingSymbolIgnoreCaseAndActiveTrue(MarketRegion.INDIA,MarketExchange.NSE,"TCS"))
            .thenReturn(Optional.of(listing));
        when(listing.getInstrumentType()).thenReturn(InstrumentType.EQUITY);
        when(listing.getInstrumentKey()).thenReturn("NSE_EQ|TEST");
        var upstox = mock(UpstoxNewsClient.class); var alpaca = mock(AlpacaRestClient.class);
        when(upstox.get("NSE_EQ|TEST")).thenThrow(new IllegalStateException("sensitive-provider-diagnostic"));
        var properties = new NewsProperties(); properties.setUpstoxEnabled(true);
        var result = new NewsService(repo,properties,upstox,alpaca).get(MarketRegion.INDIA,MarketExchange.NSE,"TCS");
        assertThat(result.status()).isEqualTo(NewsResponse.Status.UNAVAILABLE);
        assertThat(result.message()).doesNotContain("sensitive-provider-diagnostic");
        assertThat(result.articles()).isEmpty(); verifyNoInteractions(alpaca);
    }
}
