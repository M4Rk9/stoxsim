package com.stoxsim.portfolio.api;

import java.math.BigDecimal;
import java.util.UUID;

import com.stoxsim.portfolio.domain.Holding;

public record HoldingResponse(
    UUID id,
    String marketRegion,
    String exchange,
    String symbol,
    String currency,
    long quantity,
    long blockedQuantity,
    long availableQuantity,
    BigDecimal averagePrice
) {
    public static HoldingResponse from(Holding holding) {
        return new HoldingResponse(
            holding.getId(),
            holding.getInstrument().getMarketRegion().name(),
            holding.getInstrument().getExchange().name(),
            holding.getInstrument().getTradingSymbol(),
            holding.getInstrument().getCurrency(),
            holding.getQuantity(),
            holding.getBlockedQuantity(),
            holding.availableQuantity(),
            holding.getAveragePrice()
        );
    }
}
