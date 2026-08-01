package com.stoxsim.market.provider.alpaca;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.stoxsim.market.data.Candle;
import com.stoxsim.market.data.InstrumentKey;
import com.stoxsim.market.data.Quote;

import tools.jackson.databind.JsonNode;

@Component
public class AlpacaQuoteMapper {

    public Quote mapQuote(
        InstrumentKey instrument,
        JsonNode snapshot,
        Instant receivedAt
    ) {
        JsonNode latestTrade = snapshot.path("latestTrade");
        JsonNode latestQuote = snapshot.path("latestQuote");
        JsonNode minuteBar = snapshot.path("minuteBar");
        JsonNode dailyBar = snapshot.path("dailyBar");
        JsonNode previousDailyBar = snapshot.path("prevDailyBar");

        BigDecimal lastPrice = firstDecimal(
            decimal(latestTrade, "p"),
            decimal(minuteBar, "c"),
            decimal(dailyBar, "c")
        );
        if (lastPrice == null) {
            return null;
        }
        Instant exchangeTimestamp = firstInstant(
            instant(latestTrade, "t"),
            instant(minuteBar, "t"),
            instant(dailyBar, "t")
        );
        return new Quote(
            instrument,
            lastPrice,
            decimal(latestQuote, "bp"),
            decimal(latestQuote, "ap"),
            decimal(dailyBar, "o"),
            decimal(dailyBar, "h"),
            decimal(dailyBar, "l"),
            decimal(dailyBar, "c"),
            decimal(previousDailyBar, "c"),
            longValue(dailyBar, "v"),
            exchangeTimestamp,
            receivedAt
        );
    }

    public List<Candle> mapBars(JsonNode response) {
        if (response == null || !response.path("bars").isArray()) {
            return List.of();
        }
        List<Candle> result = new ArrayList<>();
        for (JsonNode bar : response.path("bars")) {
            Instant timestamp = instant(bar, "t");
            BigDecimal open = decimal(bar, "o");
            BigDecimal high = decimal(bar, "h");
            BigDecimal low = decimal(bar, "l");
            BigDecimal close = decimal(bar, "c");
            if (timestamp == null
                || open == null
                || high == null
                || low == null
                || close == null) {
                continue;
            }
            result.add(new Candle(
                timestamp,
                open,
                high,
                low,
                close,
                longValue(bar, "v")
            ));
        }
        return List.copyOf(result);
    }

    private BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.isNumber()
            ? null
            : value.decimalValue();
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() || !value.isNumber()
            ? null
            : value.longValue();
    }

    private Instant instant(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        try {
            return Instant.parse(value.textValue());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private BigDecimal firstDecimal(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Instant firstInstant(Instant... values) {
        for (Instant value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
