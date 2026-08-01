package com.stoxsim.finwiz.api;

import java.time.Instant;
import java.util.List;

public record FinwizResponse(
    String answer,
    String provider,
    String model,
    boolean groundedInStoxSimData,
    Instant generatedAt,
    Instant dataAsOf,
    List<String> suggestedQuestions,
    String disclaimer
) {
}
