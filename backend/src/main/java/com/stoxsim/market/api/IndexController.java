package com.stoxsim.market.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.service.IndexQuoteService;
import com.stoxsim.market.service.UnitedStatesIndexQuoteService;

@RestController
@RequestMapping("/api/v1/market/indices")
public class IndexController {

    private final IndexQuoteService indiaIndices;
    private final UnitedStatesIndexQuoteService unitedStatesIndices;

    public IndexController(
        IndexQuoteService indiaIndices,
        UnitedStatesIndexQuoteService unitedStatesIndices
    ) {
        this.indiaIndices = indiaIndices;
        this.unitedStatesIndices = unitedStatesIndices;
    }

    @GetMapping
    public List<IndexQuoteResponse> current(
        @RequestParam(defaultValue = "INDIA") MarketRegion marketRegion
    ) {
        return marketRegion == MarketRegion.UNITED_STATES
            ? unitedStatesIndices.current()
            : indiaIndices.current();
    }
}
