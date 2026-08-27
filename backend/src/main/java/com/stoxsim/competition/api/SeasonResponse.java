package com.stoxsim.competition.api;

import java.time.Instant;
import java.math.BigDecimal;

public record SeasonResponse(
    String code,
    String title,
    Instant startsAt,
    Instant endsAt,
    boolean open,
    String scoringVersion,
    String marketRegion,
    String currency,
    BigDecimal standardStartingCapital
) {
}
