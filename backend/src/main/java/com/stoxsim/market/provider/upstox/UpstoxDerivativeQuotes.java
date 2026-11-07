package com.stoxsim.market.provider.upstox;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.stoxsim.market.cache.MarketDataCache;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.Quote;
import com.stoxsim.market.domain.MarketRegion;

@Component
public class UpstoxDerivativeQuotes {
    private static final String ENDPOINT = "https://api.upstox.com/v2/market-quote/quotes?instrument_key=";
    private static final int MAX_BATCH = 64;
    private final UpstoxClientFactory clients;
    private final MarketDataCache cache;
    private final ObjectMapper json;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    public UpstoxDerivativeQuotes(UpstoxClientFactory clients, MarketDataCache cache, ObjectMapper json, Clock clock) {
        this.clients = clients;
        this.cache = cache;
        this.json = json;
        this.clock = clock;
    }

    public Map<String, Quote> snapshots(List<String> requested) {
        List<String> keys = requested.stream().distinct().toList();
        if (keys.isEmpty() || keys.size() > MAX_BATCH || keys.stream().anyMatch(key -> !key.matches("NSE_FO\\|[0-9]{1,16}"))) return Map.of();
        Map<String, Quote> result = new HashMap<>();
        for (String key : keys) cache.findQuote(key(key)).filter(quote -> key(key).equals(quote.instrument()) && !quote.simulated())
            .ifPresent(quote -> result.put(key, quote));
        List<String> missing = keys.stream().filter(key -> {
            Quote quote = result.get(key);
            return quote == null || quote.receivedAt() == null || quote.receivedAt().isBefore(clock.instant().minusSeconds(8));
        }).toList();
        if (missing.isEmpty()) return result;

        String encoded = URLEncoder.encode(String.join(",", missing), StandardCharsets.UTF_8);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ENDPOINT + encoded))
                .timeout(Duration.ofSeconds(6))
                .header("Accept", "application/json")
                .header("Authorization", clients.authorizationHeaders().get("Authorization"))
                .GET().build();
            var pending = http.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
            try {
                var response = pending.get(6, TimeUnit.SECONDS);
                if (response.statusCode() != 200 || response.body().length > 1_000_000) return result;
                var received = parse(json.readTree(response.body()).path("data"), missing, clock.instant());
                received.forEach((key, quote) -> { cache.storeQuote(quote); result.put(key, quote); });
            } catch (TimeoutException | InterruptedException exception) {
                pending.cancel(true);
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            }
        } catch (ExecutionException | MarketDataUnavailableException exception) {
            // Cached data can be displayed, but an expired observation cannot execute.
        }
        return result;
    }

    static Map<String, Quote> parse(JsonNode data, List<String> requested, Instant receivedAt) {
        Map<String, Quote> result = new HashMap<>();
        var allowed = requested.stream().collect(Collectors.toSet());
        if (!data.isObject()) return result;
        for (JsonNode row : data) {
            String key = row.path("instrument_token").asText();
            if (!allowed.contains(key)) continue;
            BigDecimal last = positive(row.path("last_price"));
            long traded = row.path("last_trade_time").asLong(0);
            if (last == null || traded <= 0) continue;
            result.put(key, new Quote(key(key), last,
                positive(row.path("depth").path("buy").path(0).path("price")),
                positive(row.path("depth").path("sell").path(0).path("price")),
                null, null, null, null, null,
                row.path("volume").isIntegralNumber() ? row.path("volume").asLong() : null,
                Instant.ofEpochMilli(traded), receivedAt));
        }
        return result;
    }

    private static BigDecimal positive(JsonNode node) {
        if (!node.isNumber()) return null;
        BigDecimal amount = node.decimalValue();
        return amount.signum() > 0 && amount.compareTo(BigDecimal.valueOf(1_000_000)) <= 0 ? amount : null;
    }

    private static InstrumentKey key(String key) {
        return new InstrumentKey("UPSTOX", key, MarketRegion.INDIA);
    }

    public boolean isFresh(Quote quote) {
        if (quote == null || quote.simulated() || quote.lastPrice() == null || quote.lastPrice().signum() <= 0
            || quote.exchangeTimestamp() == null || quote.receivedAt() == null) return false;
        Instant now = clock.instant();
        return !quote.exchangeTimestamp().isBefore(now.minusSeconds(60)) && !quote.exchangeTimestamp().isAfter(now.plusSeconds(2))
            && !quote.receivedAt().isBefore(now.minusSeconds(15)) && !quote.receivedAt().isAfter(now.plusSeconds(2));
    }
}
