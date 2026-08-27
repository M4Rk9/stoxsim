package com.stoxsim.progression.api;

import java.util.List;

public record ChallengeResponse(
    String code,
    String title,
    String description,
    int completedMissions,
    int totalMissions,
    List<MissionResponse> missions
) {
}
