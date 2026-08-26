package com.stoxsim.onboarding.api;

import java.time.Instant;

import com.stoxsim.auth.domain.AppUser;

public record OnboardingStateResponse(
    boolean introductionCompleted,
    boolean firstOrderCompleted,
    boolean dismissed,
    boolean completed,
    String nextStep,
    Instant introductionCompletedAt,
    Instant firstOrderPlacedAt,
    Instant dismissedAt
) {
    public static OnboardingStateResponse from(AppUser user) {
        boolean introductionCompleted = user.getOnboardingIntroCompletedAt() != null;
        boolean firstOrderCompleted = user.getFirstOrderPlacedAt() != null;
        boolean dismissed = user.getOnboardingDismissedAt() != null;
        boolean completed = introductionCompleted && firstOrderCompleted;
        return new OnboardingStateResponse(
            introductionCompleted,
            firstOrderCompleted,
            dismissed,
            completed,
            nextStep(introductionCompleted, firstOrderCompleted, dismissed),
            user.getOnboardingIntroCompletedAt(),
            user.getFirstOrderPlacedAt(),
            user.getOnboardingDismissedAt()
        );
    }

    private static String nextStep(
        boolean introductionCompleted,
        boolean firstOrderCompleted,
        boolean dismissed
    ) {
        if (dismissed) {
            return "DISMISSED";
        }
        if (!introductionCompleted) {
            return "INTRODUCTION";
        }
        if (!firstOrderCompleted) {
            return "FIRST_TRADE";
        }
        return "COMPLETE";
    }
}
