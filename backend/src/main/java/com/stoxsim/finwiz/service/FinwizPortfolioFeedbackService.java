package com.stoxsim.finwiz.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.stoxsim.finwiz.api.FinwizPortfolioFeedbackResponse;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.portfolio.api.PortfolioAnalyticsResponse;
import com.stoxsim.portfolio.service.PortfolioAnalyticsService;

@Service
public class FinwizPortfolioFeedbackService {

    public static final String FEEDBACK_VERSION = "finwiz-post-trade-v1";
    public static final String DISCLAIMER = "Educational portfolio feedback only. It describes paper-portfolio structure and does not recommend buying, selling or holding any security.";

    private static final Logger LOGGER = LoggerFactory.getLogger(FinwizPortfolioFeedbackService.class);

    private final PortfolioAnalyticsService analytics;
    private final MeterRegistry meterRegistry;

    public FinwizPortfolioFeedbackService(
        PortfolioAnalyticsService analytics,
        MeterRegistry meterRegistry
    ) {
        this.analytics = analytics;
        this.meterRegistry = meterRegistry;
    }

    public PortfolioAnalyticsResponse snapshot(UUID userId, MarketRegion marketRegion) {
        try {
            return analytics.analyze(userId, marketRegion);
        } catch (RuntimeException exception) {
            meterRegistry.counter(
                "stoxsim.finwiz.portfolio_feedback",
                "result",
                "snapshot_unavailable"
            ).increment();
            LOGGER.warn(
                "Portfolio feedback snapshot unavailable for market {}: {}",
                marketRegion,
                exception.getMessage()
            );
            return null;
        }
    }

    public FinwizPortfolioFeedbackResponse afterExecution(
        UUID userId,
        UUID orderId,
        MarketRegion marketRegion,
        PortfolioAnalyticsResponse before
    ) {
        try {
            return buildAfterExecution(userId, orderId, marketRegion, before);
        } catch (RuntimeException exception) {
            meterRegistry.counter(
                "stoxsim.finwiz.portfolio_feedback",
                "result",
                "generation_failed"
            ).increment();
            LOGGER.warn(
                "Post-trade portfolio feedback unavailable for market {}: {}",
                marketRegion,
                exception.getMessage()
            );
            return null;
        }
    }

    private FinwizPortfolioFeedbackResponse buildAfterExecution(
        UUID userId,
        UUID orderId,
        MarketRegion marketRegion,
        PortfolioAnalyticsResponse before
    ) {
        PortfolioAnalyticsResponse after = snapshot(userId, marketRegion);
        if (after == null) {
            return null;
        }

        Integer beforeScore = before == null ? null : before.stoxScore();
        Integer afterScore = after.stoxScore();
        Integer change = beforeScore == null || afterScore == null
            ? null
            : afterScore - beforeScore;

        List<String> observations = observations(before, after, change);
        FinwizPortfolioFeedbackResponse response = new FinwizPortfolioFeedbackResponse(
            orderId,
            marketRegion,
            FEEDBACK_VERSION,
            after.formulaVersion(),
            after.status(),
            beforeScore,
            afterScore,
            change,
            headline(beforeScore, afterScore, change),
            observations,
            List.of(
                "How can position size affect diversification?",
                "Why can a portfolio have many holdings and still be concentrated?",
                "What does StoxScore leave out of a risk assessment?"
            ),
            after.confidence(),
            Instant.now(),
            DISCLAIMER
        );

        meterRegistry.counter(
            "stoxsim.finwiz.portfolio_feedback",
            "result",
            "generated",
            "market",
            marketRegion.name(),
            "status",
            after.status()
        ).increment();
        return response;
    }

    private String headline(Integer before, Integer after, Integer change) {
        if (after == null) {
            return "This trade left the portfolio without enough invested positions for StoxScore.";
        }
        if (before == null) {
            return "This trade established your first portfolio-structure baseline.";
        }
        if (change > 0) {
            return "Measured portfolio structure increased under StoxScore v1.";
        }
        if (change < 0) {
            return "Measured portfolio structure decreased under StoxScore v1.";
        }
        return "Measured portfolio structure was broadly unchanged.";
    }

    private List<String> observations(
        PortfolioAnalyticsResponse before,
        PortfolioAnalyticsResponse after,
        Integer change
    ) {
        List<String> result = new ArrayList<>();
        if (before == null) {
            result.add("A before-trade structure snapshot was unavailable, so no score change is claimed.");
        } else if (before.stoxScore() == null && after.stoxScore() != null) {
            result.add("StoxScore can now be calculated from the first positive-value holding.");
        } else if (before.stoxScore() != null && after.stoxScore() == null) {
            result.add("The portfolio no longer has enough invested value for a structure score.");
        } else if (change != null) {
            result.add("StoxScore moved from " + before.stoxScore() + " to " + after.stoxScore()
                + " (" + signed(change) + "). This measures structure, not expected return.");
        }

        if (before != null) {
            result.add("Positive-value holdings changed from " + before.holdingCount()
                + " to " + after.holdingCount() + ".");
            result.add("Effective holdings changed from " + decimal(before.effectiveHoldings())
                + " to " + decimal(after.effectiveHoldings()) + ".");
            result.add("Largest-position weight changed from "
                + percent(before.largestPositionWeightPercent()) + " to "
                + percent(after.largestPositionWeightPercent()) + ".");
        } else {
            result.add("The portfolio now has " + after.holdingCount() + " positive-value holding"
                + (after.holdingCount() == 1 ? "." : "s."));
            result.add("The largest position represents "
                + percent(after.largestPositionWeightPercent()) + " of invested market value.");
        }

        if (!"HIGH".equals(after.confidence())) {
            result.add("Confidence is " + after.confidence().toLowerCase()
                + " because pricing coverage or freshness is limited.");
        }
        return List.copyOf(result);
    }

    private String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private String decimal(BigDecimal value) {
        return value == null ? "unavailable" : value.stripTrailingZeros().toPlainString();
    }

    private String percent(BigDecimal value) {
        return value == null ? "unavailable" : decimal(value) + "%";
    }
}
