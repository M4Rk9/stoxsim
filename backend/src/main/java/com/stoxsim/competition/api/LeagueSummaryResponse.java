package com.stoxsim.competition.api;

import java.time.Instant;
import java.util.UUID;

public record LeagueSummaryResponse(
    UUID id,
    String name,
    String seasonCode,
    String ownerDisplayName,
    boolean owner,
    long memberCount,
    int maxMembers,
    Instant createdAt
) {
}
