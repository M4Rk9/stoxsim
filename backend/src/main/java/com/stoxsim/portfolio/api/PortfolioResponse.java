package com.stoxsim.portfolio.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.stoxsim.market.domain.MarketRegion;

public record PortfolioResponse(
    MarketRegion marketRegion,
    String currency,
    BigDecimal startingCapital,
    BigDecimal availableCash,
    BigDecimal blockedCash,
    BigDecimal investedValue,
    BigDecimal marketValue,
    BigDecimal realizedProfitLoss,
    BigDecimal unrealizedProfitLoss,
    BigDecimal totalProfitLoss,
    BigDecimal totalAccountValue,
    BigDecimal totalReturnPercent,
    PortfolioPositionResponse.PricingStatus dataStatus,
    Instant valuedAt,
    List<PortfolioPositionResponse> holdings
) {
}
