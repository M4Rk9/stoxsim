package com.stoxsim.portfolio.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.stoxsim.market.domain.MarketRegion;

public record PortfolioInsightsResponse(
    MarketRegion marketRegion,
    String currency,
    String formulaVersion,
    String status,
    String confidence,
    BigDecimal dataCoveragePercent,
    BigDecimal cashValue,
    BigDecimal cashWeightPercent,
    BigDecimal investedWeightPercent,
    BigDecimal realizedProfitLoss,
    BigDecimal unrealizedProfitLoss,
    BigDecimal totalProfitLoss,
    List<PortfolioAllocationResponse> allocations,
    List<PortfolioAttributionResponse> attributions,
    List<String> observations,
    String disclaimer,
    Instant valuedAt
) {
}
