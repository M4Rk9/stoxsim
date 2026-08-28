package com.stoxsim.subscription.service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.stoxsim.account.domain.AccountKind;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.auth.api.dto.AccountResponse;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.subscription.api.SubscriptionResponse;
import com.stoxsim.subscription.domain.SubscriptionPlan;
import com.stoxsim.subscription.domain.SubscriptionStatus;
import com.stoxsim.subscription.domain.UserSubscription;
import com.stoxsim.subscription.provider.BillingSubscriptionUpdate;
import com.stoxsim.subscription.repository.UserSubscriptionRepository;

@Service
public class SubscriptionService {

    private final UserSubscriptionRepository subscriptions;
    private final VirtualAccountRepository accounts;
    private final Clock clock;

    public SubscriptionService(
        UserSubscriptionRepository subscriptions,
        VirtualAccountRepository accounts,
        Clock clock
    ) {
        this.subscriptions = subscriptions;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional
    public UserSubscription initializeFree(AppUser user) {
        return subscriptions.save(new UserSubscription(user));
    }

    @Transactional(readOnly = true)
    public SubscriptionResponse current(UUID userId) {
        UserSubscription subscription = subscriptions.findByUserId(userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Subscription state not found"
            ));
        List<AccountResponse> sandboxes = accounts.findSandboxesByUserId(userId)
            .stream()
            .map(AccountResponse::from)
            .toList();
        return SubscriptionResponse.from(subscription, sandboxes);
    }

    /**
     * Internal boundary for a future signature-verified billing webhook adapter.
     * No public controller calls this method.
     */
    @Transactional
    public SubscriptionResponse applyProviderUpdate(BillingSubscriptionUpdate update) {
        UserSubscription subscription = subscriptions
            .findByUserIdForUpdate(update.userId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Subscription state not found"
            ));
        subscription.apply(update, clock.instant());

        List<VirtualAccount> sandboxes = accounts.findSandboxesByUserId(update.userId());
        sandboxes.forEach(VirtualAccount::deactivate);
        if (update.status() == SubscriptionStatus.ACTIVE) {
            activatePrimarySandbox(subscription, update.plan());
        }
        return current(update.userId());
    }

    private void activatePrimarySandbox(
        UserSubscription subscription,
        SubscriptionPlan plan
    ) {
        VirtualAccount sandbox = accounts
            .findByUserIdAndAccountKindAndSandboxPlanAndSandboxSlot(
                subscription.getUserId(),
                AccountKind.SANDBOX,
                plan,
                1
            )
            .orElseGet(() -> accounts.save(VirtualAccount.sandbox(
                subscription.getUser(),
                plan,
                1,
                plan.sandboxCapitalInr()
            )));
        sandbox.activate();
    }
}
