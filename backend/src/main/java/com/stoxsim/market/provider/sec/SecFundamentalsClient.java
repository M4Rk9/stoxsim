package com.stoxsim.market.provider.sec;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private static final Set<String> QUARTERLY_FORMS = Set.of("10-Q", "10-Q/A");
    private static final Set<String> YEARLY_FORMS = Set.of("10-K", "10-K/A", "20-F", "20-F/A", "40-F", "40-F/A");

    private final RestClient secData;
    private final RestClient secFiles;
    private volatile Map<String, CompanyKey> tickerIndex = Map.of();
    private volatile Instant tickerIndexAt = Instant.EPOCH;

    public SecFundamentalsClient(
        @Value("${stoxsim.market-data.sec.user-agent:StoxSim/1.0 contact@stoxsim.com}") String userAgent
    ) {
        this.secData = RestClient.builder()
            .baseUrl("https://data.sec.gov")
            .defaultHeader("User-Agent", userAgent)
            .defaultHeader("Accept-Encoding", "gzip, deflate")
            .build();
        this.secFiles = RestClient.builder()
            .baseUrl("https://www.sec.gov")
            .defaultHeader("User-Agent", userAgent)
            .defaultHeader("Accept-Encoding", "gzip, deflate")
            .build();
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

    private List<FundamentalRatio> ratios(JsonNode facts, BigDecimal lastPrice) {
        BigDecimal eps = latestValue(facts, "us-gaap", List.of("EarningsPerShareDiluted", "EarningsPerShareBasic"), "USD/shares");
        BigDecimal revenue = latestValue(facts, "us-gaap", revenueTags(), "USD");
        BigDecimal netIncome = latestValue(facts, "us-gaap", List.of("NetIncomeLoss", "ProfitLoss"), "USD");
        BigDecimal operatingIncome = latestValue(facts, "us-gaap", List.of("OperatingIncomeLoss"), "USD");
        BigDecimal operatingCashFlow = latestValue(facts, "us-gaap", List.of("NetCashProvidedByUsedInOperatingActivities"), "USD");
        BigDecimal assets = latestValue(facts, "us-gaap", List.of("Assets"), "USD");
        BigDecimal liabilities = latestValue(facts, "us-gaap", List.of("Liabilities"), "USD");

        List<FundamentalRatio> ratios = new ArrayList<>();
        if (lastPrice != null && eps != null && eps.signum() > 0) {
            ratios.add(ratio("P/E (trailing filing EPS)", lastPrice.divide(eps, 2, RoundingMode.HALF_UP)));
        }
        if (eps != null) ratios.add(ratio("Diluted EPS", eps.setScale(2, RoundingMode.HALF_UP)));
        addPercentRatio(ratios, "Net margin", netIncome, revenue);
        addPercentRatio(ratios, "Operating margin", operatingIncome, revenue);
        addPercentRatio(ratios, "Operating cash-flow margin", operatingCashFlow, revenue);
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

    private FinancialPerformance financials(JsonNode facts, String requestedPeriod) {
        boolean yearly = "yearly".equalsIgnoreCase(requestedPeriod);
        List<FactPoint> revenue = history(facts, "us-gaap", revenueTags(), "USD", yearly);
        List<FactPoint> netIncome = history(facts, "us-gaap", List.of("NetIncomeLoss", "ProfitLoss"), "USD", yearly);
        List<FactPoint> operatingIncome = history(facts, "us-gaap", List.of("OperatingIncomeLoss"), "USD", yearly);
        List<FactPoint> operatingCashFlow = history(facts, "us-gaap", List.of("NetCashProvidedByUsedInOperatingActivities"), "USD", yearly);
        List<FactPoint> capex = history(facts, "us-gaap", List.of(
            "PaymentsToAcquirePropertyPlantAndEquipment",
            "PaymentsForAdditionsToPropertyPlantAndEquipment"
        ), "USD", yearly);

        List<FinancialMetric> metrics = new ArrayList<>();
        addMetric(metrics, "Revenue", revenue);
        addMetric(metrics, "Net income", netIncome);
        addMetric(metrics, "Operating income", operatingIncome);
        addMetric(metrics, "Operating cash flow", operatingCashFlow);
        List<FactPoint> freeCashFlow = subtractByPeriod(operatingCashFlow, capex);
        addMetric(metrics, "Free cash flow", freeCashFlow);
        if (metrics.isEmpty()) return null;
        return new FinancialPerformance(
            "SEC XBRL financial statements",
            yearly ? "yearly" : "quarterly",
            "USD million",
            List.copyOf(metrics)
        );
    }

    private void addMetric(List<FinancialMetric> target, String category, List<FactPoint> points) {
        if (points.isEmpty()) return;
        List<FinancialHistoryPoint> history = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            FactPoint point = points.get(index);
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

    private List<FactPoint> subtractByPeriod(List<FactPoint> left, List<FactPoint> right) {
        if (left.isEmpty() || right.isEmpty()) return List.of();
        Map<LocalDate, BigDecimal> rightByEnd = new LinkedHashMap<>();
        right.forEach(point -> rightByEnd.put(point.end(), point.value()));
        return left.stream()
            .filter(point -> rightByEnd.containsKey(point.end()))
            .map(point -> new FactPoint(
                point.end(),
                point.value().subtract(rightByEnd.get(point.end())),
                point.filed()
            ))
            .toList();
    }

    private BigDecimal latestValue(
        JsonNode facts,
        String taxonomy,
        List<String> tags,
        String unit
    ) {
        List<FactPoint> points = rawPoints(facts, taxonomy, tags, unit);
        return points.stream()
            .max(Comparator.comparing(FactPoint::end).thenComparing(FactPoint::filed))
            .map(FactPoint::value)
            .orElse(null);
    }

    private List<FactPoint> history(
        JsonNode facts,
        String taxonomy,
        List<String> tags,
        String unit,
        boolean yearly
    ) {
        Set<String> forms = yearly ? YEARLY_FORMS : QUARTERLY_FORMS;
        Map<LocalDate, FactPoint> byEnd = new LinkedHashMap<>();
        for (FactPoint point : rawPoints(facts, taxonomy, tags, unit, forms, yearly)) {
            FactPoint existing = byEnd.get(point.end());
            if (existing == null || point.filed().isAfter(existing.filed())) {
                byEnd.put(point.end(), point);
            }
        }
        return byEnd.values().stream()
            .sorted(Comparator.comparing(FactPoint::end).reversed())
            .limit(5)
            .toList();
    }

    private List<FactPoint> rawPoints(
        JsonNode facts,
        String taxonomy,
        List<String> tags,
        String unit
    ) {
        return rawPoints(facts, taxonomy, tags, unit, Set.of(), false);
    }

    private List<FactPoint> rawPoints(
        JsonNode facts,
        String taxonomy,
        List<String> tags,
        String unit,
        Set<String> forms,
        boolean yearly
    ) {
        JsonNode taxonomyNode = facts == null ? null : facts.path("facts").path(taxonomy);
        if (taxonomyNode == null || taxonomyNode.isMissingNode()) return List.of();

        for (String tag : tags) {
            JsonNode values = taxonomyNode.path(tag).path("units").path(unit);
            if (!values.isArray()) continue;
            List<FactPoint> points = new ArrayList<>();
            for (JsonNode value : values) {
                String endText = text(value, "end");
                BigDecimal amount = decimal(value.get("val"));
                String form = text(value, "form");
                if (endText == null || amount == null) continue;
                if (!forms.isEmpty() && (form == null || !forms.contains(form))) continue;
                LocalDate end;
                try {
                    end = LocalDate.parse(endText);
                } catch (RuntimeException ignored) {
                    continue;
                }
                String startText = text(value, "start");
                if (!forms.isEmpty() && startText != null) {
                    try {
                        long days = ChronoUnit.DAYS.between(LocalDate.parse(startText), end);
                        if (yearly && (days < 300 || days > 430)) continue;
                        if (!yearly && (days < 55 || days > 130)) continue;
                    } catch (RuntimeException ignored) {
                        continue;
                    }
                }
                Instant filed = parseFiled(text(value, "filed"));
                points.add(new FactPoint(end, amount, filed));
            }
            if (!points.isEmpty()) return List.copyOf(points);
        }
        return List.of();
    }

    private Instant parseFiled(String value) {
        if (value == null) return Instant.EPOCH;
        try {
            return LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }

    private List<String> revenueTags() {
        return List.of(
            "RevenueFromContractWithCustomerExcludingAssessedTax",
            "Revenues",
            "SalesRevenueNet"
        );
    }

    private BigDecimal decimal(JsonNode value) {
        if (value == null || value.isNull() || !value.isNumber()) return null;
        try {
            return value.decimalValue();
        } catch (RuntimeException ignored) {
            return null;
        }
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

    private record FactPoint(LocalDate end, BigDecimal value, Instant filed) {
    }
}
