package com.stoxsim.subscription.domain;
import static org.assertj.core.api.Assertions.*;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.subscription.provider.BillingSubscriptionUpdate;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.account.domain.VirtualAccount;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class ScenarioCreditTest {
    final Instant now = Instant.parse("2026-10-07T00:00:00Z");
    final AppUser user = new AppUser("credits@test.local", "hash", "Credits");
    BillingSubscriptionUpdate update(SubscriptionPlan plan, SubscriptionStatus status, Instant end) {
        return new BillingSubscriptionUpdate(UUID.randomUUID(), plan, status, "VERIFIED_TEST", "customer", "subscription", end);
    }
    @Test void freeCreditsAreLifetimeAndPaidRenewalsDoNotStack() {
        var subscription = new UserSubscription(user);
        subscription.consumeScenarioCredit(); subscription.consumeScenarioCredit();
        assertThat(subscription.remainingScenarioCredits()).isZero();
        assertThatThrownBy(subscription::consumeScenarioCredit).hasMessageContaining("No Scenario Lab credits");
        var period = now.plusSeconds(30L*86400);
        var paid = update(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, period);
        subscription.apply(paid, now);
        assertThat(subscription.remainingScenarioCredits()).isEqualTo(10);
        subscription.consumeScenarioCredit(); subscription.apply(paid, now);
        assertThat(subscription.remainingScenarioCredits()).isEqualTo(9);
        subscription.apply(update(SubscriptionPlan.PLUS, SubscriptionStatus.PAST_DUE, period.plusSeconds(30L*86400)), now);
        assertThat(subscription.remainingScenarioCredits()).isZero();
        subscription.apply(paid, now);
        assertThat(subscription.remainingScenarioCredits()).isEqualTo(9);
        subscription.apply(update(SubscriptionPlan.PLUS, SubscriptionStatus.ACTIVE, period.plusSeconds(30L*86400)), now);
        assertThat(subscription.remainingScenarioCredits()).isEqualTo(10);
    }
    @Test void planChangesWithinAPeriodKeepUsageAndGrantCorrectAllowance() {
        var subscription = new UserSubscription(user);
        var end = now.plusSeconds(30L*86400);
        subscription.apply(update(SubscriptionPlan.PLUS,SubscriptionStatus.ACTIVE,end),now);
        subscription.consumeScenarioCredit();
        subscription.apply(update(SubscriptionPlan.PRO,SubscriptionStatus.ACTIVE,end),now);
        assertThat(subscription.remainingScenarioCredits()).isEqualTo(49);
        subscription.apply(update(SubscriptionPlan.PLUS,SubscriptionStatus.ACTIVE,end),now);
        assertThat(subscription.remainingScenarioCredits()).isEqualTo(9);
    }
    @Test void paidCapitalIsCorrectInBothCurrencies() {
        for (var plan : new SubscriptionPlan[] {SubscriptionPlan.PLUS, SubscriptionPlan.PRO}) {
            var india = VirtualAccount.sandbox(user,plan,1,plan.sandboxCapital(MarketRegion.INDIA),null,MarketRegion.INDIA);
            var us = VirtualAccount.sandbox(user,plan,1,plan.sandboxCapital(MarketRegion.UNITED_STATES),null,MarketRegion.UNITED_STATES);
            assertThat(india.getCurrency()).isEqualTo("INR"); assertThat(us.getCurrency()).isEqualTo("USD");
            assertThat(india.getStartingCapital()).isEqualByComparingTo(plan == SubscriptionPlan.PLUS ? "2500000" : "10000000");
            assertThat(us.getStartingCapital()).isEqualByComparingTo(plan == SubscriptionPlan.PLUS ? "50000" : "100000");
            assertThat(us.isLeaderboardEligible()).isFalse();
        }
        for (var plan : SubscriptionPlan.values()) assertThat(plan.includes(SubscriptionFeature.SCENARIO_LAB)).isTrue();
    }
}
