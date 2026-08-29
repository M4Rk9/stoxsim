package com.stoxsim.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.web.server.ResponseStatusException;

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

    @Test
    void activeProUpdateReactivatesEveryProSandbox() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(USER_ID);
        UserSubscription subscription = new UserSubscription(user);
        VirtualAccount first = VirtualAccount.sandbox(
            user, SubscriptionPlan.PRO, 1, SubscriptionPlan.PRO.sandboxCapitalInr()
        );
        VirtualAccount second = VirtualAccount.sandbox(
            user, SubscriptionPlan.PRO, 2, SubscriptionPlan.PRO.sandboxCapitalInr(), "second"
        );
        first.deactivate();
        second.deactivate();
        when(subscriptions.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(subscription));
        when(subscriptions.findByUserId(USER_ID)).thenReturn(Optional.of(subscription));
        when(accounts.findSandboxesByUserId(USER_ID)).thenReturn(List.of(first, second));
        when(accounts.findByUserIdAndAccountKindAndSandboxPlanAndSandboxSlot(
            USER_ID, AccountKind.SANDBOX, SubscriptionPlan.PRO, 1
        )).thenReturn(Optional.of(first));

        service().applyProviderUpdate(update(SubscriptionPlan.PRO, SubscriptionStatus.ACTIVE));

        assertThat(first.isActive()).isTrue();
        assertThat(second.isActive()).isTrue();
    }

    @Test
    void activeProCreatesTheNextSandboxWithServerOwnedCapital() {
        AppUser user = mock(AppUser.class);
        UserSubscription subscription = activeSubscription(user, SubscriptionPlan.PRO);
        VirtualAccount primary = VirtualAccount.sandbox(
            user, SubscriptionPlan.PRO, 1, SubscriptionPlan.PRO.sandboxCapitalInr()
        );
        when(subscriptions.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(subscription));
        when(accounts.findByUserIdAndProvisioningKey(USER_ID, "request-123"))
            .thenReturn(Optional.empty());
        when(accounts.findSandboxesByUserIdAndPlan(USER_ID, SubscriptionPlan.PRO))
            .thenReturn(List.of(primary));
        when(accounts.save(any(VirtualAccount.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var created = service().createAdditionalSandbox(USER_ID, " request-123 ");

        assertThat(created.sandboxPlan()).isEqualTo(SubscriptionPlan.PRO);
        assertThat(created.sandboxSlot()).isEqualTo(2);
        assertThat(created.startingCapital()).isEqualByComparingTo("10000000.0000");
        assertThat(created.leaderboardEligible()).isFalse();
        var saved = org.mockito.ArgumentCaptor.forClass(VirtualAccount.class);
        verify(accounts).save(saved.capture());
        assertThat(saved.getValue().getProvisioningKey()).isEqualTo("request-123");
    }

    @Test
    void repeatedProvisioningKeyReturnsTheOriginalSandbox() {
        AppUser user = mock(AppUser.class);
        UserSubscription subscription = activeSubscription(user, SubscriptionPlan.PRO);
        VirtualAccount existing = VirtualAccount.sandbox(
            user, SubscriptionPlan.PRO, 2, SubscriptionPlan.PRO.sandboxCapitalInr(), "same-request"
        );
        when(subscriptions.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(subscription));
        when(accounts.findByUserIdAndProvisioningKey(USER_ID, "same-request"))
            .thenReturn(Optional.of(existing));

        var response = service().createAdditionalSandbox(USER_ID, "same-request");

        assertThat(response.sandboxSlot()).isEqualTo(2);
        verify(accounts, never()).save(any());
    }

    @Test
    void freePlanCannotCreateAdditionalSandboxes() {
        AppUser user = mock(AppUser.class);
        UserSubscription free = new UserSubscription(user);
        when(subscriptions.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(free));

        assertThatThrownBy(() -> service().createAdditionalSandbox(USER_ID, "request"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("active Pro subscription");

        verify(accounts, never()).save(any());
    }

    @Test
    void inactiveProCannotCreateAdditionalSandboxes() {
        AppUser user = mock(AppUser.class);
        UserSubscription subscription = new UserSubscription(user);
        subscription.apply(update(
            SubscriptionPlan.PRO,
            SubscriptionStatus.PAST_DUE
        ), NOW);
        when(subscriptions.findByUserIdForUpdate(USER_ID))
            .thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service().createAdditionalSandbox(USER_ID, "request"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("active Pro subscription");
    }

    @Test
    void proCannotExceedFiveSandboxPortfolios() {
        AppUser user = mock(AppUser.class);
        UserSubscription subscription = activeSubscription(user, SubscriptionPlan.PRO);
        List<VirtualAccount> full = java.util.stream.IntStream.rangeClosed(1, 5)
            .mapToObj(slot -> VirtualAccount.sandbox(
                user,
                SubscriptionPlan.PRO,
                slot,
                SubscriptionPlan.PRO.sandboxCapitalInr()
            ))
            .toList();
        when(subscriptions.findByUserIdForUpdate(USER_ID))
            .thenReturn(Optional.of(subscription));
        when(accounts.findByUserIdAndProvisioningKey(USER_ID, "sixth-request"))
            .thenReturn(Optional.empty());
        when(accounts.findSandboxesByUserIdAndPlan(USER_ID, SubscriptionPlan.PRO))
            .thenReturn(full);

        assertThatThrownBy(() -> service().createAdditionalSandbox(
            USER_ID,
            "sixth-request"
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("limit has been reached");

        verify(accounts, never()).save(any());
    }

    private UserSubscription activeSubscription(AppUser user, SubscriptionPlan plan) {
        UserSubscription subscription = new UserSubscription(user);
        subscription.apply(update(plan, SubscriptionStatus.ACTIVE), NOW);
        return subscription;
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
