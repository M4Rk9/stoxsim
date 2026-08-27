package com.stoxsim.portfolio.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.service.TradingQueryService;
import com.stoxsim.portfolio.api.PortfolioAllocationResponse;
import com.stoxsim.portfolio.api.PortfolioAttributionResponse;
import com.stoxsim.portfolio.api.PortfolioInsightsResponse;
import com.stoxsim.portfolio.api.PortfolioPositionResponse;
import com.stoxsim.portfolio.api.PortfolioPositionResponse.PricingStatus;
import com.stoxsim.portfolio.api.PortfolioResponse;
import com.stoxsim.trade.api.TradeResponse;

@Service
public class PortfolioInsightsService {

    public static final String FORMULA_VERSION = "portfolio-insights-v1";
    public static final String DISCLAIMER = "Portfolio analytics explain simulated allocation and past paper-trading results. They do not predict returns or provide investment advice.";

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal RECONCILIATION_TOLERANCE = new BigDecimal("0.0001");

    private final PortfolioValuationService valuation;
    private final TradingQueryService tradingQueries;

    public PortfolioInsightsService(
        PortfolioValuationService valuation,
        TradingQueryService tradingQueries
    ) {
        this.valuation = valuation;
        this.tradingQueries = tradingQueries;
    }

    public PortfolioInsightsResponse analyze(UUID userId, MarketRegion marketRegion) {
        return analyze(
            valuation.value(userId, marketRegion),
            tradingQueries.trades(userId, marketRegion)
        );
    }

    PortfolioInsightsResponse analyze(PortfolioResponse portfolio, List<TradeResponse> trades) {
        BigDecimal cashValue = money(portfolio.availableCash().add(portfolio.blockedCash()));
        BigDecimal accountValue = portfolio.totalAccountValue();
        BigDecimal investedValue = portfolio.marketValue();
        BigDecimal pricedValue = portfolio.holdings().stream()
            .filter(position -> position.pricingStatus() != PricingStatus.UNAVAILABLE)
            .map(PortfolioPositionResponse::marketValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal coverage = percent(pricedValue, investedValue);

        List<PortfolioAllocationResponse> allocations = portfolio.holdings().stream()
            .filter(position -> position.marketValue().signum() > 0)
            .sorted(Comparator.comparing(PortfolioPositionResponse::marketValue).reversed())
            .map(position -> new PortfolioAllocationResponse(
                position.exchange(),
                position.symbol(),
                position.name(),
                money(position.marketValue()),
                percent(position.marketValue(), investedValue),
                percent(position.marketValue(), accountValue),
                money(position.unrealizedProfitLoss()),
                percentage(position.returnPercent()),
                position.pricingStatus()
            ))
            .toList();

        Map<String, AttributionState> attribution = replay(trades);
        for (PortfolioPositionResponse position : portfolio.holdings()) {
            String key = key(position.exchange(), position.symbol());
            AttributionState state = attribution.computeIfAbsent(
                key,
                ignored -> new AttributionState(position.exchange(), position.symbol(), position.name())
            );
            state.name = position.name();
            state.unrealized = money(position.unrealizedProfitLoss());
        }

        BigDecimal reconstructedRealized = attribution.values().stream()
            .map(state -> state.realized)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unattributed = money(portfolio.realizedProfitLoss().subtract(reconstructedRealized));
        if (unattributed.abs().compareTo(RECONCILIATION_TOLERANCE) >= 0) {
            AttributionState state = new AttributionState("SYSTEM", "UNATTRIBUTED", "Earlier activity");
            state.realized = unattributed;
            attribution.put(key(state.exchange, state.symbol), state);
        }

        List<PortfolioAttributionResponse> attributions = attribution.values().stream()
            .map(state -> state.response(portfolio.startingCapital()))
            .filter(response -> response.totalContribution().signum() != 0)
            .sorted(Comparator.comparing(
                (PortfolioAttributionResponse response) -> response.totalContribution().abs()
            ).reversed())
            .toList();

        boolean hasActivity = !trades.isEmpty()
            || !portfolio.holdings().isEmpty()
            || portfolio.realizedProfitLoss().signum() != 0
            || portfolio.unrealizedProfitLoss().signum() != 0;
        String status = status(portfolio, coverage, hasActivity);
        String confidence = confidence(portfolio, coverage, hasActivity);
        List<String> observations = observations(
            portfolio,
            allocations,
            attributions,
            coverage,
            cashValue,
            accountValue
        );

        return new PortfolioInsightsResponse(
            portfolio.marketRegion(),
            portfolio.currency(),
            FORMULA_VERSION,
            status,
            confidence,
            investedValue.signum() == 0 ? BigDecimal.ZERO.setScale(2) : coverage,
            cashValue,
            percent(cashValue, accountValue),
            percent(investedValue, accountValue),
            money(portfolio.realizedProfitLoss()),
            money(portfolio.unrealizedProfitLoss()),
            money(portfolio.totalProfitLoss()),
            allocations,
            attributions,
            observations,
            DISCLAIMER,
            portfolio.valuedAt()
        );
    }

    private Map<String, AttributionState> replay(List<TradeResponse> trades) {
        Map<String, AttributionState> states = new LinkedHashMap<>();
        trades.stream()
            .sorted(Comparator.comparing(TradeResponse::executedAt))
            .forEach(trade -> {
                AttributionState state = states.computeIfAbsent(
                    key(trade.exchange(), trade.symbol()),
                    ignored -> new AttributionState(trade.exchange(), trade.symbol(), trade.name())
                );
                if (trade.side() == OrderSide.BUY) {
                    state.quantity = Math.addExact(state.quantity, trade.quantity());
                    state.costPool = money(state.costPool.add(trade.netCashEffect()));
                    return;
                }
                if (state.quantity < trade.quantity() || state.quantity == 0) {
                    return;
                }
                BigDecimal averageCost = state.costPool.divide(
                    BigDecimal.valueOf(state.quantity),
                    8,
                    RoundingMode.HALF_UP
                );
                BigDecimal soldCost = averageCost.multiply(BigDecimal.valueOf(trade.quantity()));
                state.realized = money(state.realized.add(trade.netCashEffect().subtract(soldCost)));
                state.quantity -= trade.quantity();
                state.costPool = state.quantity == 0
                    ? BigDecimal.ZERO.setScale(4)
                    : money(state.costPool.subtract(soldCost));
            });
        return states;
    }

    private List<String> observations(
        PortfolioResponse portfolio,
        List<PortfolioAllocationResponse> allocations,
        List<PortfolioAttributionResponse> attributions,
        BigDecimal coverage,
        BigDecimal cashValue,
        BigDecimal accountValue
    ) {
        List<String> result = new ArrayList<>();
        if (allocations.isEmpty()) {
            result.add("No current invested positions are available for an allocation breakdown.");
        } else {
            PortfolioAllocationResponse largest = allocations.getFirst();
            result.add(largest.symbol() + " is the largest current allocation at "
                + compact(largest.investedWeightPercent()) + "% of invested market value.");
            result.add("Cash represents " + compact(percent(cashValue, accountValue))
                + "% of the simulated account value.");
        }
        if (attributions.isEmpty()) {
            result.add("No gain or loss contribution has been recorded yet.");
        } else {
            PortfolioAttributionResponse largestImpact = attributions.getFirst();
            result.add(largestImpact.symbol() + " has the largest absolute contribution to simulated P/L in this market.");
        }
        if (portfolio.marketValue().signum() > 0 && coverage.compareTo(HUNDRED) < 0) {
            result.add("Pricing covers " + compact(coverage)
                + "% of invested value; unavailable positions use StoxSim's cost-basis fallback.");
        }
        result.add("Contributions reconcile realized and unrealized simulated P/L since account creation.");
        return List.copyOf(result);
    }

    private String status(PortfolioResponse portfolio, BigDecimal coverage, boolean hasActivity) {
        if (!hasActivity) {
            return "NOT_ENOUGH_DATA";
        }
        if (portfolio.marketValue().signum() > 0
            && (coverage.compareTo(new BigDecimal("80")) < 0
                || portfolio.dataStatus() == PricingStatus.UNAVAILABLE)) {
            return "LIMITED_DATA";
        }
        return "AVAILABLE";
    }

    private String confidence(PortfolioResponse portfolio, BigDecimal coverage, boolean hasActivity) {
        if (!hasActivity) {
            return "NONE";
        }
        if (portfolio.marketValue().signum() > 0
            && (coverage.compareTo(new BigDecimal("80")) < 0
                || portfolio.dataStatus() == PricingStatus.UNAVAILABLE)) {
            return "LOW";
        }
        if (portfolio.marketValue().signum() > 0
            && (coverage.compareTo(HUNDRED) < 0 || portfolio.dataStatus() == PricingStatus.STALE)) {
            return "MEDIUM";
        }
        return "HIGH";
    }

    private String key(String exchange, String symbol) {
        return exchange + ":" + symbol;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal value, BigDecimal base) {
        if (base == null || base.signum() == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return value.multiply(HUNDRED).divide(base, 2, RoundingMode.HALF_UP);
    }

    private String compact(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private final class AttributionState {
        private final String exchange;
        private final String symbol;
        private String name;
        private long quantity;
        private BigDecimal costPool = BigDecimal.ZERO.setScale(4);
        private BigDecimal realized = BigDecimal.ZERO.setScale(4);
        private BigDecimal unrealized = BigDecimal.ZERO.setScale(4);

        private AttributionState(String exchange, String symbol, String name) {
            this.exchange = exchange;
            this.symbol = symbol;
            this.name = name;
        }

        private PortfolioAttributionResponse response(BigDecimal startingCapital) {
            BigDecimal total = money(realized.add(unrealized));
            return new PortfolioAttributionResponse(
                exchange,
                symbol,
                name,
                money(realized),
                money(unrealized),
                total,
                percent(total, startingCapital),
                total.signum() > 0 ? "GAIN" : total.signum() < 0 ? "LOSS" : "FLAT"
            );
        }
    }
}
