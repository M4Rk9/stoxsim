package com.stoxsim.market.provider;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.stoxsim.market.domain.MarketRegion;

@Component
public class MarketDataProviderRegistry {

    private final Map<MarketRegion, MarketDataProvider> providers;

    public MarketDataProviderRegistry(List<MarketDataProvider> providers) {
        var byRegion = new EnumMap<MarketRegion, MarketDataProvider>(MarketRegion.class);
        for (var provider : providers) {
            var previous = byRegion.put(provider.marketRegion(), provider);
            if (previous != null) {
                throw new IllegalStateException(
                    "Multiple market-data providers configured for " + provider.marketRegion()
                );
            }
        }
        this.providers = Map.copyOf(byRegion);
    }

    public MarketDataProvider forRegion(MarketRegion region) {
        var provider = providers.get(region);
        if (provider == null) {
            throw new IllegalStateException("No market-data provider configured for " + region);
        }
        return provider;
    }
}
