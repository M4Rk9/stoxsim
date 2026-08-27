package com.stoxsim.progression.api;

import java.time.Instant;

public record MissionResponse(
    String code,
    String title,
    String description,
    int xp,
    int progress,
    int target,
    boolean completed,
    Instant completedAt
) {
}
