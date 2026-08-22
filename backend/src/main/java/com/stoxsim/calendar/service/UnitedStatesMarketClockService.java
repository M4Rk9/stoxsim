package com.stoxsim.calendar.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;

import com.stoxsim.market.provider.alpaca.AlpacaRestClient;
import com.stoxsim.market.provider.upstox.MarketDataUnavailableException;

import tools.jackson.databind.JsonNode;

@Service
public class UnitedStatesMarketClockService {

    private final AlpacaRestClient alpaca;

    public UnitedStatesMarketClockService(AlpacaRestClient alpaca) {
        this.alpaca = alpaca;
    }

    public UnitedStatesMarketClockSnapshot current() {
        JsonNode root = alpaca.getClock();
        if (root == null || !root.has("is_open")) {
            throw new MarketDataUnavailableException(
                "Alpaca returned an incomplete market clock"
            );
        }
        try {
            return new UnitedStatesMarketClockSnapshot(
                root.get("is_open").asBoolean(),
                timestamp(root, "timestamp"),
                timestamp(root, "next_open"),
                timestamp(root, "next_close")
            );
        } catch (RuntimeException exception) {
            throw new MarketDataUnavailableException(
                "Alpaca returned an invalid market clock",
                exception
            );
        }
    }

    private java.time.Instant timestamp(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing Alpaca clock field " + field);
        }
        return OffsetDateTime.parse(value.asText()).toInstant();
    }
}
