package com.stoxsim.subscription.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import com.stoxsim.account.domain.AccountKind;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.order.service.SandboxOrderCleanup;
import com.stoxsim.subscription.domain.SubscriptionPlan;
import com.stoxsim.subscription.domain.SubscriptionStatus;
import com.stoxsim.subscription.domain.UserSubscription;
import com.stoxsim.subscription.provider.BillingSubscriptionUpdate;
import com.stoxsim.subscription.provider.RazorpayTestConfig;
import com.stoxsim.subscription.repository.UserSubscriptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Opt-in projection from verified TEST records. Never touches standard portfolios. */
@Service
public class TestBenefitService {
    private final JdbcTemplate db;
    private final AppUserRepository users;
    private final UserSubscriptionRepository subscriptions;
    private final VirtualAccountRepository accounts;
    private final SandboxOrderCleanup cleanup;
    private final RazorpayTestConfig config;
    public TestBenefitService(JdbcTemplate db,AppUserRepository users,UserSubscriptionRepository subscriptions,
        VirtualAccountRepository accounts,SandboxOrderCleanup cleanup,RazorpayTestConfig config) {
        this.db=db;this.users=users;this.subscriptions=subscriptions;this.accounts=accounts;this.cleanup=cleanup;this.config=config;
    }
    /** Caller holds user and test-ledger locks, then subscription and account locks are acquired. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void sync(UUID id) {
        var row=db.queryForMap("SELECT * FROM razorpay_test_subscription WHERE id=?",id);
        UUID userId=(UUID)row.get("user_id");
        String providerId=(String)row.get("provider_id");
        boolean requested=(Boolean)row.get("benefits_enabled");
        var user=users.findById(userId).orElseThrow();
        UserSubscription subscription=subscriptions.findByUserIdForUpdate(userId).orElse(null);
        if (!requested && (subscription==null || !subscription.isTestBilling()
            || !java.util.Objects.equals(providerId,subscription.getProviderSubscriptionReference()))) return;
        if (requested && subscription!=null && !subscription.isTestBilling()
            && (subscription.getPlan()!=SubscriptionPlan.FREE || subscription.getBillingProvider()!=null))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Test benefits cannot replace an existing paid subscription");
        Instant now=Instant.now(), until=null;
        String state="OFF";
        if (requested) {
            state="LOCKED";
            Instant paidThrough=instant(row.get("paid_through"));
            String remote=(String)row.get("status");
            if (config.enabled && user.isPlatformAdmin() && user.isEmailVerified() && paidThrough!=null) {
                if (remote.equals("active")) { until=paidThrough;state="ACTIVE"; }
                if (remote.equals("pending")) { until=paidThrough.plus(Duration.ofDays(3));state="GRACE"; }
                if (until!=null && !now.isBefore(until)) { until=null;state="EXPIRED"; }
            }
        }
        if (subscription==null) subscription=subscriptions.save(new UserSubscription(user));
        SubscriptionPlan plan=SubscriptionPlan.valueOf((String)row.get("plan"));
        if (!requested || !config.enabled || !user.isPlatformAdmin() || !user.isEmailVerified()) subscription.clearTestBenefits(now);
        else {
            // The local user ID is an internal test customer reference, never sent to Razorpay.
            String remote=(String)row.get("status");
            var subscriptionStatus=until!=null ? SubscriptionStatus.ACTIVE
                : java.util.Set.of("cancelled","completed","expired").contains(remote)
                    ? SubscriptionStatus.CANCELED : SubscriptionStatus.PAST_DUE;
            subscription.apply(new BillingSubscriptionUpdate(userId,plan,subscriptionStatus,
                "RAZORPAY_TEST",userId.toString(),providerId,instant(row.get("current_period_end"))),now);
            subscription.setTestAccessUntil(until);
        }
        // Lock accounts in stable order, matching order placement/settlement's account-first discipline.
        var sandboxIds=accounts.findSandboxesByUserId(userId).stream().map(VirtualAccount::getId).sorted().toList();
        for (var accountId:sandboxIds) {
            var account=accounts.findByIdForUpdate(accountId).orElseThrow();
            if (until!=null && account.getSandboxPlan()==plan) {
                account.setTestTradingUntil(until);account.activate();
            } else {
                account.deactivate();cleanup.cancelOpenForAccount(account);
            }
        }
        if (until!=null && accounts.findByUserIdAndAccountKindAndSandboxPlanAndSandboxSlot(userId,AccountKind.SANDBOX,plan,1).isEmpty()) {
            var account=VirtualAccount.sandbox(user,plan,1,plan.sandboxCapitalInr());
            account.setTestTradingUntil(until);accounts.save(account);
        }
        db.update("UPDATE razorpay_test_subscription SET benefit_status=?,access_until=? WHERE id=?",state,
            until==null?null:Timestamp.from(until),id);
    }
    private Instant instant(Object value) { return value==null?null:((Timestamp)value).toInstant(); }
}
