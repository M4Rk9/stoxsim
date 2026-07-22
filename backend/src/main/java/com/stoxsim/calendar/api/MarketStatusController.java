package com.stoxsim.calendar.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.calendar.service.IndiaMarketSessionService;
import com.stoxsim.calendar.service.MarketSessionSnapshot;
import com.stoxsim.instrument.domain.MarketExchange;

@RestController
@RequestMapping("/api/v1/market")
public class MarketStatusController {

    private final IndiaMarketSessionService sessions;

    public MarketStatusController(IndiaMarketSessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping("/status")
    public MarketSessionSnapshot status(
        @RequestParam(defaultValue = "NSE") MarketExchange exchange
    ) {
        if (exchange != MarketExchange.NSE && exchange != MarketExchange.BSE) {
            throw new IllegalArgumentException("India market status supports NSE and BSE");
        }
        return sessions.current(exchange);
    }
}
