package com.stoxsim.subscription.api;

import java.time.Instant;
import java.util.List;

import com.stoxsim.auth.api.dto.AccountResponse;
import com.stoxsim.subscription.domain.SubscriptionPlan;
import com.stoxsim.subscription.domain.SubscriptionStatus;
import com.stoxsim.subscription.domain.UserSubscription;

public record SubscriptionResponse(
    String version,
    SubscriptionPlan plan,
    SubscriptionStatus status,
    boolean billingEnabled,
    Instant currentPeriodEnd,
    EntitlementResponse entitlements,
    SandboxProvisioningResponse sandboxProvisioning,
    List<AccountResponse> sandboxAccounts,
    List<PlanResponse> plans,
    String notice,
    String billingMode,
    Instant testAccessUntil
) {
    public static SubscriptionResponse from(
        UserSubscription subscription,
        List<AccountResponse> sandboxAccounts
    ) {
        return from(subscription, sandboxAccounts, false);
    }
    public static SubscriptionResponse from(UserSubscription subscription, List<AccountResponse> sandboxAccounts, boolean billingEnabled) {
        return new SubscriptionResponse(
            "subscription-entitlements-v2",
            subscription.getPlan(),
            subscription.getStatus(),
            billingEnabled,
            subscription.getCurrentPeriodEnd(),
            EntitlementResponse.from(subscription),
            SandboxProvisioningResponse.from(subscription, sandboxAccounts),
            List.copyOf(sandboxAccounts),
            java.util.Arrays.stream(SubscriptionPlan.values()).map(PlanResponse::from).toList(),
            subscription.isTestBilling()
                ? "TEST benefits only: simulated payments, separate sandboxes, no premium competition access."
                : subscription.isLiveBilling() ? "Provider-verified subscription. Standard competition capital remains separate."
                : billingEnabled ? "Subscription checkout is available from Account."
                : "Paid billing is not enabled. Standard competition capital always remains separate.",
            subscription.isTestBilling() ? "TEST" : subscription.isLiveBilling() ? "LIVE" : billingEnabled ? "AVAILABLE" : "DISABLED",
            subscription.getTestAccessUntil()
        );
    }
}
