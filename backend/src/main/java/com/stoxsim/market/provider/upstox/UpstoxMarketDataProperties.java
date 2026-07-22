package com.stoxsim.market.provider.upstox;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stoxsim.market-data.upstox")
public class UpstoxMarketDataProperties {

    private String analyticsToken;
    private boolean streamEnabled;
    private int quoteTtlSeconds = 30;
    private int candleTtlMinutes = 5;
    private int staleAfterSeconds = 15;
    private List<String> initialInstrumentKeys = new ArrayList<>();

    public String getAnalyticsToken() {
        return analyticsToken;
    }

    public void setAnalyticsToken(String analyticsToken) {
        this.analyticsToken = analyticsToken;
    }

    public boolean isStreamEnabled() {
        return streamEnabled;
    }

    public void setStreamEnabled(boolean streamEnabled) {
        this.streamEnabled = streamEnabled;
    }

    public int getQuoteTtlSeconds() {
        return quoteTtlSeconds;
    }

    public void setQuoteTtlSeconds(int quoteTtlSeconds) {
        this.quoteTtlSeconds = quoteTtlSeconds;
    }

    public int getCandleTtlMinutes() {
        return candleTtlMinutes;
    }

    public void setCandleTtlMinutes(int candleTtlMinutes) {
        this.candleTtlMinutes = candleTtlMinutes;
    }

    public int getStaleAfterSeconds() {
        return staleAfterSeconds;
    }

    public void setStaleAfterSeconds(int staleAfterSeconds) {
        this.staleAfterSeconds = staleAfterSeconds;
    }

    public List<String> getInitialInstrumentKeys() {
        return initialInstrumentKeys;
    }

    public void setInitialInstrumentKeys(List<String> initialInstrumentKeys) {
        this.initialInstrumentKeys = new ArrayList<>(initialInstrumentKeys);
    }

    public boolean hasAnalyticsToken() {
        return analyticsToken != null && !analyticsToken.isBlank();
    }
}
