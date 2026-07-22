package com.stoxsim.instrument.provider.upstox;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.service.InstrumentSnapshot;
import com.stoxsim.market.domain.MarketRegion;

@Component
public class UpstoxInstrumentMapper {

    public static final String PROVIDER = "UPSTOX";
    private static final Set<String> SUPPORTED_SEGMENTS = Set.of(
        "NSE_EQ",
        "BSE_EQ",
        "NSE_INDEX",
        "BSE_INDEX"
    );

    public Optional<InstrumentSnapshot> map(JsonNode node) {
        String segment = text(node, "segment");
        if (!SUPPORTED_SEGMENTS.contains(segment)) {
            return Optional.empty();
        }

        String instrumentKey = text(node, "instrument_key");
        String name = text(node, "name");
        String tradingSymbol = optionalText(node, "trading_symbol").orElse(name);
        if (instrumentKey.isBlank() || tradingSymbol.isBlank() || name.isBlank()) {
            return Optional.empty();
        }

        MarketExchange exchange = segment.startsWith("NSE")
            ? MarketExchange.NSE
            : MarketExchange.BSE;
        String securityType = optionalText(node, "security_type").orElse(null);

        return Optional.of(new InstrumentSnapshot(
            PROVIDER,
            instrumentKey,
            MarketRegion.INDIA,
            exchange,
            segment,
            tradingSymbol,
            name,
            optionalText(node, "isin").orElse(null),
            instrumentType(segment, securityType),
            "INR",
            positiveInt(node, "lot_size", 1),
            decimal(node, "tick_size", new BigDecimal("0.05")),
            securityType
        ));
    }

    private InstrumentType instrumentType(String segment, String securityType) {
        if (segment.endsWith("_INDEX")) {
            return InstrumentType.INDEX;
        }
        if (securityType != null && securityType.toUpperCase().contains("ETF")) {
            return InstrumentType.ETF;
        }
        return InstrumentType.EQUITY;
    }

    private String text(JsonNode node, String field) {
        return optionalText(node, field).orElse("");
    }

    private Optional<String> optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return Optional.empty();
        }
        String text = value.asText().trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private int positiveInt(JsonNode node, String field, int fallback) {
        int value = node.path(field).asInt(fallback);
        return value > 0 ? value : fallback;
    }

    private BigDecimal decimal(JsonNode node, String field, BigDecimal fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return fallback;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
