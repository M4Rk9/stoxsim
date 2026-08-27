package com.stoxsim.competition.api;

public record LeagueCreatedResponse(
    LeagueDetailResponse league,
    String inviteCode,
    String inviteNote
) {
}
