package com.stoxsim.market.provider.alpaca;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "stoxsim.market-data.alpaca")
public class AlpacaMarketDataProperties {

    private String keyId;
    private String secretKey;
    private String dataBaseUrl = "https://data.alpaca.markets";
    private String tradingBaseUrl = "https://paper-api.alpaca.markets";
    private String feed = "iex";
    private boolean instrumentSyncOnStartup = true;
    private boolean pollingEnabled = true;
    private long pollingIntervalMillis = 5000;

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getDataBaseUrl() {
        return dataBaseUrl;
    }

    public void setDataBaseUrl(String dataBaseUrl) {
        this.dataBaseUrl = dataBaseUrl;
    }

    public String getTradingBaseUrl() {
        return tradingBaseUrl;
    }

    public void setTradingBaseUrl(String tradingBaseUrl) {
        this.tradingBaseUrl = tradingBaseUrl;
    }

    public String getFeed() {
        return feed;
    }

    public void setFeed(String feed) {
        this.feed = feed;
    }

    public boolean isInstrumentSyncOnStartup() {
        return instrumentSyncOnStartup;
    }

    public void setInstrumentSyncOnStartup(boolean instrumentSyncOnStartup) {
        this.instrumentSyncOnStartup = instrumentSyncOnStartup;
    }

    public boolean isPollingEnabled() {
        return pollingEnabled;
    }

    public void setPollingEnabled(boolean pollingEnabled) {
        this.pollingEnabled = pollingEnabled;
    }

    public long getPollingIntervalMillis() {
        return pollingIntervalMillis;
    }

    public void setPollingIntervalMillis(long pollingIntervalMillis) {
        this.pollingIntervalMillis = pollingIntervalMillis;
    }

    public boolean hasCredentials() {
        return keyId != null && !keyId.isBlank()
            && secretKey != null && !secretKey.isBlank();
    }
}
