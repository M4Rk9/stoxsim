package com.stoxsim.subscription.api;

import java.math.BigDecimal;

import com.stoxsim.subscription.domain.SubscriptionPlan;

public record PlanResponse(
    SubscriptionPlan plan,
    String displayName,
    BigDecimal monthlyPriceInr,
    EntitlementResponse entitlements
) {
    public static PlanResponse from(SubscriptionPlan plan) {
        return new PlanResponse(
            plan,
            plan.displayName(),
            plan.monthlyPriceInr(),
            EntitlementResponse.from(plan)
        );
    }
}
