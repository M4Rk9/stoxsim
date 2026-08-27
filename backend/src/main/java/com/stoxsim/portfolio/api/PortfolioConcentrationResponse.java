package com.stoxsim.portfolio.api;

import java.math.BigDecimal;

public record PortfolioConcentrationResponse(
    String symbol,
    BigDecimal weightPercent
) {
}
