package com.stoxsim.finwiz.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.finwiz.api.FinwizRequest;
import com.stoxsim.market.api.CandleSeriesResponse;
import com.stoxsim.market.api.QuoteResponse;
import com.stoxsim.market.api.StockInsightsResponse;
import com.stoxsim.market.data.Candle;
import com.stoxsim.market.data.CandleInterval;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.service.MarketDataService;
import com.stoxsim.market.service.StockInsightsService;

@Service
public class FinwizContextService {

    private static final MathContext MATH = MathContext.DECIMAL64;

    private final MarketDataService marketData;
    private final StockInsightsService stockInsights;

    public FinwizContextService(
        MarketDataService marketData,
        StockInsightsService stockInsights
    ) {
        this.marketData = marketData;
        this.stockInsights = stockInsights;
    }

    public ContextSnapshot build(FinwizRequest request) {
        if (!request.requestsStockContext()) {
            return ContextSnapshot.empty();
        }
        if (request.marketRegion() == null || request.exchange() == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "marketRegion and exchange are required when a stock symbol is supplied"
            );
        }

        String symbol = request.symbol().trim().toUpperCase();
        QuoteResponse quote = safelyQuote(request, symbol);
        CandleSeriesResponse candles = safelyCandles(request, symbol);
        StockInsightsResponse insights = safelyInsights(request, symbol);

        boolean grounded = quote != null || candles != null || insights != null;
        if (!grounded) {
            return new ContextSnapshot(
                false,
                symbol,
                null,
                "No verified StoxSim market data was available for this symbol. Explain only general concepts and clearly state that company-specific analysis is unavailable."
            );
        }

        StringBuilder context = new StringBuilder(2048);
        context.append("Verified StoxSim context for ")
            .append(symbol)
            .append(" (region=")
            .append(request.marketRegion())
            .append(", exchange=")
            .append(request.exchange())
            .append(").\n");

        Instant dataAsOf = null;
        if (quote != null) {
            context.append("Quote: currency=").append(quote.currency())
                .append(", lastPrice=").append(value(quote.lastPrice()))
                .append(", previousClose=").append(value(quote.previousClose()))
                .append(", open=").append(value(quote.open()))
                .append(", high=").append(value(quote.high()))
                .append(", low=").append(value(quote.low()))
                .append(", volume=").append(quote.volume())
                .append(", status=").append(quote.dataStatus())
                .append(".\n");
            dataAsOf = quote.exchangeTimestamp() == null
                ? quote.receivedAt()
                : quote.exchangeTimestamp();
        }

        if (candles != null && !candles.candles().isEmpty()) {
            TechnicalSummary technical = technicalSummary(candles.candles());
            context.append("Technical summary from daily adjusted candles: ")
                .append("latestClose=").append(value(technical.latestClose()))
                .append(", return20SessionsPercent=").append(value(technical.return20SessionsPercent()))
                .append(", sma20=").append(value(technical.sma20()))
                .append(", sma50=").append(value(technical.sma50()))
                .append(", rsi14=").append(value(technical.rsi14()))
                .append(", high20=").append(value(technical.high20()))
                .append(", low20=").append(value(technical.low20()))
                .append(". These indicators describe historical price behaviour and are not predictions.\n");
            Instant candleAsOf = candles.candles().stream()
                .map(Candle::timestamp)
                .max(Comparator.naturalOrder())
                .orElse(null);
            dataAsOf = later(dataAsOf, candleAsOf);
        }

        if (insights != null) {
            context.append("Fundamentals status=").append(insights.status()).append(".\n");
            if (insights.profile() != null) {
                context.append("Company profile: sector=")
                    .append(insights.profile().sector())
                    .append(", description=")
                    .append(shorten(insights.profile().description(), 700))
                    .append(".\n");
            }
            if (insights.ratios() != null && !insights.ratios().isEmpty()) {
                context.append("Fundamental ratios:\n");
                insights.ratios().stream().limit(12).forEach(ratio -> context
                    .append("- ").append(ratio.name())
                    .append(": company=").append(ratio.companyValue())
                    .append(", sector=").append(ratio.sectorValue())
                    .append("\n"));
            }
            if (insights.financials() != null && insights.financials().metrics() != null) {
                context.append("Financial statement metrics (latest reported values):\n");
                insights.financials().metrics().stream().limit(10).forEach(metric -> {
                    context.append("- ").append(metric.category()).append(": ");
                    metric.history().stream().limit(4).forEach(point -> context
                        .append(point.period()).append("=").append(point.value())
                        .append(point.change() == null ? "" : " (change " + point.change() + ")")
                        .append("; "));
                    context.append("\n");
                });
            }
            dataAsOf = later(dataAsOf, insights.asOf());
        }

        return new ContextSnapshot(true, symbol, dataAsOf, context.toString());
    }

    private QuoteResponse safelyQuote(FinwizRequest request, String symbol) {
        try {
            return marketData.getQuote(
                request.marketRegion(),
                request.exchange(),
                symbol
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private CandleSeriesResponse safelyCandles(FinwizRequest request, String symbol) {
        try {
            ZoneId zone = request.marketRegion() == MarketRegion.INDIA
                ? ZoneId.of("Asia/Kolkata")
                : ZoneId.of("America/New_York");
            LocalDate to = LocalDate.now(zone);
            return marketData.getCandles(
                request.marketRegion(),
                request.exchange(),
                symbol,
                CandleInterval.ONE_DAY,
                to.minusMonths(8),
                to
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private StockInsightsResponse safelyInsights(FinwizRequest request, String symbol) {
        try {
            return stockInsights.get(
                request.marketRegion(),
                request.exchange(),
                symbol,
                "quarterly"
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private TechnicalSummary technicalSummary(List<Candle> source) {
        List<Candle> candles = new ArrayList<>(source);
        candles.sort(Comparator.comparing(Candle::timestamp));
        List<BigDecimal> closes = candles.stream()
            .map(Candle::close)
            .filter(value -> value != null && value.signum() > 0)
            .toList();
        if (closes.isEmpty()) {
            return TechnicalSummary.empty();
        }

        BigDecimal latest = closes.getLast();
        BigDecimal baseline = closes.get(Math.max(0, closes.size() - 21));
        BigDecimal return20 = baseline.signum() == 0
            ? null
            : latest.subtract(baseline)
                .multiply(BigDecimal.valueOf(100), MATH)
                .divide(baseline, 4, RoundingMode.HALF_UP);
        List<BigDecimal> last20 = tail(closes, 20);
        return new TechnicalSummary(
            latest,
            return20,
            average(last20),
            average(tail(closes, 50)),
            rsi(closes, 14),
            last20.stream().max(Comparator.naturalOrder()).orElse(null),
            last20.stream().min(Comparator.naturalOrder()).orElse(null)
        );
    }

    private List<BigDecimal> tail(List<BigDecimal> values, int count) {
        return values.subList(Math.max(0, values.size() - count), values.size());
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) return null;
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal rsi(List<BigDecimal> closes, int period) {
        if (closes.size() <= period) return null;
        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;
        for (int index = closes.size() - period; index < closes.size(); index++) {
            BigDecimal change = closes.get(index).subtract(closes.get(index - 1));
            if (change.signum() >= 0) {
                gains = gains.add(change);
            } else {
                losses = losses.add(change.abs());
            }
        }
        if (losses.signum() == 0) return BigDecimal.valueOf(100);
        BigDecimal relativeStrength = gains.divide(losses, 8, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(100).subtract(
            BigDecimal.valueOf(100).divide(
                BigDecimal.ONE.add(relativeStrength),
                4,
                RoundingMode.HALF_UP
            )
        );
    }

    private String value(Object value) {
        return value == null ? "unavailable" : value.toString();
    }

    private String shorten(String value, int limit) {
        if (value == null || value.isBlank()) return "unavailable";
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= limit
            ? normalized
            : normalized.substring(0, limit) + "…";
    }

    private Instant later(Instant first, Instant second) {
        if (first == null) return second;
        if (second == null) return first;
        return first.isAfter(second) ? first : second;
    }

    public record ContextSnapshot(
        boolean grounded,
        String symbol,
        Instant dataAsOf,
        String text
    ) {
        static ContextSnapshot empty() {
            return new ContextSnapshot(false, null, null, "No company-specific market data was requested.");
        }
    }

    private record TechnicalSummary(
        BigDecimal latestClose,
        BigDecimal return20SessionsPercent,
        BigDecimal sma20,
        BigDecimal sma50,
        BigDecimal rsi14,
        BigDecimal high20,
        BigDecimal low20
    ) {
        static TechnicalSummary empty() {
            return new TechnicalSummary(null, null, null, null, null, null, null);
        }
    }
}
