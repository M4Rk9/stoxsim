package com.stoxsim.calendar.service;

import java.time.Instant;

public record UnitedStatesMarketClockSnapshot(
    boolean open,
    Instant timestamp,
    Instant nextOpen,
    Instant nextClose
) {}
