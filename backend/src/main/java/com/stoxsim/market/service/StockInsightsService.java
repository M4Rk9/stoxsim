package com.stoxsim.market.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.upstox.ApiException;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.market.api.StockInsightsResponse;
import com.stoxsim.market.api.StockInsightsResponse.CompanyProfile;
import com.stoxsim.market.api.StockInsightsResponse.FinancialPerformance;
import com.stoxsim.market.api.StockInsightsResponse.FundamentalRatio;
import com.stoxsim.market.api.StockInsightsResponse.FundamentalsStatus;
import com.stoxsim.market.cache.StockInsightsCache;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.provider.upstox.UpstoxFundamentalsClient;
import com.stoxsim.market.provider.upstox.UpstoxMarketDataProperties;

@Service
public class StockInsightsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockInsightsService.class);

    private final TradableInstrumentRepository instruments;
    private final UpstoxFundamentalsClient client;
    private final StockInsightsCache cache;
    private final UpstoxMarketDataProperties properties;

    public StockInsightsService(
        TradableInstrumentRepository instruments,
        UpstoxFundamentalsClient client,
        StockInsightsCache cache,
        UpstoxMarketDataProperties properties
    ) {
        this.instruments = instruments;
        this.client = client;
        this.cache = cache;
        this.properties = properties;
    }

    public StockInsightsResponse get(
        MarketRegion marketRegion,
        MarketExchange exchange,
        String symbol,
        String requestedPeriod
    ) {
        String timePeriod = normalizePeriod(requestedPeriod);
        var instrument = instruments
            .findByMarketRegionAndExchangeAndTradingSymbolIgnoreCaseAndActiveTrue(
                marketRegion,
                exchange,
                symbol
            )
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Instrument not found"
            ));

        String isin = instrument.getIsin();
        if (isin == null || isin.isBlank()) {
            return unavailable(
                instrument.getProvider(),
                instrument.getTradingSymbol(),
                null,
                "Fundamentals are not available for this instrument."
            );
        }

        var cached = cache.find(isin, timePeriod);
        if (cached.isPresent()) {
            return cached.get();
        }

        CompanyProfile profile = null;
        List<FundamentalRatio> ratios = List.of();
        FinancialPerformance financials = null;
        int failures = 0;

        try {
            profile = client.getProfile(isin);
        } catch (ApiException exception) {
            failures++;
            logFailure("profile", isin, exception);
        }
        try {
            ratios = client.getRatios(isin);
        } catch (ApiException exception) {
            failures++;
            logFailure("ratios", isin, exception);
        }
        try {
            financials = client.getFinancials(isin, timePeriod);
        } catch (ApiException exception) {
            failures++;
            logFailure("income statement", isin, exception);
        }

        boolean hasProfile = profile != null;
        boolean hasRatios = !ratios.isEmpty();
        boolean hasFinancials = financials != null;
        int available = (hasProfile ? 1 : 0) + (hasRatios ? 1 : 0) + (hasFinancials ? 1 : 0);
        FundamentalsStatus status = available == 3
            ? FundamentalsStatus.AVAILABLE
            : available > 0 ? FundamentalsStatus.PARTIAL : FundamentalsStatus.UNAVAILABLE;
        String message = status == FundamentalsStatus.AVAILABLE
            ? null
            : status == FundamentalsStatus.PARTIAL
                ? "Some company data is temporarily unavailable."
                : "Company fundamentals are temporarily unavailable.";

        var response = new StockInsightsResponse(
            instrument.getProvider(),
            instrument.getTradingSymbol(),
            isin,
            Instant.now(),
            status,
            profile,
            ratios,
            financials,
            message
        );
        Duration ttl = status == FundamentalsStatus.AVAILABLE
            ? Duration.ofHours(properties.getFundamentalsTtlHours())
            : Duration.ofMinutes(failures > 0 ? 15 : 60);
        cache.store(isin, timePeriod, response, ttl);
        return response;
    }

    private String normalizePeriod(String value) {
        String normalized = value == null
            ? "quarterly"
            : value.trim().toLowerCase(Locale.ROOT);
        if (!"quarterly".equals(normalized) && !"yearly".equals(normalized)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "timePeriod must be quarterly or yearly"
            );
        }
        return normalized;
    }

    private StockInsightsResponse unavailable(
        String provider,
        String symbol,
        String isin,
        String message
    ) {
        return new StockInsightsResponse(
            provider,
            symbol,
            isin,
            Instant.now(),
            FundamentalsStatus.UNAVAILABLE,
            null,
            List.of(),
            null,
            message
        );
    }

    private void logFailure(String section, String isin, ApiException exception) {
        LOGGER.warn(
            "Could not retrieve Upstox {} for ISIN {}: HTTP {}",
            section,
            isin,
            exception.getCode()
        );
    }
}
