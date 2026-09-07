package com.stoxsim.market.provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.stoxsim.market.provider.alpaca.AlpacaMarketDataProperties;
import com.stoxsim.market.provider.alpaca.AlpacaRestClient;
import com.stoxsim.market.provider.upstox.MarketDataUnavailableException;
import com.stoxsim.market.provider.upstox.UpstoxClientFactory;
import com.stoxsim.market.provider.upstox.UpstoxMarketDataProperties;

class PublicMarketDataServingGuardTest {

    @Test
    void blocksUpstoxEvenWhenATokenIsConfigured() {
        var properties = new UpstoxMarketDataProperties();
        properties.setAnalyticsToken("configured-token");
        properties.setPublicServingEnabled(false);

        var factory = new UpstoxClientFactory(properties);

        assertThatThrownBy(factory::createClient)
            .isInstanceOf(MarketDataUnavailableException.class)
            .hasMessageContaining("public market-data serving is disabled");
    }

    @Test
    void blocksAlpacaEvenWhenCredentialsAreConfigured() {
        var properties = new AlpacaMarketDataProperties();
        properties.setKeyId("configured-key");
        properties.setSecretKey("configured-secret");
        properties.setPublicServingEnabled(false);

        var client = new AlpacaRestClient(properties);

        assertThatThrownBy(() -> client.getSnapshots(List.of("AAPL")))
            .isInstanceOf(MarketDataUnavailableException.class)
            .hasMessageContaining("public market-data serving is disabled");
    }
}
