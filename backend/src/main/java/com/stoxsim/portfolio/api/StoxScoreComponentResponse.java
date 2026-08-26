package com.stoxsim.portfolio.api;

public record StoxScoreComponentResponse(
    String key,
    String label,
    int score,
    int weightPercent,
    String explanation
) {
}
