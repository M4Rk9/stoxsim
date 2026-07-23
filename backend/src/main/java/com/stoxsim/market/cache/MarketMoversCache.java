package com.stoxsim.market.cache;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.stoxsim.market.api.MarketMoversResponse;

import tools.jackson.databind.ObjectMapper;

@Component
public class MarketMoversCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(MarketMoversCache.class);
    private static final String KEY = "market:movers:india:nse-equities";
    private static final Duration RETENTION = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public MarketMoversCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public Optional<MarketMoversResponse> find() {
        try {
            String value = redis.opsForValue().get(KEY);
            return value == null
                ? Optional.empty()
                : Optional.of(objectMapper.readValue(value, MarketMoversResponse.class));
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not read market movers cache", exception);
            return Optional.empty();
        }
    }

    public void store(MarketMoversResponse response) {
        try {
            redis.opsForValue().set(
                KEY,
                objectMapper.writeValueAsString(response),
                RETENTION
            );
        } catch (RuntimeException exception) {
            LOGGER.warn("Could not store market movers cache", exception);
        }
    }
}
