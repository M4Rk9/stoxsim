package com.stoxsim.market.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.market.service.MarketMoverService;

@RestController
@RequestMapping("/api/v1/market/movers")
public class MarketMoversController {

    private final MarketMoverService movers;

    public MarketMoversController(MarketMoverService movers) {
        this.movers = movers;
    }

    @GetMapping
    public MarketMoversResponse current() {
        return movers.current();
    }
}
