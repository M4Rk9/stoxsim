package com.stoxsim.subscription.domain;

public enum SubscriptionFeature {
    SANDBOX_PORTFOLIO(SubscriptionPlan.PLUS),
    EXPANDED_FINWIZ(SubscriptionPlan.PLUS),
    ADVANCED_ANALYTICS(SubscriptionPlan.PLUS),
    MULTIPLE_PORTFOLIOS(SubscriptionPlan.PRO),
    ADVANCED_RISK_ANALYTICS(SubscriptionPlan.PRO),
    SCENARIO_LAB(SubscriptionPlan.PRO),
    PREMIUM_COMPETITIONS(SubscriptionPlan.PRO);

    private final SubscriptionPlan minimumPlan;

    SubscriptionFeature(SubscriptionPlan minimumPlan) {
        this.minimumPlan = minimumPlan;
    }

    public SubscriptionPlan minimumPlan() {
        return minimumPlan;
    }
}
