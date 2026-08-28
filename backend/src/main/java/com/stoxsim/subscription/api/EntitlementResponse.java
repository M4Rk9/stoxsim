package com.stoxsim.subscription.api;

import java.math.BigDecimal;

import com.stoxsim.subscription.domain.SubscriptionPlan;

public record EntitlementResponse(
    BigDecimal standardCompetitiveCapitalInr,
    BigDecimal sandboxCapitalInr,
    int maximumSandboxPortfolios,
    String finwizTier,
    String analyticsTier,
    boolean privateLeagues,
    boolean scenarioLab,
    boolean multiplePortfolios,
    boolean premiumCompetitions
) {
    public static EntitlementResponse from(SubscriptionPlan plan) {
        return new EntitlementResponse(
            SubscriptionPlan.STANDARD_COMPETITIVE_CAPITAL_INR,
            plan.sandboxCapitalInr(),
            plan.maximumSandboxPortfolios(),
            plan.finwizTier(),
            plan.analyticsTier(),
            plan.privateLeagues(),
            plan.scenarioLab(),
            plan.multiplePortfolios(),
            plan.premiumCompetitions()
        );
    }
}
