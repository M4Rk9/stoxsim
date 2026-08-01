package com.stoxsim.instrument.provider.alpaca;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.service.InstrumentSnapshot;
import com.stoxsim.market.domain.MarketRegion;

import tools.jackson.databind.JsonNode;

@Component
public class AlpacaInstrumentMapper {

    public static final String PROVIDER = "ALPACA";

    public Optional<InstrumentSnapshot> map(JsonNode node) {
        if (node == null
            || !"active".equalsIgnoreCase(text(node, "status"))
            || !"us_equity".equalsIgnoreCase(text(node, "class"))
            || !node.path("tradable").asBoolean(false)) {
            return Optional.empty();
        }

        String symbol = text(node, "symbol");
        String name = text(node, "name");
        MarketExchange exchange = exchange(text(node, "exchange"));
        if (symbol == null || exchange == null) {
            return Optional.empty();
        }
        if (name == null || name.isBlank()) {
            name = symbol;
        }

        return Optional.of(new InstrumentSnapshot(
            PROVIDER,
            symbol,
            MarketRegion.UNITED_STATES,
            exchange,
            "US_EQUITY",
            symbol,
            name,
            null,
            instrumentType(name),
            "USD",
            1,
            new BigDecimal("0.0001"),
            text(node, "id")
        ));
    }

    private MarketExchange exchange(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "NASDAQ" -> MarketExchange.NASDAQ;
            case "NYSE" -> MarketExchange.NYSE;
            case "ARCA", "NYSEARCA" -> MarketExchange.NYSE_ARCA;
            case "AMEX" -> MarketExchange.AMEX;
            case "BATS" -> MarketExchange.CBOE;
            default -> null;
        };
    }

    private InstrumentType instrumentType(String name) {
        String normalized = name.toUpperCase(Locale.ROOT);
        return normalized.contains(" ETF")
            || normalized.contains(" FUND")
            || normalized.contains("ISHARES")
            || normalized.contains("SPDR")
            || normalized.contains("VANGUARD")
            ? InstrumentType.ETF
            : InstrumentType.EQUITY;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String text = value.textValue();
        return text == null || text.isBlank() ? null : text.trim();
    }
}
