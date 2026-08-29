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
    String notice
) {
    public static SubscriptionResponse from(
        UserSubscription subscription,
        List<AccountResponse> sandboxAccounts
    ) {
        return new SubscriptionResponse(
            "subscription-entitlements-v2",
            subscription.getPlan(),
            subscription.getStatus(),
            false,
            subscription.getCurrentPeriodEnd(),
            EntitlementResponse.from(subscription.getPlan()),
            SandboxProvisioningResponse.from(subscription, sandboxAccounts),
            List.copyOf(sandboxAccounts),
            java.util.Arrays.stream(SubscriptionPlan.values()).map(PlanResponse::from).toList(),
            "Paid billing is not enabled. Standard competition capital always remains separate."
        );
    }
}
