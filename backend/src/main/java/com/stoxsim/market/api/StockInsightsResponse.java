package com.stoxsim.market.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StockInsightsResponse(
    String provider,
    String symbol,
    String isin,
    Instant asOf,
    FundamentalsStatus status,
    CompanyProfile profile,
    List<FundamentalRatio> ratios,
    FinancialPerformance financials,
    String message
) {
    public enum FundamentalsStatus {
        AVAILABLE,
        PARTIAL,
        UNAVAILABLE
    }

    public record CompanyProfile(
        String description,
        String sector,
        BigDecimal sectorMarketCapInrCrore,
        String sectorMarketCapInrFormatted
    ) {
    }

    public record FundamentalRatio(
        String name,
        String companyValue,
        String sectorValue
    ) {
    }

    public record FinancialPerformance(
        String statementType,
        String timePeriod,
        String unitsIn,
        List<FinancialMetric> metrics
    ) {
    }

    public record FinancialMetric(
        String category,
        List<FinancialHistoryPoint> history
    ) {
    }

    public record FinancialHistoryPoint(
        String period,
        BigDecimal value,
        String change
    ) {
    }
}
