package com.stoxsim.market.provider.sec;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.stoxsim.market.api.StockInsightsResponse;
import com.stoxsim.market.api.StockInsightsResponse.CompanyProfile;
import com.stoxsim.market.api.StockInsightsResponse.FinancialHistoryPoint;
import com.stoxsim.market.api.StockInsightsResponse.FinancialMetric;
import com.stoxsim.market.api.StockInsightsResponse.FinancialPerformance;
import com.stoxsim.market.api.StockInsightsResponse.FundamentalRatio;
import com.stoxsim.market.api.StockInsightsResponse.FundamentalsStatus;

import tools.jackson.databind.JsonNode;

@Component
public class SecFundamentalsClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecFundamentalsClient.class);
    private static final Duration TICKER_INDEX_TTL = Duration.ofHours(24);
    private static final BigDecimal MILLION = new BigDecimal("1000000");

    private final RestClient secData;
    private final RestClient secFiles;
    private final SecRequestThrottle throttle;
    private volatile Map<String, CompanyKey> tickerIndex = Map.of();
    private volatile Instant tickerIndexAt = Instant.EPOCH;

    public SecFundamentalsClient(
        @Value("${stoxsim.market-data.sec.user-agent:StoxSim/1.0 support.stoxsim@gmail.com}") String userAgent,
        @Value("${stoxsim.market-data.sec.max-requests-per-second:8}") int maxRequestsPerSecond
    ) {
        String identifiedUserAgent = userAgent == null ? "" : userAgent.trim();
        if (identifiedUserAgent.isBlank() || !identifiedUserAgent.contains("@")) {
            throw new IllegalArgumentException(
                "SEC_USER_AGENT must identify StoxSim and include a monitored contact email"
            );
        }
        this.throttle = new SecRequestThrottle(maxRequestsPerSecond);
        this.secData = RestClient.builder()
            .baseUrl("https://data.sec.gov")
            .defaultHeader("User-Agent", identifiedUserAgent)
            .defaultHeader("Accept-Encoding", "gzip, deflate")
            .build();
        this.secFiles = RestClient.builder()
            .baseUrl("https://www.sec.gov")
            .defaultHeader("User-Agent", identifiedUserAgent)
            .defaultHeader("Accept-Encoding", "gzip, deflate")
            .build();
    }

    SecFundamentalsClient(String userAgent) {
        this(userAgent, 8);
    }

    public StockInsightsResponse get(
        String symbol,
        String requestedPeriod,
        BigDecimal lastPrice
    ) {
        CompanyKey key = company(symbol);
        JsonNode submissions = getJson(secData, "/submissions/CIK" + key.cik() + ".json");
        JsonNode facts = getJson(secData, "/api/xbrl/companyfacts/CIK" + key.cik() + ".json");

        CompanyProfile profile = profile(submissions, key);
        List<FundamentalRatio> ratios = ratios(facts, lastPrice);
        FinancialPerformance financials = financials(facts, requestedPeriod);
        boolean hasProfile = profile != null;
        boolean hasRatios = !ratios.isEmpty();
        boolean hasFinancials = financials != null && !financials.metrics().isEmpty();
        int available = (hasProfile ? 1 : 0) + (hasRatios ? 1 : 0) + (hasFinancials ? 1 : 0);
        FundamentalsStatus status = available == 3
            ? FundamentalsStatus.AVAILABLE
            : available > 0 ? FundamentalsStatus.PARTIAL : FundamentalsStatus.UNAVAILABLE;
        String message = status == FundamentalsStatus.AVAILABLE
            ? null
            : status == FundamentalsStatus.PARTIAL
                ? "Some SEC filing metrics are not reported in a comparable XBRL concept for this company."
                : "SEC EDGAR fundamentals are unavailable for this company.";

        return new StockInsightsResponse(
            "SEC_EDGAR",
            symbol.toUpperCase(Locale.ROOT),
            null,
            Instant.now(),
            status,
            profile,
            ratios,
            financials,
            message
        );
    }

    private CompanyKey company(String symbol) {
        refreshTickerIndexIfNeeded();
        CompanyKey result = tickerIndex.get(normalizeSymbol(symbol));
        if (result == null) {
            throw new IllegalStateException("SEC EDGAR could not map ticker " + symbol + " to a CIK");
        }
        return result;
    }

    private synchronized void refreshTickerIndexIfNeeded() {
        if (!tickerIndex.isEmpty()
            && tickerIndexAt.isAfter(Instant.now().minus(TICKER_INDEX_TTL))) {
            return;
        }
        JsonNode root = getJson(secFiles, "/files/company_tickers.json");
        if (root == null || !root.isObject()) {
            throw new IllegalStateException("SEC ticker index is unavailable");
        }
        Map<String, CompanyKey> mapped = new LinkedHashMap<>();
        root.properties().forEach(entry -> {
            JsonNode value = entry.getValue();
            String ticker = text(value, "ticker");
            int cik = value.path("cik_str").asInt(0);
            if (ticker == null || cik <= 0) return;
            mapped.put(
                normalizeSymbol(ticker),
                new CompanyKey(
                    String.format(Locale.ROOT, "%010d", cik),
                    ticker,
                    text(value, "title")
                )
            );
        });
        if (mapped.size() < 5_000) {
            throw new IllegalStateException("SEC ticker index returned an unexpectedly small catalogue");
        }
        tickerIndex = Map.copyOf(mapped);
        tickerIndexAt = Instant.now();
    }

    private JsonNode getJson(RestClient client, String path) {
        throttle.acquire();
        try {
            return client.get().uri(path).retrieve().body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new IllegalStateException("SEC EDGAR request failed for " + path, exception);
        }
    }

    private CompanyProfile profile(JsonNode submissions, CompanyKey key) {
        String name = text(submissions, "name");
        if (name == null) name = key.title();
        String industry = text(submissions, "sicDescription");
        String exchange = firstText(submissions == null ? null : submissions.get("exchanges"));
        String description = name == null
            ? null
            : name + " is a U.S. public-company filer in SEC EDGAR"
                + (industry == null ? "" : " classified under " + industry)
                + (exchange == null ? "" : " and listed on " + exchange)
                + ". Financial figures below are extracted from standardized XBRL facts in SEC filings.";
        if (description == null && industry == null) return null;
        return new CompanyProfile(description, industry, null, null);
    }

    List<FundamentalRatio> ratios(JsonNode facts, BigDecimal lastPrice) {
        BigDecimal eps = SecFactSeries.trailingTwelveMonths(
            facts,
            "us-gaap",
            List.of("EarningsPerShareDiluted", "EarningsPerShareBasic"),
            "USD/shares"
        );
        BigDecimal revenue = SecFactSeries.trailingTwelveMonths(
            facts,
            "us-gaap",
            revenueTags(),
            "USD"
        );
        BigDecimal netIncome = SecFactSeries.trailingTwelveMonths(
            facts,
            "us-gaap",
            List.of("NetIncomeLoss", "ProfitLoss"),
            "USD"
        );
        BigDecimal operatingIncome = SecFactSeries.trailingTwelveMonths(
            facts,
            "us-gaap",
            List.of("OperatingIncomeLoss"),
            "USD"
        );
        BigDecimal operatingCashFlow = SecFactSeries.trailingTwelveMonths(
            facts,
            "us-gaap",
            List.of("NetCashProvidedByUsedInOperatingActivities"),
            "USD"
        );
        BigDecimal assets = SecFactSeries.latestInstant(
            facts,
            "us-gaap",
            List.of("Assets"),
            "USD"
        );
        BigDecimal liabilities = SecFactSeries.latestInstant(
            facts,
            "us-gaap",
            List.of("Liabilities"),
            "USD"
        );

        List<FundamentalRatio> ratios = new ArrayList<>();
        if (lastPrice != null && eps != null && eps.signum() > 0) {
            ratios.add(ratio(
                "P/E (TTM)",
                lastPrice.divide(eps, 2, RoundingMode.HALF_UP)
            ));
        }
        if (eps != null) {
            ratios.add(ratio(
                "Diluted EPS (TTM)",
                eps.setScale(2, RoundingMode.HALF_UP)
            ));
        }
        addPercentRatio(ratios, "Net margin", netIncome, revenue);
        addPercentRatio(ratios, "Operating margin", operatingIncome, revenue);
        addPercentRatio(
            ratios,
            "Operating cash-flow margin",
            operatingCashFlow,
            revenue
        );
        addPercentRatio(ratios, "Liabilities / assets", liabilities, assets);
        return List.copyOf(ratios);
    }

    private FundamentalRatio ratio(String name, BigDecimal value) {
        return new FundamentalRatio(name, value.stripTrailingZeros().toPlainString(), null);
    }

    private void addPercentRatio(
        List<FundamentalRatio> target,
        String name,
        BigDecimal numerator,
        BigDecimal denominator
    ) {
        if (numerator == null || denominator == null || denominator.signum() == 0) return;
        BigDecimal value = numerator.multiply(BigDecimal.valueOf(100))
            .divide(denominator, 2, RoundingMode.HALF_UP);
        target.add(new FundamentalRatio(name, value.toPlainString() + "%", null));
    }

    FinancialPerformance financials(JsonNode facts, String requestedPeriod) {
        boolean yearly = "yearly".equalsIgnoreCase(requestedPeriod);
        List<SecFactSeries.Point> revenue = series(
            facts,
            revenueTags(),
            yearly
        );
        List<SecFactSeries.Point> netIncome = series(
            facts,
            List.of("NetIncomeLoss", "ProfitLoss"),
            yearly
        );
        List<SecFactSeries.Point> operatingIncome = series(
            facts,
            List.of("OperatingIncomeLoss"),
            yearly
        );
        List<SecFactSeries.Point> operatingCashFlow = series(
            facts,
            List.of("NetCashProvidedByUsedInOperatingActivities"),
            yearly
        );
        List<SecFactSeries.Point> capex = series(
            facts,
            List.of(
                "PaymentsToAcquirePropertyPlantAndEquipment",
                "PaymentsForAdditionsToPropertyPlantAndEquipment"
            ),
            yearly
        );

        List<FinancialMetric> metrics = new ArrayList<>();
        addMetric(metrics, "revenue", revenue);
        addMetric(metrics, "net_profit", netIncome);
        addMetric(metrics, "operating_profit", operatingIncome);
        addMetric(metrics, "operating_cash_flow", operatingCashFlow);
        addMetric(
            metrics,
            "free_cash_flow",
            subtractByPeriod(operatingCashFlow, capex)
        );
        if (metrics.isEmpty()) return null;
        return new FinancialPerformance(
            "SEC XBRL financial statements",
            yearly ? "yearly" : "quarterly",
            "USD million",
            List.copyOf(metrics)
        );
    }

    private List<SecFactSeries.Point> series(
        JsonNode facts,
        List<String> tags,
        boolean yearly
    ) {
        return yearly
            ? SecFactSeries.yearly(facts, "us-gaap", tags, "USD")
            : SecFactSeries.quarterly(facts, "us-gaap", tags, "USD");
    }

    private void addMetric(List<FinancialMetric> target, String category, List<SecFactSeries.Point> points) {
        if (points.isEmpty()) return;
        List<FinancialHistoryPoint> history = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            SecFactSeries.Point point = points.get(index);
            BigDecimal change = index + 1 < points.size()
                ? percentChange(point.value(), points.get(index + 1).value())
                : null;
            history.add(new FinancialHistoryPoint(
                point.end().toString(),
                point.value().divide(MILLION, 2, RoundingMode.HALF_UP),
                change == null ? null : (change.signum() >= 0 ? "+" : "") + change.toPlainString() + "%"
            ));
        }
        target.add(new FinancialMetric(category, List.copyOf(history)));
    }

    private BigDecimal percentChange(BigDecimal current, BigDecimal previous) {
        if (current == null || previous == null || previous.signum() == 0) return null;
        return current.subtract(previous)
            .multiply(BigDecimal.valueOf(100))
            .divide(previous.abs(), 1, RoundingMode.HALF_UP);
    }

    private List<SecFactSeries.Point> subtractByPeriod(List<SecFactSeries.Point> left, List<SecFactSeries.Point> right) {
        if (left.isEmpty() || right.isEmpty()) return List.of();
        Map<LocalDate, BigDecimal> rightByEnd = new LinkedHashMap<>();
        right.forEach(point -> rightByEnd.put(point.end(), point.value()));
        return left.stream()
            .filter(point -> rightByEnd.containsKey(point.end()))
            .map(point -> new SecFactSeries.Point(
                point.end(),
                point.value().subtract(rightByEnd.get(point.end())),
                point.filed()
            ))
            .toList();
    }

    private List<String> revenueTags() {
        return List.of(
            "RevenueFromContractWithCustomerExcludingAssessedTax",
            "Revenues",
            "SalesRevenueNet"
        );
    }

    private String text(JsonNode node, String field) {
        if (node == null) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String firstText(JsonNode array) {
        if (array == null || !array.isArray() || array.isEmpty()) return null;
        String value = array.get(0).asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizeSymbol(String symbol) {
        return symbol.toUpperCase(Locale.ROOT)
            .replace(".", "")
            .replace("-", "")
            .replace("/", "");
    }

    private record CompanyKey(String cik, String ticker, String title) {
    }

}
