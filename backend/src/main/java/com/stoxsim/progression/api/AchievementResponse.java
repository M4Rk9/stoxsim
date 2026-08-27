package com.stoxsim.progression.api;

import java.time.Instant;

public record AchievementResponse(
    String code,
    String title,
    String description,
    boolean unlocked,
    Instant unlockedAt
) {
}
