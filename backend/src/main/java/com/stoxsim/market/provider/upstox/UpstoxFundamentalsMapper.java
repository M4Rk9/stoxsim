package com.stoxsim.market.provider.upstox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.JsonNode;

import com.stoxsim.market.api.StockInsightsResponse.CompanyProfile;
import com.stoxsim.market.api.StockInsightsResponse.FinancialHistoryPoint;
import com.stoxsim.market.api.StockInsightsResponse.FinancialMetric;
import com.stoxsim.market.api.StockInsightsResponse.FinancialPerformance;
import com.stoxsim.market.api.StockInsightsResponse.FundamentalRatio;

public final class UpstoxFundamentalsMapper {

    private UpstoxFundamentalsMapper() {
    }

    public static CompanyProfile profile(JsonNode data) {
        if (missing(data)) {
            return null;
        }
        JsonNode marketCap = data.path("sector_market_cap_inr");
        String description = text(data, "company_profile");
        String sector = text(data, "sector");
        if (description == null && sector == null) {
            return null;
        }
        return new CompanyProfile(
            description,
            sector,
            decimal(marketCap.get("value")),
            text(marketCap, "formatted")
        );
    }

    public static List<FundamentalRatio> ratios(JsonNode data) {
        if (missing(data) || !data.isArray()) {
            return List.of();
        }
        List<FundamentalRatio> ratios = new ArrayList<>();
        for (JsonNode ratio : data) {
            String name = text(ratio, "name");
            if (name != null) {
                ratios.add(new FundamentalRatio(
                    name,
                    text(ratio, "company_value"),
                    text(ratio, "sector_value")
                ));
            }
        }
        return List.copyOf(ratios);
    }

    public static FinancialPerformance financials(JsonNode data) {
        if (missing(data)) {
            return null;
        }
        List<FinancialMetric> metrics = new ArrayList<>();
        JsonNode statements = data.path("income_statement");
        if (statements.isArray()) {
            for (JsonNode statement : statements) {
                String category = text(statement, "category");
                if (category == null) {
                    continue;
                }
                List<FinancialHistoryPoint> history = new ArrayList<>();
                JsonNode entries = statement.path("history");
                if (entries.isArray()) {
                    for (JsonNode entry : entries) {
                        String period = text(entry, "period");
                        BigDecimal value = decimal(entry.get("value"));
                        if (period != null && value != null) {
                            history.add(new FinancialHistoryPoint(
                                period,
                                value,
                                text(entry, "change")
                            ));
                        }
                    }
                }
                metrics.add(new FinancialMetric(category, List.copyOf(history)));
            }
        }
        if (metrics.isEmpty()) {
            return null;
        }
        return new FinancialPerformance(
            text(data, "type"),
            text(data, "time_period"),
            text(data, "units_in"),
            List.copyOf(metrics)
        );
    }

    private static boolean missing(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    private static String text(JsonNode node, String field) {
        if (missing(node)) {
            return null;
        }
        JsonNode value = node.get(field);
        if (missing(value)) {
            return null;
        }
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static BigDecimal decimal(JsonNode value) {
        if (missing(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
