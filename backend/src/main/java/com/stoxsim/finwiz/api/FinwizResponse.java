package com.stoxsim.finwiz.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record FinwizResponse(
    String answer,
    String provider,
    String model,
    boolean groundedInStoxSimData,
    Instant generatedAt,
    Instant dataAsOf,
    List<String> suggestedQuestions,
    String disclaimer,
    String finwizTier,
    String answerKind,
    String learningReference,
    List<Source> sources,
    AiAllowance aiAllowance
) {
    public FinwizResponse withAllowance(AiAllowance allowance) {
        return new FinwizResponse(answer, provider, model, groundedInStoxSimData, generatedAt, dataAsOf,
            suggestedQuestions, disclaimer, finwizTier, answerKind, learningReference, sources, allowance);
    }

    public record AiAllowance(int dailyLimit, int remaining) { }
    public record Source(String title, String url, int revision, LocalDate reviewedOn) { }
}
