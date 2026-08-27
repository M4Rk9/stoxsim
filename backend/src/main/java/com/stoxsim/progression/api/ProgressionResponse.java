package com.stoxsim.progression.api;

import java.time.LocalDate;
import java.util.List;

public record ProgressionResponse(
    String version,
    int totalXp,
    int level,
    String levelName,
    int levelFloorXp,
    Integer nextLevelXp,
    int currentStreak,
    int longestStreak,
    LocalDate lastCheckInDate,
    String checkInZoneId,
    boolean checkedInToday,
    List<ChallengeResponse> challenges,
    List<AchievementResponse> achievements,
    String disclaimer
) {
}
