package com.stoxsim.subscription.provider;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.stoxsim.subscription.domain.SubscriptionPlan;
import com.stoxsim.subscription.domain.SubscriptionStatus;

/**
 * Provider-neutral input accepted only from a future verified billing adapter.
 * It is deliberately not exposed as an HTTP request body.
 */
public record BillingSubscriptionUpdate(
    UUID userId,
    SubscriptionPlan plan,
    SubscriptionStatus status,
    String provider,
    String customerReference,
    String subscriptionReference,
    Instant currentPeriodEnd
) {
    public BillingSubscriptionUpdate {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(status, "status");
        provider = required(provider, "provider", 32);
        customerReference = required(customerReference, "customerReference", 120);
        subscriptionReference = required(subscriptionReference, "subscriptionReference", 120);
        if (plan == SubscriptionPlan.FREE) {
            throw new IllegalArgumentException("Billing updates must use a paid plan");
        }
    }

    private static String required(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.trim();
    }
}
