package com.stoxsim.competition.api;

import java.math.BigDecimal;
import java.time.Instant;

public record StandingResponse(
    int rank,
    String displayName,
    BigDecimal returnPercent,
    String dataStatus,
    Instant joinedAt,
    Instant valuedAt,
    boolean currentUser
) {
}
