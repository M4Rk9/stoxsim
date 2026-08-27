package com.stoxsim.portfolio.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.stoxsim.market.domain.MarketRegion;

public record PortfolioAnalyticsResponse(
    MarketRegion marketRegion,
    String formulaVersion,
    String status,
    Integer stoxScore,
    String structureBand,
    String confidence,
    BigDecimal dataCoveragePercent,
    int holdingCount,
    BigDecimal effectiveHoldings,
    BigDecimal largestPositionWeightPercent,
    BigDecimal topThreeWeightPercent,
    BigDecimal concentrationIndex,
    List<StoxScoreComponentResponse> components,
    List<PortfolioConcentrationResponse> largestPositions,
    List<String> observations,
    String disclaimer,
    Instant valuedAt
) {
}
