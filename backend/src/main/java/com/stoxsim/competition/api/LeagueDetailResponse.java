package com.stoxsim.competition.api;

import java.util.List;

public record LeagueDetailResponse(
    LeagueSummaryResponse league,
    SeasonResponse season,
    List<StandingResponse> standings,
    String comparisonNote,
    String disclaimer
) {
}
