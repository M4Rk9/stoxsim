package com.stoxsim.market.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.market.service.MarketMoverService;
import com.stoxsim.market.service.UnitedStatesMarketMoverService;

@RestController
@RequestMapping("/api/v1/market/movers")
public class MarketMoversController {

    private final MarketMoverService indiaMovers;
    private final UnitedStatesMarketMoverService unitedStatesMovers;

    public MarketMoversController(
        MarketMoverService indiaMovers,
        UnitedStatesMarketMoverService unitedStatesMovers
    ) {
        this.indiaMovers = indiaMovers;
        this.unitedStatesMovers = unitedStatesMovers;
    }

    @GetMapping
    public MarketMoversResponse current(
        @RequestParam(defaultValue = "INDIA") MarketRegion marketRegion
    ) {
        return marketRegion == MarketRegion.UNITED_STATES
            ? unitedStatesMovers.current()
            : indiaMovers.current();
    }
}
