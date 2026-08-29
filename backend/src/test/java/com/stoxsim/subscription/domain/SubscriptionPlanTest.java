package com.stoxsim.subscription.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SubscriptionPlanTest {

    @Test
    void keepsStandardCompetitionCapitalEqualAcrossEveryPlan() {
        assertThat(SubscriptionPlan.values())
            .allSatisfy(plan -> assertThat(
                SubscriptionPlan.STANDARD_COMPETITIVE_CAPITAL_INR
            ).isEqualByComparingTo("500000.0000"));
    }

    @Test
    void exposesTheRoadmapSandboxLimits() {
        assertThat(SubscriptionPlan.FREE.maximumSandboxPortfolios()).isZero();
        assertThat(SubscriptionPlan.FREE.sandboxCapitalInr()).isNull();
        assertThat(SubscriptionPlan.PLUS.maximumSandboxPortfolios()).isEqualTo(1);
        assertThat(SubscriptionPlan.PLUS.sandboxCapitalInr())
            .isEqualByComparingTo("2500000.0000");
        assertThat(SubscriptionPlan.PRO.maximumSandboxPortfolios()).isEqualTo(5);
        assertThat(SubscriptionPlan.PRO.sandboxCapitalInr())
            .isEqualByComparingTo("10000000.0000");
    }

    @Test
    void enforcesFeatureTiersAtThePlanBoundary() {
        assertThat(SubscriptionPlan.FREE.includes(
            SubscriptionFeature.SANDBOX_PORTFOLIO
        )).isFalse();
        assertThat(SubscriptionPlan.PLUS.includes(
            SubscriptionFeature.SANDBOX_PORTFOLIO
        )).isTrue();
        assertThat(SubscriptionPlan.PLUS.includes(
            SubscriptionFeature.MULTIPLE_PORTFOLIOS
        )).isFalse();
        assertThat(SubscriptionPlan.PRO.includes(
            SubscriptionFeature.MULTIPLE_PORTFOLIOS
        )).isTrue();
    }
}
