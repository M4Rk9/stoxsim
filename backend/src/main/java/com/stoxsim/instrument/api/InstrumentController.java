package com.stoxsim.instrument.api;

import java.util.List;
import java.util.Locale;
import com.stoxsim.instrument.domain.InstrumentType;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.market.domain.MarketRegion;

@RestController
@RequestMapping("/api/v1/instruments")
public class InstrumentController {

    private final TradableInstrumentRepository repository;

    public InstrumentController(TradableInstrumentRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/search")
    public List<InstrumentResponse> search(
        @RequestParam MarketRegion marketRegion,
        @RequestParam String q
    ) {
        String query = q.trim();
        if (query.length() < 2 || query.length() > 160) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Search query must contain 2 to 160 characters"
            );
        }
        // RIL is the common company abbreviation, not its NSE trading symbol.
        if (marketRegion == MarketRegion.INDIA && query.equalsIgnoreCase("RIL")) query = "RELIANCE";
        String compact = query.toLowerCase(Locale.ROOT).replaceAll("[\\s&-]+", "");
        if (compact.isEmpty()) return List.of();
        var exchanges = marketRegion == MarketRegion.INDIA ? List.of(MarketExchange.NSE)
            : List.of(MarketExchange.NASDAQ, MarketExchange.NYSE, MarketExchange.NYSE_ARCA, MarketExchange.AMEX, MarketExchange.CBOE);
        return repository.search(marketRegion, query, compact, exchanges,
            List.of(InstrumentType.EQUITY, InstrumentType.ETF), PageRequest.of(0, 8))
            .stream()
            .map(InstrumentResponse::from)
            .toList();
    }

    @GetMapping("/{marketRegion}/{exchange}/{symbol}")
    public InstrumentResponse get(
        @PathVariable MarketRegion marketRegion,
        @PathVariable MarketExchange exchange,
        @PathVariable String symbol
    ) {
        return repository
            .findByMarketRegionAndExchangeAndTradingSymbolIgnoreCaseAndActiveTrue(
                marketRegion,
                exchange,
                symbol
            )
            .map(InstrumentResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
