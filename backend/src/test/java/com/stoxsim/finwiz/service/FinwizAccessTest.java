package com.stoxsim.finwiz.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.subscription.domain.*;
import com.stoxsim.subscription.provider.BillingSubscriptionUpdate;
import com.stoxsim.subscription.repository.UserSubscriptionRepository;

class FinwizAccessTest {
    private final UUID id = UUID.randomUUID();
    private final Instant now = Instant.now();
    private final UserSubscriptionRepository subscriptions = mock(UserSubscriptionRepository.class);
    private final AppUser user = mock(AppUser.class);
    private final FinwizAccess access = new FinwizAccess(subscriptions, Clock.fixed(now, ZoneOffset.UTC));

    private UserSubscription subscription(SubscriptionPlan plan, SubscriptionStatus status, Instant end, String provider) {
        var subscription = new UserSubscription(user);
        if (plan != SubscriptionPlan.FREE) subscription.apply(new BillingSubscriptionUpdate(id, plan, status, provider, "customer", "subscription", end), now);
        when(subscriptions.findByUserId(id)).thenReturn(Optional.of(subscription));
        return subscription;
    }

    @Test void resolvesAllPlansAndRechecksDowngradesOnEveryQuestion() {
        for (var plan : SubscriptionPlan.values()) {
            var sub = subscription(plan, SubscriptionStatus.ACTIVE, now.plusSeconds(3600), "VERIFIED_PROVIDER");
            assertThat(access.depth(id)).isEqualTo(FinwizDepth.from(plan));
            sub.apply(new BillingSubscriptionUpdate(id, SubscriptionPlan.PRO, SubscriptionStatus.PAST_DUE, "VERIFIED_PROVIDER", "customer", "subscription", now.plusSeconds(3600)), now);
            assertThat(access.depth(id)).isEqualTo(FinwizDepth.BASIC);
        }
    }
    @Test void expiredMissingAndInactivePaidPeriodsReceiveBasic() {
        for (Instant end : Arrays.asList(null, now.minusSeconds(1), now)) {
            subscription(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, end, "VERIFIED_PROVIDER");
            assertThat(access.depth(id)).isEqualTo(FinwizDepth.BASIC);
        }
        for (var status : SubscriptionStatus.values()) if (status != SubscriptionStatus.ACTIVE) {
            subscription(SubscriptionPlan.PRO, status, now.plusSeconds(3600), "VERIFIED_PROVIDER");
            assertThat(access.depth(id)).isEqualTo(FinwizDepth.BASIC);
        }
    }
    @Test void testBenefitsRequireUnexpiredVerifiedAdministratorAccess() {
        var sub = subscription(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE, now.plusSeconds(3600), "RAZORPAY_TEST");
        sub.setTestAccessUntil(now.plusSeconds(3600));
        assertThat(access.depth(id)).isEqualTo(FinwizDepth.BASIC);
        when(user.isPlatformAdmin()).thenReturn(true); when(user.isEmailVerified()).thenReturn(true);
        assertThat(access.depth(id)).isEqualTo(FinwizDepth.FULL);
        sub.setTestAccessUntil(now.minusSeconds(1));
        assertThat(access.depth(id)).isEqualTo(FinwizDepth.BASIC);
    }
    @Test void missingSubscriptionCannotAcquirePaidDepth() {
        when(subscriptions.findByUserId(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> access.depth(id)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
