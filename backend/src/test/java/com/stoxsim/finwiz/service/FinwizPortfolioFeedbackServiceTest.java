package com.stoxsim.finwiz.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.portfolio.api.PortfolioAnalyticsResponse;
import com.stoxsim.portfolio.service.PortfolioAnalyticsService;

@ExtendWith(MockitoExtension.class)
class FinwizPortfolioFeedbackServiceTest {

    @Mock
    private PortfolioAnalyticsService analytics;

    @Test
    void explainsAFirstExecutedTradeWithoutClaimingAChangeFromMissingData() {
        UUID userId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(analytics.analyze(userId, MarketRegion.INDIA))
            .thenReturn(scored(5, 1, "1.00", "100.00", "HIGH"));

        var response = service().afterExecution(
            userId,
            orderId,
            MarketRegion.INDIA,
            unscored()
        );

        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.scoreBefore()).isNull();
        assertThat(response.scoreAfter()).isEqualTo(5);
        assertThat(response.scoreChange()).isNull();
        assertThat(response.headline()).contains("first portfolio-structure baseline");
        assertThat(response.observations()).anyMatch(value -> value.contains("first positive-value holding"));
        assertThat(response.disclaimer()).contains("does not recommend buying, selling or holding");
    }

    @Test
    void reportsVersionedStructuralChangesWithoutReturnOrTradingClaims() {
        UUID userId = UUID.randomUUID();
        when(analytics.analyze(userId, MarketRegion.INDIA))
            .thenReturn(scored(62, 4, "3.40", "38.00", "HIGH"));

        var response = service().afterExecution(
            userId,
            UUID.randomUUID(),
            MarketRegion.INDIA,
            scored(48, 3, "2.10", "55.00", "HIGH")
        );

        assertThat(response.feedbackVersion()).isEqualTo("finwiz-post-trade-v1");
        assertThat(response.formulaVersion()).isEqualTo("stoxscore-portfolio-v1");
        assertThat(response.scoreChange()).isEqualTo(14);
        assertThat(response.observations()).contains(
            "StoxScore moved from 48 to 62 (+14). This measures structure, not expected return.",
            "Positive-value holdings changed from 3 to 4.",
            "Effective holdings changed from 2.1 to 3.4.",
            "Largest-position weight changed from 55% to 38%."
        );
        assertThat(response.headline()).doesNotContainIgnoringCase("buy", "sell", "return");
    }

    @Test
    void lowersTheFeedbackClaimWhenPricingConfidenceIsLimited() {
        UUID userId = UUID.randomUUID();
        when(analytics.analyze(userId, MarketRegion.UNITED_STATES))
            .thenReturn(scored(50, 3, "2.00", "60.00", "LOW"));

        var response = service().afterExecution(
            userId,
            UUID.randomUUID(),
            MarketRegion.UNITED_STATES,
            null
        );

        assertThat(response.confidence()).isEqualTo("LOW");
        assertThat(response.observations()).contains(
            "A before-trade structure snapshot was unavailable, so no score change is claimed.",
            "Confidence is low because pricing coverage or freshness is limited."
        );
    }

    @Test
    void returnsNoFeedbackWhenTheAfterSnapshotIsUnavailable() {
        UUID userId = UUID.randomUUID();
        when(analytics.analyze(userId, MarketRegion.INDIA))
            .thenThrow(new IllegalStateException("temporary valuation failure"));

        assertThat(service().afterExecution(
            userId,
            UUID.randomUUID(),
            MarketRegion.INDIA,
            unscored()
        )).isNull();
    }

    private FinwizPortfolioFeedbackService service() {
        return new FinwizPortfolioFeedbackService(analytics, new SimpleMeterRegistry());
    }

    private PortfolioAnalyticsResponse unscored() {
        return new PortfolioAnalyticsResponse(
            MarketRegion.INDIA,
            "stoxscore-portfolio-v1",
            "NOT_ENOUGH_DATA",
            null,
            "Not scored yet",
            "NONE",
            new BigDecimal("0.00"),
            0,
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            new BigDecimal("0.0000"),
            List.of(),
            List.of(),
            List.of(),
            "Educational only",
            Instant.parse("2026-08-27T00:00:00Z")
        );
    }

    private PortfolioAnalyticsResponse scored(
        int score,
        int holdings,
        String effectiveHoldings,
        String largestWeight,
        String confidence
    ) {
        return new PortfolioAnalyticsResponse(
            MarketRegion.INDIA,
            "stoxscore-portfolio-v1",
            "LOW".equals(confidence) ? "LIMITED_DATA" : "AVAILABLE",
            score,
            "Developing",
            confidence,
            new BigDecimal("100.00"),
            holdings,
            new BigDecimal(effectiveHoldings),
            new BigDecimal(largestWeight),
            new BigDecimal("80.00"),
            new BigDecimal("0.2500"),
            List.of(),
            List.of(),
            List.of(),
            "Educational only",
            Instant.parse("2026-08-27T00:00:00Z")
        );
    }
}
