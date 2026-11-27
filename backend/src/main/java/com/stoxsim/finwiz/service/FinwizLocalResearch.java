package com.stoxsim.finwiz.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.stoxsim.finwiz.api.FinwizRequest;
import com.stoxsim.market.data.MarketDataStatus;
import com.stoxsim.portfolio.api.PortfolioPositionResponse.PricingStatus;
import com.stoxsim.portfolio.service.PortfolioAnalyticsService;
import com.stoxsim.portfolio.service.PortfolioValuationService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinwizLocalResearch {
    private static final String AMOUNT = "([0-9]{1,12}(?:\\.[0-9]{1,6})?)";
    private static final Pattern SIMPLE_RETURN = Pattern.compile(
        "(?:calculate )?(?:simple return|price return|percentage change) from " + AMOUNT + " to " + AMOUNT);
    private static final Set<String> PRICE_QUESTIONS = Set.of(
        "what is the current price", "current stock price", "latest stock price",
        "what is the latest price", "show the latest quote", "what is the share price");
    private static final Set<String> PORTFOLIO_RETURNS = Set.of(
        "what is my portfolio return", "calculate my portfolio return", "show my paper portfolio return");
    private static final Set<String> PORTFOLIO_RISK = Set.of(
        "explain my portfolio risk", "how concentrated is my portfolio", "explain my portfolio concentration");

    private final FinwizContextService contexts;
    private final PortfolioValuationService valuation;
    private final PortfolioAnalyticsService analytics;

    public FinwizLocalResearch(FinwizContextService contexts, PortfolioValuationService valuation,
        PortfolioAnalyticsService analytics) {
        this.contexts = contexts;
        this.valuation = valuation;
        this.analytics = analytics;
    }

    record Answer(String text, String kind, Instant asOf) { }

    Answer resolve(UUID user, FinwizRequest request, String normalizedQuestion, FinwizDepth depth) {
        if (request.requestsStockContext()) {
            return isPriceQuestion(request, normalizedQuestion) ? quote(request) : null;
        }
        if (PRICE_QUESTIONS.contains(normalizedQuestion)) {
            return new Answer("Choose a stock, exchange and market to see an available quote.", "unavailable", null);
        }
        var calculation = calculate(normalizedQuestion, depth);
        if (calculation != null) return calculation;
        boolean returns = PORTFOLIO_RETURNS.contains(normalizedQuestion);
        if (!returns && !PORTFOLIO_RISK.contains(normalizedQuestion)) return null;
        if (request.marketRegion() == null) {
            return new Answer("Choose a market to see the return or concentration of your paper portfolio.", "unavailable", null);
        }
        try {
            return returns ? portfolioReturn(user, request, depth) : portfolioRisk(user, request, depth);
        } catch (DataAccessException | ResponseStatusException e) {
            return new Answer("Your paper portfolio data is unavailable right now.", "unavailable", null);
        }
    }

    private static boolean isPriceQuestion(FinwizRequest request, String question) {
        if (PRICE_QUESTIONS.contains(question)) return true;
        String symbol = FinwizKnowledge.normalize(request.symbol());
        return !symbol.isEmpty() && Set.of("what is the current price of ", "current price of ", "what is the latest price of ", "latest quote for ")
            .stream().anyMatch(prefix -> question.equals(prefix + symbol));
    }

    private Answer quote(FinwizRequest request) {
        var quote = contexts.quote(request);
        if (quote == null || quote.lastPrice() == null || quote.lastPrice().signum() <= 0
            || !Set.of("INR", "USD").contains(quote.currency() == null ? "" : quote.currency())
            || quote.dataStatus() == null || quote.dataStatus() == MarketDataStatus.UNAVAILABLE) {
            return new Answer("A verified external quote is unavailable for the selected stock.", "unavailable", null);
        }
        Instant asOf = quote.exchangeTimestamp() == null ? quote.receivedAt() : quote.exchangeTimestamp();
        if (asOf == null) return new Answer("A timestamped quote is unavailable for the selected stock.", "unavailable", null);
        String status = switch (quote.dataStatus()) {
            case LIVE -> "latest feed quote";
            case CLOSED -> "market closed";
            case STALE -> "stale quote";
            case UNAVAILABLE -> throw new IllegalStateException("Unavailable quote passed validation");
        };
        String timeLabel = quote.exchangeTimestamp() == null ? "Received at" : "Observed at";
        return new Answer("**" + quote.symbol() + " · " + quote.exchange() + "**\n\n"
            + quote.currency() + " " + number(quote.lastPrice()) + " (" + status + "). "
            + timeLabel + " " + asOf + ".", "market_data", asOf);
    }

    static Answer calculate(String question, FinwizDepth depth) {
        var match = SIMPLE_RETURN.matcher(question);
        if (!match.matches()) return null;
        BigDecimal start = new BigDecimal(match.group(1));
        BigDecimal end = new BigDecimal(match.group(2));
        if (start.signum() == 0) {
            return new Answer("The starting value must be greater than zero to calculate a percentage return.", "calculation", null);
        }
        BigDecimal percent = end.subtract(start).movePointRight(2).divide(start, 4, RoundingMode.HALF_UP);
        String detail = switch (depth) {
            case BASIC -> "";
            case EXPANDED -> "\n\nAbsolute change: " + number(end.subtract(start)) + " in the original units.";
            case FULL -> "\n\nAbsolute change: " + number(end.subtract(start))
                + " in the original units. The result is a holding-period price change. Annualizing it requires the elapsed time.";
        };
        return new Answer("**" + number(percent) + "%**\n\n(" + number(end) + " − " + number(start)
            + ") ÷ " + number(start) + " × 100. Both values must use the same units. This excludes costs, distributions and cash flows."
            + detail, "calculation", null);
    }

    private Answer portfolioReturn(UUID user, FinwizRequest request, FinwizDepth depth) {
        var result = valuation.value(user, request.marketRegion());
        if (result.totalReturnPercent() == null || result.dataStatus() == PricingStatus.UNAVAILABLE) {
            return new Answer("There is insufficient pricing data to calculate your paper portfolio return.", "unavailable", null);
        }
        String answer = "**" + number(result.totalReturnPercent()) + "%** return for your "
            + request.marketRegion().name().replace('_', ' ') + " paper portfolio."
            + "\n\nStoxSim compares your portfolio equity with its starting capital.";
        if (depth != FinwizDepth.BASIC) {
            answer += "\n\nPortfolio equity: " + result.currency() + " " + number(result.totalAccountValue())
                + ". Starting capital: " + result.currency() + " " + number(result.startingCapital()) + ".";
        }
        if (depth == FinwizDepth.FULL) {
            answer += "\n\nBenchmark comparisons need matching dates and a consistent treatment of cash flows.";
        }
        answer += "\n\nPricing status: " + result.dataStatus().name().toLowerCase(java.util.Locale.ROOT).replace('_',' ') + ".";
        return new Answer(answer, "portfolio", result.valuedAt());
    }

    private Answer portfolioRisk(UUID user, FinwizRequest request, FinwizDepth depth) {
        var result = analytics.analyze(user, request.marketRegion());
        if (result.holdingCount() == 0 || result.largestPositionWeightPercent() == null) {
            return new Answer("Your paper portfolio has no valued positions to assess for concentration.", "unavailable", null);
        }
        String answer = "Your largest position represents **" + number(result.largestPositionWeightPercent())
            + "%** of the measured holdings. A larger share makes your portfolio more sensitive to that holding.";
        if (depth != FinwizDepth.BASIC && result.topThreeWeightPercent() != null) {
            answer += "\n\nThe largest three positions represent " + number(result.topThreeWeightPercent()) + "% combined.";
        }
        if (depth == FinwizDepth.FULL) {
            answer += "\n\nCompare sector weights and common economic exposures as well. Several positions can still depend on the same source of risk.";
        }
        if (!"HIGH".equals(result.confidence())) answer += "\n\nPricing may be stale or incomplete. Treat the weights as approximate.";
        return new Answer(answer, "portfolio", result.valuedAt());
    }

    private static String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
