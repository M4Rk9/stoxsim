package com.stoxsim.scenario;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.portfolio.api.PortfolioPositionResponse.PricingStatus;
import com.stoxsim.portfolio.service.PortfolioValuationService;
import com.stoxsim.subscription.domain.SubscriptionFeature;
import com.stoxsim.subscription.repository.UserSubscriptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScenarioService {
    public record Definition(String id, String title, int version, String inputType, List<BigDecimal> shocks, String lesson) {}
    public record Request(String scenarioId, Integer version, BigDecimal customShockPercent) {}
    public record Position(String symbol, String exchange, long quantity, Instant priceTimestamp, BigDecimal startingPrice, BigDecimal endingPrice, BigDecimal startingValue, BigDecimal endingValue, BigDecimal change) {}
    public record Result(UUID accountId, String currency, Instant valuedAt, Definition scenario, BigDecimal cash, BigDecimal startingInvestedValue, BigDecimal startingEquity, ScenarioMath.Projection projection, List<Position> positions, List<String> assumptions) {}
    public static final List<Definition> CATALOG = List.of(
        definition("selloff", "Market sell-off", List.of("-10", "-20", "-30"), "Cash cushions a uniform fall in equity prices."),
        definition("recovery", "Fall and partial recovery", List.of("-20", "-35", "-15", "0", "10"), "A positive ending return can hide a deep drawdown along the way."),
        definition("whipsaw", "Rally then reversal", List.of("15", "30", "5", "-10"), "Drawdown is measured from the highest portfolio value, not just the starting value."),
        definition("custom", "Custom price shock", List.of("-10"), "Explore a single uniform price move between -100% and +100%.")
    );
    private static Definition definition(String id, String title, List<String> shocks, String lesson) {
        return new Definition(id, title, 1, "SYNTHETIC", shocks.stream().map(BigDecimal::new).toList(), lesson);
    }
    private final VirtualAccountRepository accounts;
    private final UserSubscriptionRepository subscriptions;
    private final PortfolioValuationService valuation;
    private final Clock clock;
    public ScenarioService(VirtualAccountRepository accounts, UserSubscriptionRepository subscriptions, PortfolioValuationService valuation, Clock clock) {
        this.accounts=accounts; this.subscriptions=subscriptions; this.valuation=valuation; this.clock=clock;
    }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Result run(UUID user, UUID accountId, Request request) {
        accounts.findOwnedById(user, accountId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        var subscription = subscriptions.findByUserId(user).orElse(null);
        if (subscription == null || !subscription.hasActiveEntitlement(SubscriptionFeature.SCENARIO_LAB))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Scenario Lab requires active Pro benefits");
        if (request == null || request.version() == null || request.version() != 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a supported scenario version");
        var selected = CATALOG.stream().filter(s -> s.id().equals(request.scenarioId())).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a listed scenario"));
        if (selected.id().equals("custom")) {
            var shock = request.customShockPercent();
            if (shock == null || shock.compareTo(new BigDecimal("-100")) < 0 || shock.compareTo(new BigDecimal("100")) > 0 || shock.stripTrailingZeros().scale() > 2)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a shock from -100 to 100 with at most two decimal places");
            selected = new Definition(selected.id(), selected.title(), selected.version(), selected.inputType(), List.of(shock), selected.lesson());
        } else if (request.customShockPercent() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Custom shocks apply only to the custom scenario");
        }
        var portfolio = valuation.valueForAccount(user, accountId);
        var now = clock.instant();
        boolean unpriced = portfolio.holdings().stream().anyMatch(p ->
            (p.pricingStatus() != PricingStatus.LIVE && p.pricingStatus() != PricingStatus.CLOSED)
            || p.priceTimestamp() == null || p.priceTimestamp().isAfter(now)
            || !portfolio.currency().equals(p.currency()) || p.currentPrice() == null || p.currentPrice().signum() <= 0);
        if (unpriced) throw new ResponseStatusException(HttpStatus.CONFLICT, "Reliable prices are unavailable for one or more holdings. Refresh prices before running a scenario.");
        var cash = portfolio.availableCash().add(portfolio.blockedCash());
        var invested = portfolio.holdings().stream().map(p -> p.marketValue()).reduce(BigDecimal.ZERO, BigDecimal::add);
        var projection = ScenarioMath.project(cash, invested, selected.shocks());
        var finalShock = selected.shocks().getLast();
        var positions = portfolio.holdings().stream().map(p -> {
            var ending = ScenarioMath.stressed(p.marketValue(), finalShock);
            return new Position(p.symbol(), p.exchange(), p.quantity(), p.priceTimestamp(), p.currentPrice(), ScenarioMath.stressed(p.currentPrice(), finalShock), p.marketValue(), ending, ScenarioMath.money(ending.subtract(p.marketValue())));
        }).toList();
        return new Result(accountId, portfolio.currency(), portfolio.valuedAt(), selected, ScenarioMath.money(cash), ScenarioMath.money(invested), projection.steps().getFirst().equity(), projection, positions, List.of(
            "Synthetic educational inputs; not historical events, forecasts, probabilities or investment advice.",
            "Every holding receives the same cumulative price shock relative to its starting price. Steps have no calendar duration and are not compounded sequential returns.",
            "Quantities and available plus blocked cash stay fixed. Pending orders do not execute; no rebalancing, deposits or withdrawals occur.",
            "Dividends, taxes, fees, slippage, FX, stock-specific sensitivity and corporate actions are excluded. Rounded position totals can differ slightly from aggregate values.",
            "Starting marks may be last available closed-session quotes; each holding includes its quote timestamp. A new run can use different marks.",
            "This result is not saved and changes no account balance, holding, order, portfolio history or competition score."
        ));
    }
}
