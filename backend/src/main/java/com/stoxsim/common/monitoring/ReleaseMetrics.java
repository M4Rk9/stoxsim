package com.stoxsim.common.monitoring;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.stoxsim.finwiz.config.FinwizSpendProperties;
import com.stoxsim.rewards.ReferralService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReleaseMetrics {
    private final JdbcTemplate db;
    private final ReferralService referrals;
    private final FinwizSpendProperties spendSettings;
    private final Clock clock;
    private final MultiGauge plans;
    private final MultiGauge purchases;
    private final MultiGauge billing;
    private final MultiGauge referralCounts;
    private final MultiGauge ledger;
    private final MultiGauge spend;
    private final MultiGauge remaining;
    private final AtomicLong collectedAt = new AtomicLong();

    public ReleaseMetrics(JdbcTemplate db, ReferralService referrals, FinwizSpendProperties spendSettings,
        Clock clock, MeterRegistry registry) {
        this.db = db;
        this.referrals = referrals;
        this.spendSettings = spendSettings;
        this.clock = clock;
        plans = gauge(registry, "stoxsim.subscription.learners");
        purchases = gauge(registry, "stoxsim.billing.purchases");
        billing = gauge(registry, "stoxsim.billing.charges.30d.paise");
        referralCounts = gauge(registry, "stoxsim.referral.attributions");
        ledger = gauge(registry, "stoxsim.referral.ledger.mismatches");
        spend = gauge(registry, "stoxsim.finwiz.spend.month.micros");
        remaining = gauge(registry, "stoxsim.finwiz.spend.remaining.micros");
        registry.gauge("stoxsim.release.metrics.last.success.seconds", collectedAt);
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 300_000)
    @Transactional(readOnly = true, timeout = 15)
    public void collect() {
        plans.register(db.query("""
            WITH learner_plans AS (
                SELECT CASE
                    WHEN s.subscription_status='ACTIVE' AND s.plan IN ('PLUS','PRO')
                      AND s.billing_provider IN ('RAZORPAY','GOOGLE_PLAY') AND s.billing_access_until>now()
                    THEN s.plan ELSE 'FREE' END AS plan
                FROM app_user u LEFT JOIN user_subscription s ON s.user_id=u.id
                WHERE u.platform_role='USER' AND NOT u.is_test_account
            )
            SELECT expected.plan,count(actual.plan)
            FROM (VALUES ('FREE'),('PLUS'),('PRO')) AS expected(plan)
            LEFT JOIN learner_plans actual ON actual.plan=expected.plan
            GROUP BY expected.plan
            """, (rs,n) -> MultiGauge.Row.of(Tags.of("plan",rs.getString(1)),rs.getLong(2))), true);

        purchases.register(db.query("""
            SELECT provider,plan,
                CASE WHEN access_until>now() THEN status
                    WHEN status IN ('ACTIVE','ENDING','GRACE') THEN 'NO_ACCESS' ELSE status END AS state,
                count(*)
            FROM billing_purchase GROUP BY provider,plan,state
            """, (rs,n) -> MultiGauge.Row.of(Tags.of("provider",rs.getString(1),"plan",rs.getString(2),
                "state",rs.getString(3)),rs.getLong(4))), true);

        billing.register(db.query("""
            SELECT coalesce(sum(amount_minor),0),coalesce(sum(refunded_minor),0)
            FROM billing_charge WHERE provider='RAZORPAY' AND currency='INR'
                AND status IN ('captured','refunded') AND paid_at>=now()-interval '30 days'
            """, (rs,n) -> List.of(
                MultiGauge.Row.of(Tags.of("kind","captured"),rs.getBigDecimal(1)),
                MultiGauge.Row.of(Tags.of("kind","refunded"),rs.getBigDecimal(2)))).getFirst(), true);

        referralCounts.register(db.query("""
            WITH referral_states AS (
                SELECT CASE WHEN rejection IN ('DUPLICATE_IDENTITY','EXPIRED') THEN rejection ELSE status END AS state
                FROM referral_attribution
            )
            SELECT expected.state,count(actual.state)
            FROM (VALUES ('PENDING'),('QUALIFIED'),('REWARDED'),('REJECTED'),('DUPLICATE_IDENTITY'),('EXPIRED')) AS expected(state)
            LEFT JOIN referral_states actual ON actual.state=expected.state
            GROUP BY expected.state
            """, (rs,n) -> MultiGauge.Row.of(Tags.of("state",rs.getString(1)),rs.getLong(2))), true);
        var health = referrals.ledgerHealth();
        ledger.register(List.of(
            MultiGauge.Row.of(Tags.of("kind","account"),health.unbalancedAccounts()),
            MultiGauge.Row.of(Tags.of("kind","referral"),health.unmatchedReferrals())), true);

        if (spendSettings.enabled()) collectSpend();
        collectedAt.set(clock.instant().getEpochSecond());
    }

    private void collectSpend() {
        var groups = db.query("""
            SELECT depth,coalesce(sum(reserved_micros),0),coalesce(sum(reported_micros),0),
                coalesce(sum(coalesce(reported_micros,reserved_micros)),0),
                coalesce(sum(coalesce(reported_micros,reserved_micros))
                    FILTER (WHERE usage_day=(now() AT TIME ZONE 'UTC')::date),0)
            FROM finwiz_spend_attempt
            WHERE usage_day>=date_trunc('month',now() AT TIME ZONE 'UTC')::date
            GROUP BY depth
            """, (rs,n) -> new Spend(rs.getString(1),rs.getDouble(2),rs.getDouble(3),rs.getDouble(4),rs.getDouble(5)));
        List<MultiGauge.Row<?>> amounts = groups.stream().flatMap(row -> Stream.<MultiGauge.Row<?>>of(
            MultiGauge.Row.of(Tags.of("tier",row.depth(),"kind","estimated"),row.estimated()),
            MultiGauge.Row.of(Tags.of("tier",row.depth(),"kind","reported"),row.reported()))).toList();
        spend.register(amounts, true);
        remaining.register(List.of(
            budget("day",spendSettings.dailyMicros(),groups.stream().mapToDouble(Spend::day).sum()),
            budget("month",spendSettings.monthlyMicros(),groups.stream().mapToDouble(Spend::month).sum()),
            budget("plus",spendSettings.plusMonthlyMicros(),tierSpend(groups,"EXPANDED")),
            budget("pro",spendSettings.proMonthlyMicros(),tierSpend(groups,"FULL"))), true);
    }

    private double tierSpend(List<Spend> rows, String tier) {
        return rows.stream().filter(row -> row.depth().equals(tier)).mapToDouble(Spend::month).sum();
    }

    private MultiGauge.Row<?> budget(String scope, long limit, double used) {
        return MultiGauge.Row.of(Tags.of("scope",scope),Math.max(0,limit-used));
    }

    private MultiGauge gauge(MeterRegistry registry, String name) {
        return MultiGauge.builder(name).register(registry);
    }

    private record Spend(String depth, double estimated, double reported, double month, double day) { }
}
