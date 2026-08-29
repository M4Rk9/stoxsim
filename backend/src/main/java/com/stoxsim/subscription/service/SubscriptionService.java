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
import com.stoxsim.subscription.domain.SubscriptionFeature;
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
        sandboxes.forEach(account -> {
            if (update.status() == SubscriptionStatus.ACTIVE
                && account.getSandboxPlan() == update.plan()) {
                account.activate();
            } else {
                account.deactivate();
            }
        });
        if (update.status() == SubscriptionStatus.ACTIVE
            && update.plan().includes(SubscriptionFeature.SANDBOX_PORTFOLIO)) {
            activatePrimarySandbox(subscription, update.plan());
        }
        return current(update.userId());
    }

    @Transactional
    public AccountResponse createAdditionalSandbox(UUID userId, String idempotencyKey) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        UserSubscription subscription = subscriptions.findByUserIdForUpdate(userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Subscription state not found"
            ));
        requireActiveFeature(subscription, SubscriptionFeature.MULTIPLE_PORTFOLIOS);

        var existing = accounts.findByUserIdAndProvisioningKey(userId, normalizedKey);
        if (existing.isPresent()) {
            return AccountResponse.from(existing.get());
        }

        List<VirtualAccount> currentPlanSandboxes = accounts
            .findSandboxesByUserIdAndPlan(userId, subscription.getPlan());
        if (currentPlanSandboxes.stream().noneMatch(account -> account.getSandboxSlot() == 1)) {
            accounts.save(VirtualAccount.sandbox(
                subscription.getUser(),
                subscription.getPlan(),
                1,
                subscription.getPlan().sandboxCapitalInr()
            ));
        }

        int slot = java.util.stream.IntStream
            .rangeClosed(2, subscription.getPlan().maximumSandboxPortfolios())
            .filter(candidate -> currentPlanSandboxes.stream()
                .noneMatch(account -> account.getSandboxSlot() == candidate))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The Pro sandbox portfolio limit has been reached"
            ));
        VirtualAccount sandbox = accounts.save(VirtualAccount.sandbox(
            subscription.getUser(),
            subscription.getPlan(),
            slot,
            subscription.getPlan().sandboxCapitalInr(),
            normalizedKey
        ));
        return AccountResponse.from(sandbox);
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

    private void requireActiveFeature(
        UserSubscription subscription,
        SubscriptionFeature feature
    ) {
        if (!subscription.hasActiveEntitlement(feature)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "This feature requires an active "
                    + feature.minimumPlan().displayName()
                    + " subscription"
            );
        }
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null
            || idempotencyKey.isBlank()
            || idempotencyKey.length() > 100) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Idempotency-Key must contain between 1 and 100 characters"
            );
        }
        return idempotencyKey.trim();
    }
}
