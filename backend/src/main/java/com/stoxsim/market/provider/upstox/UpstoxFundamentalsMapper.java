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
        JsonNode marketCap = field(data, "sector_market_cap_inr", "sectorMarketCapInr");
        String description = text(data, "company_profile", "companyProfile");
        String sector = text(data, "sector");
        if (description == null && sector == null) {
            return null;
        }
        return new CompanyProfile(
            description,
            sector,
            decimal(field(marketCap, "value")),
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
                    text(ratio, "company_value", "companyValue"),
                    text(ratio, "sector_value", "sectorValue")
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
        JsonNode statements = field(data, "income_statement", "incomeStatement");
        if (statements.isArray()) {
            for (JsonNode statement : statements) {
                String category = text(statement, "category");
                if (category == null) {
                    continue;
                }
                List<FinancialHistoryPoint> history = new ArrayList<>();
                JsonNode entries = field(statement, "history");
                if (entries.isArray()) {
                    for (JsonNode entry : entries) {
                        String period = text(entry, "period");
                        BigDecimal value = decimal(field(entry, "value"));
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
            text(data, "time_period", "timePeriod"),
            text(data, "units_in", "unitsIn"),
            List.copyOf(metrics)
        );
    }

    private static JsonNode field(JsonNode node, String... names) {
        if (missing(node)) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (!missing(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean missing(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    private static String text(JsonNode node, String... fields) {
        JsonNode value = field(node, fields);
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
