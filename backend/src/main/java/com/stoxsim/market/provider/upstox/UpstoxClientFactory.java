package com.stoxsim.market.provider.upstox;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.upstox.ApiClient;

@Component
public class UpstoxClientFactory {

    private final UpstoxMarketDataProperties properties;

    public UpstoxClientFactory(UpstoxMarketDataProperties properties) {
        this.properties = properties;
    }

    public ApiClient createClient() {
        if (!properties.hasAnalyticsToken()) {
            throw new MarketDataUnavailableException(
                "UPSTOX_ANALYTICS_TOKEN is required for India market data"
            );
        }
        var client = new ApiClient();
        client.setAccessToken(properties.getAnalyticsToken());
        client.setUserAgent("StoxSim/0.1");
        return client;
    }

    Map<String, String> authorizationHeaders() {
        if (!properties.hasAnalyticsToken()) {
            throw new MarketDataUnavailableException(
                "UPSTOX_ANALYTICS_TOKEN is required for India market data"
            );
        }
        return Map.of("Authorization", "Bearer " + properties.getAnalyticsToken());
    }
}
