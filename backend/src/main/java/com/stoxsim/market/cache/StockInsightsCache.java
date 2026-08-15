package com.stoxsim.market.cache;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.stoxsim.market.api.StockInsightsResponse;

import tools.jackson.databind.ObjectMapper;

@Component
public class StockInsightsCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(StockInsightsCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public StockInsightsCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Optional<StockInsightsResponse> find(
        String provider,
        String identifier,
        String timePeriod
    ) {
        try {
            String encoded = redis.opsForValue().get(key(provider, identifier, timePeriod));
            return encoded == null
                ? Optional.empty()
                : Optional.of(objectMapper.readValue(encoded, StockInsightsResponse.class));
        } catch (Exception exception) {
            LOGGER.warn(
                "Could not read cached {} fundamentals for {}",
                provider,
                identifier,
                exception
            );
            return Optional.empty();
        }
    }

    public void store(
        String provider,
        String identifier,
        String timePeriod,
        StockInsightsResponse response,
        Duration ttl
    ) {
        try {
            redis.opsForValue().set(
                key(provider, identifier, timePeriod),
                objectMapper.writeValueAsString(response),
                ttl
            );
        } catch (Exception exception) {
            LOGGER.warn(
                "Could not cache {} fundamentals for {}",
                provider,
                identifier,
                exception
            );
        }
    }

    private String key(String provider, String identifier, String timePeriod) {
        return "market:fundamentals:"
            + provider.toUpperCase(Locale.ROOT)
            + ":"
            + identifier.toUpperCase(Locale.ROOT)
            + ":"
            + timePeriod;
    }
}
