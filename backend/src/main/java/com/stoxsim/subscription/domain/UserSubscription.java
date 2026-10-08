package com.stoxsim.subscription.domain;

import java.time.Instant;
import java.util.UUID;

import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.subscription.provider.BillingSubscriptionUpdate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "user_subscription")
public class UserSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 24)
    private SubscriptionStatus status;

    @Column(name = "billing_provider", length = 32)
    private String billingProvider;

    @Column(name = "provider_customer_reference", length = 120)
    private String providerCustomerReference;

    @Column(name = "provider_subscription_reference", length = 120)
    private String providerSubscriptionReference;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "test_access_until")
    private Instant testAccessUntil;

    @Column(name = "scenario_free_used", nullable = false)
    private int scenarioFreeUsed;
    @Column(name = "scenario_paid_used", nullable = false)
    private int scenarioPaidUsed;
    @Column(name = "scenario_credit_period_end")
    private Instant scenarioCreditPeriodEnd;

    public int remainingScenarioCredits() {
        var effective = effectivePlan();
        return Math.max(0, effective.scenarioCredits() - (effective == SubscriptionPlan.FREE ? scenarioFreeUsed : scenarioPaidUsed));
    }
    public Instant getScenarioCreditPeriodEnd() { return scenarioCreditPeriodEnd; }
    public void consumeScenarioCredit() {
        if (remainingScenarioCredits() <= 0) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.FORBIDDEN, "No Scenario Lab credits left. View your plan for more credits.");
        if (effectivePlan() == SubscriptionPlan.FREE) scenarioFreeUsed++; else scenarioPaidUsed++;
    }

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserSubscription() {
    }

    public UserSubscription(AppUser user) {
        this.user = user;
        this.plan = SubscriptionPlan.FREE;
        this.status = SubscriptionStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void apply(BillingSubscriptionUpdate update, Instant now) {
        // Only a newly paid period refills credits. Replayed events, grace periods,
        // plan toggles and failed renewals must not grant another allowance.
        if (update.status() == SubscriptionStatus.ACTIVE && update.plan() != SubscriptionPlan.FREE
            && update.currentPeriodEnd() != null && update.currentPeriodEnd().isAfter(now)
            && (scenarioCreditPeriodEnd == null || update.currentPeriodEnd().isAfter(scenarioCreditPeriodEnd))) {
            scenarioPaidUsed = 0;
            scenarioCreditPeriodEnd = update.currentPeriodEnd();
        }
        this.plan = update.plan();
        this.status = update.status();
        this.billingProvider = update.provider();
        this.providerCustomerReference = update.customerReference();
        this.providerSubscriptionReference = update.subscriptionReference();
        this.currentPeriodEnd = update.currentPeriodEnd();
        this.updatedAt = now;
    }

    public UUID getUserId() {
        return user.getId();
    }

    public AppUser getUser() {
        return user;
    }

    public SubscriptionPlan getPlan() {
        return plan;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public boolean hasActiveEntitlement(SubscriptionFeature feature) {
        return effectivePlan().includes(feature)
            && !(isTestBilling() && feature == SubscriptionFeature.PREMIUM_COMPETITIONS);
    }

    public boolean isTestBilling() { return "RAZORPAY_TEST".equals(billingProvider); }
    public String getBillingProvider() { return billingProvider; }
    public String getProviderSubscriptionReference() { return providerSubscriptionReference; }
    public Instant getTestAccessUntil() { return testAccessUntil; }
    public void setTestAccessUntil(Instant until) { testAccessUntil = until; }
    public SubscriptionPlan effectivePlan() {
        if (status != SubscriptionStatus.ACTIVE) return SubscriptionPlan.FREE;
        if (isTestBilling() && (testAccessUntil == null || !Instant.now().isBefore(testAccessUntil)
            || !user.isPlatformAdmin() || !user.isEmailVerified())) return SubscriptionPlan.FREE;
        return plan;
    }
    public void clearTestBenefits(Instant now) {
        if (!isTestBilling()) return;
        plan = SubscriptionPlan.FREE; status = SubscriptionStatus.ACTIVE;
        billingProvider = null; providerCustomerReference = null; providerSubscriptionReference = null;
        currentPeriodEnd = null; testAccessUntil = null; updatedAt = now;
    }
}
