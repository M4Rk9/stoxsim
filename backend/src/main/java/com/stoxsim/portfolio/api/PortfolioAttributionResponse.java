package com.stoxsim.portfolio.api;

import java.math.BigDecimal;

public record PortfolioAttributionResponse(
    String exchange,
    String symbol,
    String name,
    BigDecimal realizedProfitLoss,
    BigDecimal unrealizedProfitLoss,
    BigDecimal totalContribution,
    BigDecimal accountImpactPercent,
    String contributionType
) {
}
