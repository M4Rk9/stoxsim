package com.stoxsim.portfolio.api;

import java.math.BigDecimal;

public record PortfolioAllocationResponse(
    String exchange,
    String symbol,
    String name,
    BigDecimal marketValue,
    BigDecimal investedWeightPercent,
    BigDecimal accountWeightPercent,
    BigDecimal unrealizedProfitLoss,
    BigDecimal returnPercent,
    PortfolioPositionResponse.PricingStatus pricingStatus
) {
}
