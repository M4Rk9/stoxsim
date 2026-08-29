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
        return status == SubscriptionStatus.ACTIVE && plan.includes(feature);
    }
}
