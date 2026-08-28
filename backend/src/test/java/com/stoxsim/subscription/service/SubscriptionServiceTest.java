package com.stoxsim.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stoxsim.account.domain.AccountKind;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.subscription.domain.SubscriptionPlan;
import com.stoxsim.subscription.domain.SubscriptionStatus;
import com.stoxsim.subscription.domain.UserSubscription;
import com.stoxsim.subscription.provider.BillingSubscriptionUpdate;
import com.stoxsim.subscription.repository.UserSubscriptionRepository;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final UUID USER_ID = UUID.fromString(
        "09e99aab-1db3-4bd0-9022-d37c4e07b311"
    );
    private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

    @Mock private UserSubscriptionRepository subscriptions;
    @Mock private VirtualAccountRepository accounts;

    @Test
    void activePlusUpdateCreatesAnIsolatedNonCompetitiveSandbox() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(USER_ID);
        UserSubscription subscription = new UserSubscription(user);
        when(subscriptions.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(subscription));
        when(subscriptions.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));
        when(accounts.findSandboxesByUserId(USER_ID)).thenReturn(List.of());
        when(accounts.findByUserIdAndAccountKindAndSandboxPlanAndSandboxSlot(
            USER_ID,
            AccountKind.SANDBOX,
            SubscriptionPlan.PLUS,
            1
        )).thenReturn(Optional.empty());
        when(accounts.save(any(VirtualAccount.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service().applyProviderUpdate(update(
            SubscriptionPlan.PLUS,
            SubscriptionStatus.ACTIVE
        ));

        var sandbox = org.mockito.ArgumentCaptor.forClass(VirtualAccount.class);
        verify(accounts).save(sandbox.capture());
        assertThat(sandbox.getValue().getAccountKind()).isEqualTo(AccountKind.SANDBOX);
        assertThat(sandbox.getValue().getStartingCapital())
            .isEqualByComparingTo("2500000.0000");
        assertThat(sandbox.getValue().isLeaderboardEligible()).isFalse();
        assertThat(response.plan()).isEqualTo(SubscriptionPlan.PLUS);
        assertThat(response.billingEnabled()).isFalse();
    }

    @Test
    void nonActiveProviderStateLocksExistingSandboxes() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(USER_ID);
        UserSubscription subscription = new UserSubscription(user);
        VirtualAccount sandbox = VirtualAccount.sandbox(
            user,
            SubscriptionPlan.PLUS,
            1,
            SubscriptionPlan.PLUS.sandboxCapitalInr()
        );
        when(subscriptions.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(subscription));
        when(subscriptions.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));
        when(accounts.findSandboxesByUserId(USER_ID)).thenReturn(List.of(sandbox));

        service().applyProviderUpdate(update(
            SubscriptionPlan.PLUS,
            SubscriptionStatus.PAST_DUE
        ));

        assertThat(sandbox.isActive()).isFalse();
        verify(accounts, never()).save(any());
    }

    private SubscriptionService service() {
        return new SubscriptionService(
            subscriptions,
            accounts,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private BillingSubscriptionUpdate update(
        SubscriptionPlan plan,
        SubscriptionStatus status
    ) {
        return new BillingSubscriptionUpdate(
            USER_ID,
            plan,
            status,
            "test-provider",
            "customer-123",
            "subscription-123",
            NOW.plusSeconds(2_592_000)
        );
    }
}
