package com.stoxsim.subscription.api;

import java.util.List;

import com.stoxsim.account.domain.AccountKind;
import com.stoxsim.auth.api.dto.AccountResponse;
import com.stoxsim.subscription.domain.SubscriptionFeature;
import com.stoxsim.subscription.domain.SubscriptionStatus;
import com.stoxsim.subscription.domain.UserSubscription;

public record SandboxProvisioningResponse(
    boolean canCreateAdditional,
    int currentPlanSandboxes,
    int maximumSandboxes,
    SandboxProvisioningStatus status
) {
    public static SandboxProvisioningResponse from(
        UserSubscription subscription,
        List<AccountResponse> sandboxes
    ) {
        int current = (int) sandboxes.stream()
            .filter(account -> account.accountKind() == AccountKind.SANDBOX)
            .filter(account -> account.sandboxPlan() == subscription.getPlan())
            .count();
        int maximum = subscription.getPlan().maximumSandboxPortfolios();
        SandboxProvisioningStatus status = status(subscription, current, maximum);
        return new SandboxProvisioningResponse(
            status == SandboxProvisioningStatus.AVAILABLE,
            current,
            maximum,
            status
        );
    }

    private static SandboxProvisioningStatus status(
        UserSubscription subscription,
        int current,
        int maximum
    ) {
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            return SandboxProvisioningStatus.SUBSCRIPTION_INACTIVE;
        }
        if (!subscription.hasActiveEntitlement(SubscriptionFeature.MULTIPLE_PORTFOLIOS)) {
            return SandboxProvisioningStatus.PRO_REQUIRED;
        }
        if (current >= maximum) {
            return SandboxProvisioningStatus.LIMIT_REACHED;
        }
        return SandboxProvisioningStatus.AVAILABLE;
    }
}
