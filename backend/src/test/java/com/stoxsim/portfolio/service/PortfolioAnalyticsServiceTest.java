package com.stoxsim.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.portfolio.api.PortfolioPositionResponse;
import com.stoxsim.portfolio.api.PortfolioPositionResponse.PricingStatus;
import com.stoxsim.portfolio.api.PortfolioResponse;

@ExtendWith(MockitoExtension.class)
class PortfolioAnalyticsServiceTest {

    @Mock
    private PortfolioValuationService valuation;

    @Test
    void leavesAnEmptyPortfolioUnscored() {
        var analytics = service().analyze(portfolio(List.of(), PricingStatus.CLOSED));

        assertThat(analytics.status()).isEqualTo("NOT_ENOUGH_DATA");
        assertThat(analytics.stoxScore()).isNull();
        assertThat(analytics.structureBand()).isEqualTo("Not scored yet");
    }

    @Test
    void identifiesASingleHoldingAsConcentrated() {
        var analytics = service().analyze(portfolio(
            List.of(position("ONLY", "1000", PricingStatus.LIVE)),
            PricingStatus.LIVE
        ));

        assertThat(analytics.stoxScore()).isEqualTo(5);
        assertThat(analytics.structureBand()).isEqualTo("Concentrated");
        assertThat(analytics.largestPositionWeightPercent()).isEqualByComparingTo("100.00");
        assertThat(analytics.effectiveHoldings()).isEqualByComparingTo("1.00");
        assertThat(analytics.formulaVersion()).isEqualTo("stoxscore-portfolio-v1");
    }

    @Test
    void givesEightEqualHoldingsTheMaximumStructureScore() {
        List<PortfolioPositionResponse> holdings = IntStream.rangeClosed(1, 8)
            .mapToObj(index -> position("EQ" + index, "1000", PricingStatus.CLOSED))
            .toList();

        var analytics = service().analyze(portfolio(holdings, PricingStatus.CLOSED));

        assertThat(analytics.stoxScore()).isEqualTo(100);
        assertThat(analytics.structureBand()).isEqualTo("Broadly diversified");
        assertThat(analytics.topThreeWeightPercent()).isEqualByComparingTo("37.50");
        assertThat(analytics.confidence()).isEqualTo("HIGH");
        assertThat(analytics.components()).hasSize(3);
    }

    @Test
    void reducesConfidenceWhenUnavailablePricesAreMaterial() {
        var analytics = service().analyze(portfolio(
            List.of(
                position("LIVE", "750", PricingStatus.LIVE),
                position("MISSING", "250", PricingStatus.UNAVAILABLE)
            ),
            PricingStatus.UNAVAILABLE
        ));

        assertThat(analytics.status()).isEqualTo("LIMITED_DATA");
        assertThat(analytics.confidence()).isEqualTo("LOW");
        assertThat(analytics.dataCoveragePercent()).isEqualByComparingTo("75.00");
        assertThat(analytics.observations()).anyMatch(message -> message.contains("75%"));
    }

    @Test
    void marksAnUnavailableAggregateValuationAsLimitedEvenWithFullPositionCoverage() {
        var analytics = service().analyze(portfolio(
            List.of(position("FALLBACK", "1000", PricingStatus.LIVE)),
            PricingStatus.UNAVAILABLE
        ));

        assertThat(analytics.status()).isEqualTo("LIMITED_DATA");
        assertThat(analytics.confidence()).isEqualTo("LOW");
        assertThat(analytics.dataCoveragePercent()).isEqualByComparingTo("100.00");
    }

    private PortfolioAnalyticsService service() {
        return new PortfolioAnalyticsService(valuation);
    }

    private PortfolioResponse portfolio(
        List<PortfolioPositionResponse> holdings,
        PricingStatus status
    ) {
        BigDecimal marketValue = holdings.stream()
            .map(PortfolioPositionResponse::marketValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PortfolioResponse(
            MarketRegion.INDIA,
            "INR",
            new BigDecimal("500000"),
            new BigDecimal("500000").subtract(marketValue),
            BigDecimal.ZERO,
            marketValue,
            marketValue,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("500000"),
            BigDecimal.ZERO,
            status,
            Instant.parse("2026-08-26T12:00:00Z"),
            holdings
        );
    }

    private PortfolioPositionResponse position(
        String symbol,
        String marketValue,
        PricingStatus status
    ) {
        BigDecimal value = new BigDecimal(marketValue);
        return new PortfolioPositionResponse(
            UUID.randomUUID(),
            "NSE",
            symbol,
            symbol + " Limited",
            "INR",
            1,
            0,
            1,
            value,
            value,
            value,
            value,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            status,
            Instant.parse("2026-08-26T12:00:00Z")
        );
    }
}
