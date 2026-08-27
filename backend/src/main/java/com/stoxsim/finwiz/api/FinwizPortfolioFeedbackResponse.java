package com.stoxsim.finwiz.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.stoxsim.market.domain.MarketRegion;

public record FinwizPortfolioFeedbackResponse(
    UUID orderId,
    MarketRegion marketRegion,
    String feedbackVersion,
    String formulaVersion,
    String status,
    Integer scoreBefore,
    Integer scoreAfter,
    Integer scoreChange,
    String headline,
    List<String> observations,
    List<String> suggestedQuestions,
    String confidence,
    Instant generatedAt,
    String disclaimer
) {
}
