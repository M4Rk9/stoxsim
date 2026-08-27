package com.stoxsim.competition.api;

import java.math.BigDecimal;
import java.util.List;

public record CompetitionBoardResponse(
    SeasonResponse season,
    boolean enrolled,
    long participantCount,
    Integer yourRank,
    BigDecimal yourBaselineValue,
    BigDecimal yourLatestValue,
    List<StandingResponse> standings,
    String comparisonNote,
    String disclaimer
) {
}
