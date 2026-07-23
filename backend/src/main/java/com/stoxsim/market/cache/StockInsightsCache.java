package com.stoxsim.market.cache;

import java.time.Duration;
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

    public Optional<StockInsightsResponse> find(String isin, String timePeriod) {
        try {
            String encoded = redis.opsForValue().get(key(isin, timePeriod));
            return encoded == null
                ? Optional.empty()
                : Optional.of(objectMapper.readValue(encoded, StockInsightsResponse.class));
        } catch (Exception exception) {
            LOGGER.warn("Could not read cached fundamentals for {}", isin, exception);
            return Optional.empty();
        }
    }

    public void store(
        String isin,
        String timePeriod,
        StockInsightsResponse response,
        Duration ttl
    ) {
        try {
            redis.opsForValue().set(
                key(isin, timePeriod),
                objectMapper.writeValueAsString(response),
                ttl
            );
        } catch (Exception exception) {
            LOGGER.warn("Could not cache fundamentals for {}", isin, exception);
        }
    }

    private String key(String isin, String timePeriod) {
        return "market:fundamentals:UPSTOX:" + isin + ":" + timePeriod;
    }
}
