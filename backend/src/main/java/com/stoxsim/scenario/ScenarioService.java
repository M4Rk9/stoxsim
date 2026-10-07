package com.stoxsim.scenario;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.portfolio.api.PortfolioPositionResponse.PricingStatus;
import com.stoxsim.portfolio.service.PortfolioValuationService;
import com.stoxsim.subscription.repository.UserSubscriptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScenarioService {
    public record Definition(String id, String title, int version, String inputType, List<BigDecimal> shocks, String lesson) {}
    public record Request(String scenarioId, Integer version, BigDecimal customShockPercent, String title, List<BigDecimal> customShocks, UUID requestId) {}
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
    private final org.springframework.jdbc.core.JdbcTemplate db;
    private final tools.jackson.databind.ObjectMapper json;
    public ScenarioService(VirtualAccountRepository accounts, UserSubscriptionRepository subscriptions, PortfolioValuationService valuation, Clock clock, org.springframework.jdbc.core.JdbcTemplate db, tools.jackson.databind.ObjectMapper json) {
        this.accounts=accounts; this.subscriptions=subscriptions; this.valuation=valuation; this.clock=clock; this.db=db; this.json=json;
    }
    @Transactional
    public Result run(UUID user, UUID accountId, Request request) {
        accounts.findOwnedById(user, accountId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        var subscription = subscriptions.findByUserIdForUpdate(user).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
        UUID requestId = request == null || request.requestId() == null ? UUID.randomUUID() : request.requestId();
        String payload = json.writeValueAsString(request);
        var previous = db.queryForList("SELECT account_id, request_payload, result_payload FROM scenario_run WHERE user_id=? AND request_id=?", user, requestId);
        if (!previous.isEmpty()) {
            var row = previous.getFirst();
            if (!accountId.equals(row.get("account_id")) || !payload.equals(row.get("request_payload")))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This request ID already belongs to another scenario");
            return json.readValue((String)row.get("result_payload"), Result.class);
        }
        if (request == null || request.version() == null || request.version() != 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a supported scenario version");
        var selected = CATALOG.stream().filter(s -> s.id().equals(request.scenarioId())).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a listed scenario"));
        if (selected.id().equals("custom")) {
            var shocks = request.customShocks() != null ? request.customShocks()
                : request.customShockPercent() == null ? List.<BigDecimal>of() : List.of(request.customShockPercent());
            if (request.customShocks() != null && request.customShockPercent() != null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a single price move or a custom path");
            if (shocks.isEmpty() || shocks.size() > 12 || shocks.stream().anyMatch(shock -> shock == null
                || shock.compareTo(new BigDecimal("-100")) < 0 || shock.compareTo(new BigDecimal("100")) > 0 || shock.stripTrailingZeros().scale() > 2))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use 1–12 price moves from -100% to 100%, with at most two decimal places");
            String title = request.title() == null || request.title().isBlank() ? "My scenario" : request.title().trim();
            if (title.length() > 80) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a scenario name of up to 80 characters");
            selected = new Definition(selected.id(), title, selected.version(), selected.inputType(), List.copyOf(shocks), selected.lesson());
        } else if (request.customShockPercent() != null || request.customShocks() != null || request.title() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Custom shocks apply only to the custom scenario");
        }
        subscription.consumeScenarioCredit();
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
        var result = new Result(accountId, portfolio.currency(), portfolio.valuedAt(), selected, ScenarioMath.money(cash), ScenarioMath.money(invested), projection.steps().getFirst().equity(), projection, positions, List.of(
            "Synthetic educational inputs; not historical events, forecasts, probabilities or investment advice.",
            "Every holding receives the same cumulative price shock relative to its starting price. Steps have no calendar duration and are not compounded sequential returns.",
            "Quantities and available plus blocked cash stay fixed. Pending orders do not execute; no rebalancing, deposits or withdrawals occur.",
            "Dividends, taxes, fees, slippage, FX, stock-specific sensitivity and corporate actions are excluded. Rounded position totals can differ slightly from aggregate values.",
            "Starting marks may be last available closed-session quotes; each holding includes its quote timestamp. A new run can use different marks.",
            "Scenarios change no balance, holding, order, portfolio history or competition score."
        ));
        db.update("INSERT INTO scenario_run(user_id,request_id,account_id,request_payload,result_payload) VALUES (?,?,?,?,?)", user, requestId, accountId, payload, json.writeValueAsString(result));
        return result;
    }
    public record Credits(String plan, int allowance, int remaining, Instant renewsAt) {}
    @Transactional(readOnly=true)
    public Credits credits(UUID user) {
        var subscription = subscriptions.findByUserId(user).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription not found"));
        var plan = subscription.effectivePlan();
        return new Credits(plan.name(), plan.scenarioCredits(), subscription.remainingScenarioCredits(), plan == com.stoxsim.subscription.domain.SubscriptionPlan.FREE ? null : subscription.getScenarioCreditPeriodEnd());
    }
}
