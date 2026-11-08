package com.stoxsim.market.provider.upstox;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.TreeMap;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.stoxsim.market.data.Candle;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class UpstoxHistoricalCandleClient {

    private static final String BASE_URL = "https://api.upstox.com/v3/historical-candle";

    private final Clock clock;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final UpstoxClientFactory clientFactory;

    public UpstoxHistoricalCandleClient(
        ObjectMapper objectMapper,
        UpstoxClientFactory clientFactory,
        Clock clock
    ) {
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.clientFactory = clientFactory;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    }

    /** Historical V3 excludes today's candles; combine it with the intraday endpoint. */
    public List<Candle> getRange(String instrumentKey, String unit, int interval, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
        boolean currentDay = !from.isAfter(today) && !to.isBefore(today)
            && List.of("minutes", "hours", "days").contains(unit);
        if (!currentDay) return getCandles(instrumentKey, unit, interval, from, to);
        var rows = new TreeMap<Instant, Candle>();
        if (from.isBefore(today)) {
            for (Candle candle : getCandles(instrumentKey, unit, interval, from, today.minusDays(1))) {
                rows.put(candle.timestamp(), candle);
            }
        }
        for (Candle candle : getIntradayCandles(instrumentKey, unit, interval)) {
            if (candle.timestamp().atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().equals(today)) {
                rows.put(candle.timestamp(), candle);
            }
        }
        return List.copyOf(rows.values());
    }

    public List<Candle> getIntradayCandles(String instrumentKey, String unit, int interval) {
        return request(URI.create(String.format("%s/intraday/%s/%s/%d",
            BASE_URL, pathSegment(instrumentKey), pathSegment(unit), interval)));
    }

    public List<Candle> getCandles(
        String instrumentKey,
        String unit,
        int interval,
        LocalDate from,
        LocalDate to
    ) {
        URI uri = URI.create(String.format(
            "%s/%s/%s/%d/%s/%s",
            BASE_URL,
            pathSegment(instrumentKey),
            pathSegment(unit),
            interval,
            to,
            from
        ));
        return request(uri);
    }

    private List<Candle> request(URI uri) {
        String authorization = clientFactory.authorizationHeaders().get("Authorization");
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")
            .header("Authorization", authorization)
            .header("User-Agent", "StoxSim/0.1")
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new MarketDataUnavailableException(
                    "Upstox historical candles returned HTTP " + response.statusCode()
                );
            }
            JsonNode candles = objectMapper.readTree(response.body())
                .path("data")
                .path("candles");
            if (!candles.isArray()) {
                return List.of();
            }

            List<Candle> result = new ArrayList<>();
            for (JsonNode row : candles) {
                if (!row.isArray() || row.size() < 6) {
                    continue;
                }
                try {
                    result.add(new Candle(
                        OffsetDateTime.parse(row.get(0).asText()).toInstant(),
                        decimal(row.get(1)),
                        decimal(row.get(2)),
                        decimal(row.get(3)),
                        decimal(row.get(4)),
                        row.get(5).asLong()
                    ));
                } catch (RuntimeException ignored) {
                    // One malformed provider row must not suppress the remaining series.
                }
            }
            return List.copyOf(result);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MarketDataUnavailableException(
                "Upstox historical candle request was interrupted",
                exception
            );
        } catch (IOException exception) {
            throw new MarketDataUnavailableException(
                "Could not retrieve Upstox historical candles",
                exception
            );
        }
    }

    private String pathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private BigDecimal decimal(JsonNode value) {
        return new BigDecimal(value.asText());
    }
}
