package com.stoxsim.portfolio.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PortfolioPositionResponse(
    UUID holdingId,
    String exchange,
    String symbol,
    String name,
    String currency,
    long quantity,
    long blockedQuantity,
    long availableQuantity,
    BigDecimal averagePrice,
    BigDecimal currentPrice,
    BigDecimal investedValue,
    BigDecimal marketValue,
    BigDecimal unrealizedProfitLoss,
    BigDecimal returnPercent,
    PricingStatus pricingStatus,
    Instant priceTimestamp
) {
    public enum PricingStatus {
        LIVE,
        CLOSED,
        STALE,
        UNAVAILABLE
    }
}
