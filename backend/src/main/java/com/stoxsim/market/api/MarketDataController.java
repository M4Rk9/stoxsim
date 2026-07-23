package com.stoxsim.market.api;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.market.data.CandleInterval;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.service.MarketDataService;
import com.stoxsim.market.service.StockInsightsService;

@RestController
@RequestMapping("/api/v1/instruments")
public class MarketDataController {

    private static final ZoneId INDIA_TIME = ZoneId.of("Asia/Kolkata");

    private final MarketDataService marketData;
    private final StockInsightsService stockInsights;

    public MarketDataController(
        MarketDataService marketData,
        StockInsightsService stockInsights
    ) {
        this.marketData = marketData;
        this.stockInsights = stockInsights;
    }

    @GetMapping("/{marketRegion}/{exchange}/{symbol}/quote")
    public QuoteResponse quote(
        @PathVariable MarketRegion marketRegion,
        @PathVariable MarketExchange exchange,
        @PathVariable String symbol
    ) {
        return marketData.getQuote(marketRegion, exchange, symbol);
    }

    @GetMapping("/{marketRegion}/{exchange}/{symbol}/candles")
    public CandleSeriesResponse candles(
        @PathVariable MarketRegion marketRegion,
        @PathVariable MarketExchange exchange,
        @PathVariable String symbol,
        @RequestParam(defaultValue = "ONE_DAY") CandleInterval interval,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate from,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate to
    ) {
        LocalDate resolvedTo = to == null ? LocalDate.now(INDIA_TIME) : to;
        LocalDate resolvedFrom = from == null ? resolvedTo.minusMonths(1) : from;
        return marketData.getCandles(
            marketRegion,
            exchange,
            symbol,
            interval,
            resolvedFrom,
            resolvedTo
        );
    }

    @GetMapping("/{marketRegion}/{exchange}/{symbol}/insights")
    public StockInsightsResponse insights(
        @PathVariable MarketRegion marketRegion,
        @PathVariable MarketExchange exchange,
        @PathVariable String symbol,
        @RequestParam(defaultValue = "quarterly") String timePeriod
    ) {
        return stockInsights.get(
            marketRegion,
            exchange,
            symbol,
            timePeriod
        );
    }
}
