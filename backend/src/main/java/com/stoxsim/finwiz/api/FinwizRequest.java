package com.stoxsim.finwiz.api;

import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.market.domain.MarketRegion;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FinwizRequest(
    @NotBlank
    @Size(max = 2000)
    String question,
    @NotNull
    Topic topic,
    ExperienceLevel experienceLevel,
    MarketRegion marketRegion,
    MarketExchange exchange,
    @Size(max = 32)
    String symbol
) {
    public enum Topic {
        LEARN,
        STOCK_FUNDAMENTALS,
        TECHNICAL_ANALYSIS,
        FUNDAMENTAL_ANALYSIS,
        VALUATION,
        CASH_FLOW,
        MARKET_EVALUATION,
        PORTFOLIO_EDUCATION
    }

    public enum ExperienceLevel {
        BEGINNER,
        INTERMEDIATE
    }

    public ExperienceLevel resolvedExperienceLevel() {
        return experienceLevel == null ? ExperienceLevel.BEGINNER : experienceLevel;
    }

    public boolean requestsStockContext() {
        return symbol != null && !symbol.isBlank();
    }
}
