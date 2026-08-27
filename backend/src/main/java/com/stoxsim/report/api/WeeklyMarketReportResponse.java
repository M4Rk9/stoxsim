package com.stoxsim.report.api;

import java.math.BigDecimal;

import com.stoxsim.market.domain.MarketRegion;

public record WeeklyMarketReportResponse(
    MarketRegion marketRegion,
    String currency,
    BigDecimal accountValue,
    BigDecimal accountValueChange,
    BigDecimal totalProfitLoss,
    BigDecimal totalProfitLossChange,
    BigDecimal totalReturnPercent,
    BigDecimal realizedProfitLoss,
    BigDecimal unrealizedProfitLoss,
    BigDecimal cashWeightPercent,
    BigDecimal investedWeightPercent,
    long tradesExecuted,
    String largestAllocationSymbol,
    BigDecimal largestAllocationWeightPercent,
    String largestContributionSymbol,
    BigDecimal largestContribution,
    String status,
    String confidence,
    BigDecimal dataCoveragePercent
) {
}
