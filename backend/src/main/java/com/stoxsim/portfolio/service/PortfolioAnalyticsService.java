package com.stoxsim.portfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.portfolio.api.PortfolioAnalyticsResponse;
import com.stoxsim.portfolio.api.PortfolioConcentrationResponse;
import com.stoxsim.portfolio.api.PortfolioPositionResponse;
import com.stoxsim.portfolio.api.PortfolioPositionResponse.PricingStatus;
import com.stoxsim.portfolio.api.StoxScoreComponentResponse;

@Service
public class PortfolioAnalyticsService {

    public static final String FORMULA_VERSION = "stoxscore-portfolio-v1";
    public static final String DISCLAIMER = "StoxScore is an educational portfolio-structure indicator. It does not predict returns or provide investment advice.";

    private static final int TARGET_HOLDINGS = 8;
    private static final double BREADTH_WEIGHT = 0.35;
    private static final double BALANCE_WEIGHT = 0.45;
    private static final double CONCENTRATION_WEIGHT = 0.20;

    private final PortfolioValuationService valuation;

    public PortfolioAnalyticsService(PortfolioValuationService valuation) {
        this.valuation = valuation;
    }

    public PortfolioAnalyticsResponse analyze(UUID userId, MarketRegion marketRegion) {
        return analyze(valuation.value(userId, marketRegion));
    }

    PortfolioAnalyticsResponse analyze(com.stoxsim.portfolio.api.PortfolioResponse portfolio) {
        List<PortfolioPositionResponse> positions = portfolio.holdings().stream()
            .filter(position -> position.marketValue().signum() > 0)
            .sorted(Comparator.comparing(PortfolioPositionResponse::marketValue).reversed())
            .toList();
        BigDecimal totalValue = positions.stream()
            .map(PortfolioPositionResponse::marketValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (positions.isEmpty() || totalValue.signum() <= 0) {
            return empty(portfolio);
        }

        List<WeightedPosition> weighted = positions.stream()
            .map(position -> new WeightedPosition(
                position,
                position.marketValue().divide(totalValue, 12, RoundingMode.HALF_UP).doubleValue()
            ))
            .toList();
        double concentrationIndex = weighted.stream()
            .mapToDouble(position -> position.weight() * position.weight())
            .sum();
        double effectiveHoldings = concentrationIndex == 0 ? 0 : 1 / concentrationIndex;
        double largestWeight = weighted.getFirst().weight();
        double topThreeWeight = weighted.stream().limit(3)
            .mapToDouble(WeightedPosition::weight)
            .sum();
        double coverage = weighted.stream()
            .filter(position -> position.position().pricingStatus() != PricingStatus.UNAVAILABLE)
            .mapToDouble(WeightedPosition::weight)
            .sum();

        int breadthScore = rounded(clamp(positions.size() * 100.0 / TARGET_HOLDINGS));
        int balanceScore = rounded(clamp(
            (effectiveHoldings - 1) * 100.0 / (TARGET_HOLDINGS - 1)
        ));
        int concentrationScore = rounded(concentrationScore(largestWeight));
        int score = rounded(
            breadthScore * BREADTH_WEIGHT
                + balanceScore * BALANCE_WEIGHT
                + concentrationScore * CONCENTRATION_WEIGHT
        );

        String status = coverage < 0.80 || portfolio.dataStatus() == PricingStatus.UNAVAILABLE
            ? "LIMITED_DATA"
            : "AVAILABLE";
        String confidence = confidence(portfolio.dataStatus(), coverage);
        List<StoxScoreComponentResponse> components = List.of(
            new StoxScoreComponentResponse(
                "BREADTH",
                "Portfolio breadth",
                breadthScore,
                35,
                positions.size() + " positive-value holdings measured against the v1 learning target of " + TARGET_HOLDINGS + "."
            ),
            new StoxScoreComponentResponse(
                "BALANCE",
                "Weight balance",
                balanceScore,
                45,
                decimal(effectiveHoldings) + " effective holdings after accounting for unequal position sizes."
            ),
            new StoxScoreComponentResponse(
                "CONCENTRATION",
                "Largest-position concentration",
                concentrationScore,
                20,
                "The largest position represents " + percent(largestWeight) + "% of invested market value."
            )
        );

        List<PortfolioConcentrationResponse> largestPositions = weighted.stream()
            .limit(3)
            .map(position -> new PortfolioConcentrationResponse(
                position.position().symbol(),
                percentage(position.weight())
            ))
            .toList();

        List<String> observations = new ArrayList<>();
        observations.add("The top three positions account for " + percent(topThreeWeight) + "% of invested market value.");
        observations.add("Effective holdings compare actual diversification with an equally weighted portfolio.");
        if (coverage < 1) {
            observations.add("Only " + percent(coverage) + "% of invested value has an available market price; unavailable holdings use StoxSim's valuation fallback.");
        }

        return new PortfolioAnalyticsResponse(
            portfolio.marketRegion(),
            FORMULA_VERSION,
            status,
            score,
            structureBand(score),
            confidence,
            percentage(coverage),
            positions.size(),
            decimal(effectiveHoldings),
            percentage(largestWeight),
            percentage(topThreeWeight),
            ratio(concentrationIndex),
            components,
            largestPositions,
            List.copyOf(observations),
            DISCLAIMER,
            portfolio.valuedAt()
        );
    }

    private PortfolioAnalyticsResponse empty(com.stoxsim.portfolio.api.PortfolioResponse portfolio) {
        return new PortfolioAnalyticsResponse(
            portfolio.marketRegion(),
            FORMULA_VERSION,
            "NOT_ENOUGH_DATA",
            null,
            "Not scored yet",
            "NONE",
            BigDecimal.ZERO.setScale(2),
            0,
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(4),
            List.of(),
            List.of(),
            List.of("Place an educational paper trade to begin measuring portfolio structure."),
            DISCLAIMER,
            portfolio.valuedAt()
        );
    }

    private double concentrationScore(double largestWeight) {
        if (largestWeight <= 0.20) {
            return 100;
        }
        if (largestWeight >= 0.80) {
            return 0;
        }
        return (0.80 - largestWeight) * 100 / 0.60;
    }

    private String confidence(PricingStatus dataStatus, double coverage) {
        if (coverage < 0.80 || dataStatus == PricingStatus.UNAVAILABLE) {
            return "LOW";
        }
        if (coverage < 1 || dataStatus == PricingStatus.STALE) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private String structureBand(int score) {
        if (score < 40) {
            return "Concentrated";
        }
        if (score < 60) {
            return "Developing";
        }
        if (score < 80) {
            return "Diversified";
        }
        return "Broadly diversified";
    }

    private int rounded(double value) {
        return (int) Math.round(clamp(value));
    }

    private double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private BigDecimal percentage(double value) {
        return BigDecimal.valueOf(value * 100).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    private String percent(double value) {
        return percentage(value).stripTrailingZeros().toPlainString();
    }

    private record WeightedPosition(
        PortfolioPositionResponse position,
        double weight
    ) {
    }
}
