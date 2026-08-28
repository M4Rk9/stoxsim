package com.stoxsim.subscription.provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.stoxsim.subscription.domain.SubscriptionPlan;
import com.stoxsim.subscription.domain.SubscriptionStatus;

class BillingSubscriptionUpdateTest {

    @Test
    void refusesToTreatAFreePlanAsAProviderPaidEntitlement() {
        assertThatThrownBy(() -> new BillingSubscriptionUpdate(
            UUID.randomUUID(),
            SubscriptionPlan.FREE,
            SubscriptionStatus.ACTIVE,
            "provider",
            "customer",
            "subscription",
            Instant.now()
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("paid plan");
    }

    @Test
    void requiresAllOpaqueProviderReferences() {
        assertThatThrownBy(() -> new BillingSubscriptionUpdate(
            UUID.randomUUID(),
            SubscriptionPlan.PLUS,
            SubscriptionStatus.ACTIVE,
            " ",
            "customer",
            "subscription",
            Instant.now()
        )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("provider");
    }
}
