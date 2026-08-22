package com.stoxsim.market.provider.alpaca;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.stoxsim.market.provider.upstox.MarketDataUnavailableException;

import tools.jackson.databind.JsonNode;

@Component
public class AlpacaRestClient {

    private final AlpacaMarketDataProperties properties;
    private final RestClient dataClient;
    private final RestClient tradingClient;

    public AlpacaRestClient(AlpacaMarketDataProperties properties) {
        this.properties = properties;
        this.dataClient = client(properties.getDataBaseUrl());
        this.tradingClient = client(properties.getTradingBaseUrl());
    }

    public Map<String, JsonNode> getSnapshots(Collection<String> symbols) {
        requireCredentials();
        if (symbols.isEmpty()) {
            return Map.of();
        }
        String requested = symbols.stream().collect(Collectors.joining(","));
        try {
            JsonNode root = dataClient.get()
                .uri(uri -> uri
                    .path("/v2/stocks/snapshots")
                    .queryParam("symbols", requested)
                    .queryParam("feed", properties.getFeed())
                    .build())
                .retrieve()
                .body(JsonNode.class);
            JsonNode container = root != null && root.has("snapshots")
                ? root.get("snapshots")
                : root;
            if (container == null || !container.isObject()) {
                return Map.of();
            }
            Map<String, JsonNode> snapshots = new LinkedHashMap<>();
            for (String symbol : symbols) {
                JsonNode snapshot = container.get(symbol);
                if (snapshot != null && !snapshot.isNull()) {
                    snapshots.put(symbol, snapshot);
                }
            }
            return Map.copyOf(snapshots);
        } catch (RestClientException exception) {
            throw new MarketDataUnavailableException(
                "Could not retrieve Alpaca stock snapshots",
                exception
            );
        }
    }

    public JsonNode getBars(
        String symbol,
        String timeframe,
        LocalDate from,
        LocalDate to
    ) {
        requireCredentials();
        try {
            String start = from.atStartOfDay().toInstant(ZoneOffset.UTC).toString();
            String end = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toString();
            return dataClient.get()
                .uri(uri -> uri
                    .path("/v2/stocks/{symbol}/bars")
                    .queryParam("timeframe", timeframe)
                    .queryParam("start", start)
                    .queryParam("end", end)
                    .queryParam("adjustment", "all")
                    .queryParam("feed", properties.getFeed())
                    .queryParam("limit", 10000)
                    .build(symbol))
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new MarketDataUnavailableException(
                "Could not retrieve Alpaca historical bars for " + symbol,
                exception
            );
        }
    }

    public JsonNode getMovers(int top) {
        requireCredentials();
        try {
            return dataClient.get()
                .uri(uri -> uri
                    .path("/v1beta1/screener/stocks/movers")
                    .queryParam("top", top)
                    .build())
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new MarketDataUnavailableException(
                "Could not retrieve Alpaca top market movers",
                exception
            );
        }
    }

    public JsonNode getAssets() {
        requireCredentials();
        try {
            return tradingClient.get()
                .uri(uri -> uri
                    .path("/v2/assets")
                    .queryParam("status", "active")
                    .queryParam("asset_class", "us_equity")
                    .build())
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new MarketDataUnavailableException(
                "Could not retrieve the Alpaca US asset catalogue",
                exception
            );
        }
    }

    public JsonNode getClock() {
        requireCredentials();
        try {
            return tradingClient.get()
                .uri("/v2/clock")
                .retrieve()
                .body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new MarketDataUnavailableException(
                "Could not retrieve the Alpaca market clock",
                exception
            );
        }
    }

    private RestClient client(String baseUrl) {
        return RestClient.builder()
            .baseUrl(baseUrl)
            .defaultHeader("APCA-API-KEY-ID", value(properties.getKeyId()))
            .defaultHeader("APCA-API-SECRET-KEY", value(properties.getSecretKey()))
            .build();
    }

    private String value(String input) {
        return input == null ? "" : input;
    }

    private void requireCredentials() {
        if (!properties.hasCredentials()) {
            throw new MarketDataUnavailableException(
                "Alpaca credentials are not configured"
            );
        }
    }
}
