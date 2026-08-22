package com.stoxsim.calendar.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.calendar.service.IndiaMarketSessionService;
import com.stoxsim.calendar.service.MarketSessionSnapshot;
import com.stoxsim.calendar.service.UnitedStatesMarketClockService;
import com.stoxsim.calendar.service.UnitedStatesMarketClockSnapshot;
import com.stoxsim.instrument.domain.MarketExchange;

@RestController
@RequestMapping("/api/v1/market")
public class MarketStatusController {

    private final IndiaMarketSessionService sessions;
    private final UnitedStatesMarketClockService unitedStatesClock;

    public MarketStatusController(
        IndiaMarketSessionService sessions,
        UnitedStatesMarketClockService unitedStatesClock
    ) {
        this.sessions = sessions;
        this.unitedStatesClock = unitedStatesClock;
    }

    @GetMapping("/status")
    public MarketSessionSnapshot status(
        @RequestParam(defaultValue = "NSE") MarketExchange exchange
    ) {
        return sessions.current(exchange);
    }

    @GetMapping("/status/united-states/authoritative")
    public UnitedStatesMarketClockSnapshot authoritativeUnitedStatesStatus() {
        return unitedStatesClock.current();
    }
}
