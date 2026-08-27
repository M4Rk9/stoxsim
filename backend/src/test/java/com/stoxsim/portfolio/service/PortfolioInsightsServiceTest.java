package com.stoxsim.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.service.TradingQueryService;
import com.stoxsim.portfolio.api.PortfolioPositionResponse;
import com.stoxsim.portfolio.api.PortfolioPositionResponse.PricingStatus;
import com.stoxsim.portfolio.api.PortfolioResponse;
import com.stoxsim.trade.api.TradeResponse;

@ExtendWith(MockitoExtension.class)
class PortfolioInsightsServiceTest {

    private static final Instant VALUED_AT = Instant.parse("2026-08-27T05:00:00Z");

    @Mock private PortfolioValuationService valuation;
    @Mock private TradingQueryService tradingQueries;

    @Test
    void returnsAnEmptyEducationalStateForANewAccount() {
        var insights = service().analyze(portfolio(
            "500000",
            "500000",
            "0",
            "0",
            "0",
            List.of(),
            PricingStatus.CLOSED
        ), List.of());

        assertThat(insights.status()).isEqualTo("NOT_ENOUGH_DATA");
        assertThat(insights.confidence()).isEqualTo("NONE");
        assertThat(insights.cashWeightPercent()).isEqualByComparingTo("100.00");
        assertThat(insights.allocations()).isEmpty();
        assertThat(insights.attributions()).isEmpty();
    }

    @Test
    void explainsCashAndInvestedAllocationWithoutExternalMetadata() {
        var insights = service().analyze(portfolio(
            "2000",
            "1000",
            "0",
            "0",
            "0",
            List.of(
                position("LARGE", "Large Limited", "600", "30", PricingStatus.LIVE),
                position("SMALL", "Small Limited", "400", "-10", PricingStatus.LIVE)
            ),
            PricingStatus.LIVE
        ), List.of());

        assertThat(insights.formulaVersion()).isEqualTo("portfolio-insights-v1");
        assertThat(insights.cashWeightPercent()).isEqualByComparingTo("50.00");
        assertThat(insights.investedWeightPercent()).isEqualByComparingTo("50.00");
        assertThat(insights.allocations()).extracting(allocation -> allocation.symbol())
            .containsExactly("LARGE", "SMALL");
        assertThat(insights.allocations().getFirst().investedWeightPercent())
            .isEqualByComparingTo("60.00");
        assertThat(insights.dataCoveragePercent()).isEqualByComparingTo("100.00");
    }

    @Test
    void replaysFeeAdjustedTradesAndReconcilesPerformanceBySymbol() {
        List<TradeResponse> trades = List.of(
            trade("A", OrderSide.SELL, 4, "480", "5", "475", "2026-08-27T04:00:00Z"),
            trade("A", OrderSide.BUY, 10, "1000", "10", "1010", "2026-08-26T04:00:00Z")
        );
        var insights = service().analyze(portfolio(
            "2000",
            "1340",
            "71",
            "54",
            "125",
            List.of(position("A", "Alpha Limited", "660", "54", PricingStatus.LIVE)),
            PricingStatus.LIVE
        ), trades);

        assertThat(insights.attributions()).hasSize(1);
        var attribution = insights.attributions().getFirst();
        assertThat(attribution.symbol()).isEqualTo("A");
        assertThat(attribution.realizedProfitLoss()).isEqualByComparingTo("71.0000");
        assertThat(attribution.unrealizedProfitLoss()).isEqualByComparingTo("54.0000");
        assertThat(attribution.totalContribution()).isEqualByComparingTo("125.0000");
        assertThat(attribution.accountImpactPercent()).isEqualByComparingTo("6.25");
        assertThat(attribution.contributionType()).isEqualTo("GAIN");
    }

    @Test
    void keepsClosedPositionsInRealizedAttribution() {
        List<TradeResponse> trades = List.of(
            trade("CLOSED", OrderSide.BUY, 1, "100", "1", "101", "2026-08-25T04:00:00Z"),
            trade("CLOSED", OrderSide.SELL, 1, "200", "9", "191", "2026-08-26T04:00:00Z")
        );
        var insights = service().analyze(portfolio(
            "1000",
            "1090",
            "90",
            "0",
            "90",
            List.of(),
            PricingStatus.CLOSED
        ), trades);

        assertThat(insights.status()).isEqualTo("AVAILABLE");
        assertThat(insights.allocations()).isEmpty();
        assertThat(insights.attributions()).singleElement().satisfies(attribution -> {
            assertThat(attribution.symbol()).isEqualTo("CLOSED");
            assertThat(attribution.realizedProfitLoss()).isEqualByComparingTo("90.0000");
            assertThat(attribution.unrealizedProfitLoss()).isZero();
        });
    }

    @Test
    void reportsLimitedConfidenceWhenUnavailablePricesAreMaterial() {
        var insights = service().analyze(portfolio(
            "2000",
            "1000",
            "0",
            "0",
            "0",
            List.of(
                position("LIVE", "Live Limited", "750", "0", PricingStatus.LIVE),
                position("FALLBACK", "Fallback Limited", "250", "0", PricingStatus.UNAVAILABLE)
            ),
            PricingStatus.UNAVAILABLE
        ), List.of());

        assertThat(insights.status()).isEqualTo("LIMITED_DATA");
        assertThat(insights.confidence()).isEqualTo("LOW");
        assertThat(insights.dataCoveragePercent()).isEqualByComparingTo("75.00");
        assertThat(insights.observations()).anyMatch(observation -> observation.contains("75%"));
    }

    private PortfolioInsightsService service() {
        return new PortfolioInsightsService(valuation, tradingQueries);
    }

    private PortfolioResponse portfolio(
        String startingCapital,
        String cash,
        String realized,
        String unrealized,
        String totalProfitLoss,
        List<PortfolioPositionResponse> holdings,
        PricingStatus status
    ) {
        BigDecimal marketValue = holdings.stream()
            .map(PortfolioPositionResponse::marketValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal accountValue = new BigDecimal(cash).add(marketValue);
        return new PortfolioResponse(
            MarketRegion.INDIA,
            "INR",
            new BigDecimal(startingCapital),
            new BigDecimal(cash),
            BigDecimal.ZERO,
            marketValue,
            marketValue,
            new BigDecimal(realized),
            new BigDecimal(unrealized),
            new BigDecimal(totalProfitLoss),
            accountValue,
            BigDecimal.ZERO,
            status,
            VALUED_AT,
            holdings
        );
    }

    private PortfolioPositionResponse position(
        String symbol,
        String name,
        String marketValue,
        String unrealized,
        PricingStatus status
    ) {
        BigDecimal value = new BigDecimal(marketValue);
        BigDecimal profitLoss = new BigDecimal(unrealized);
        return new PortfolioPositionResponse(
            UUID.randomUUID(),
            "NSE",
            symbol,
            name,
            "INR",
            1,
            0,
            1,
            value.subtract(profitLoss),
            value,
            value.subtract(profitLoss),
            value,
            profitLoss,
            BigDecimal.ZERO,
            status,
            VALUED_AT
        );
    }

    private TradeResponse trade(
        String symbol,
        OrderSide side,
        long quantity,
        String grossValue,
        String charges,
        String netCashEffect,
        String executedAt
    ) {
        BigDecimal zero = BigDecimal.ZERO;
        return new TradeResponse(
            UUID.randomUUID(),
            UUID.randomUUID(),
            MarketRegion.INDIA.name(),
            "NSE",
            symbol,
            symbol + " Limited",
            side,
            quantity,
            new BigDecimal(grossValue).divide(BigDecimal.valueOf(quantity)),
            new BigDecimal(grossValue),
            new BigDecimal(charges),
            zero,
            zero,
            zero,
            zero,
            zero,
            zero,
            zero,
            new BigDecimal(netCashEffect),
            "test-v1",
            true,
            Instant.parse(executedAt)
        );
    }
}
