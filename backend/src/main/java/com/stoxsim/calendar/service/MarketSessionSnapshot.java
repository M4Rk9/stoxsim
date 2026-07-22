package com.stoxsim.calendar.service;

import java.time.LocalDate;
import java.time.ZonedDateTime;

import com.stoxsim.calendar.domain.MarketPhase;
import com.stoxsim.instrument.domain.MarketExchange;

public record MarketSessionSnapshot(
    MarketExchange exchange,
    MarketPhase phase,
    String timezone,
    ZonedDateTime currentTime,
    ZonedDateTime nextTransition,
    LocalDate orderDate
) {
    public boolean allowsOrderEntry() {
        return phase != MarketPhase.PRE_OPEN_MATCHING
            && phase != MarketPhase.PRE_OPEN_BUFFER;
    }

    public boolean executable() {
        return phase == MarketPhase.REGULAR;
    }
}
