package com.stoxsim.market.provider.alpaca;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

@Component
public class AlpacaMarketMoverMapper {

    public List<MoverSeed> map(JsonNode root, String collectionName) {
        JsonNode collection = root == null ? null : root.get(collectionName);
        if (collection == null || !collection.isArray()) {
            return List.of();
        }
        List<MoverSeed> result = new ArrayList<>();
        for (JsonNode node : collection) {
            String symbol = text(node, "symbol");
            BigDecimal price = decimal(node, "price", "last_price");
            BigDecimal change = decimal(node, "change");
            BigDecimal percent = decimal(
                node,
                "percent_change",
                "change_percent",
                "percentage_change"
            );
            if (symbol == null || price == null || percent == null) {
                continue;
            }
            result.add(new MoverSeed(
                symbol.toUpperCase(Locale.ROOT),
                price,
                change,
                percent
            ));
        }
        return List.copyOf(result);
    }

    private BigDecimal decimal(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            try {
                return new BigDecimal(value.asText());
            } catch (NumberFormatException ignored) {
                // Try the next documented/legacy field name.
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    public record MoverSeed(
        String symbol,
        BigDecimal price,
        BigDecimal change,
        BigDecimal percentChange
    ) {
    }
}
