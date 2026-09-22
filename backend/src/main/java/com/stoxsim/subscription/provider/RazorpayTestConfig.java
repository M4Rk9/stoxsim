package com.stoxsim.subscription.provider;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RazorpayTestConfig {
    public final boolean enabled;
    public final String keyId, keySecret, webhookSecret, plusPlan, proPlan;
    public RazorpayTestConfig(
        @Value("${stoxsim.billing.test.enabled:false}") boolean enabled,
        @Value("${stoxsim.billing.test.key-id:}") String keyId,
        @Value("${stoxsim.billing.test.key-secret:}") String keySecret,
        @Value("${stoxsim.billing.test.webhook-secret:}") String webhookSecret,
        @Value("${stoxsim.billing.test.plus-plan:}") String plusPlan,
        @Value("${stoxsim.billing.test.pro-plan:}") String proPlan) {
        this.enabled=enabled; this.keyId=keyId; this.keySecret=keySecret;
        this.webhookSecret=webhookSecret; this.plusPlan=plusPlan; this.proPlan=proPlan;
        if (enabled && (!keyId.matches("rzp_test_[A-Za-z0-9]+") || keySecret.isBlank()
            || webhookSecret.length()<32 || !plusPlan.matches("plan_[A-Za-z0-9]+")
            || !proPlan.matches("plan_[A-Za-z0-9]+") || plusPlan.equals(proPlan)))
            throw new IllegalStateException("Test billing requires test keys, distinct plan IDs and a webhook secret of at least 32 characters");
    }
    public String planId(String plan) {
        return switch(plan) { case "PLUS" -> plusPlan; case "PRO" -> proPlan;
            default -> throw new IllegalArgumentException("Unsupported test plan"); };
    }
}
