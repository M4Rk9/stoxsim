package com.stoxsim.rewards;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReferralService {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ELIGIBILITY = """
        inviter.email_verified_at IS NOT NULL AND learner.email_verified_at IS NOT NULL
        AND inviter.platform_role='USER' AND learner.platform_role='USER'
        AND NOT inviter.is_test_account AND NOT learner.is_test_account
        AND learner.created_at <= now() - (? * interval '1 hour')
        AND EXISTS (
            SELECT 1 FROM virtual_account a JOIN trade t ON t.account_id=a.id
            JOIN paper_order o ON o.id=t.order_id AND o.account_id=a.id AND o.status='EXECUTED'
            WHERE a.user_id=r.referred_id AND a.account_kind='STANDARD'
              AND t.executed_at>=greatest(r.created_at, learner.email_verified_at)
              AND t.executed_at<r.created_at+(? * interval '1 day')
        )
        """;
    private final JdbcTemplate db;
    private final ReferralProperties properties;
    private final VirtualRewardLedger ledger;
    private final String frontendUrl;

    public ReferralService(JdbcTemplate db, ReferralProperties properties, VirtualRewardLedger ledger,
        @Value("${stoxsim.frontend-url}") String frontendUrl) {
        this.db = db;
        this.properties = properties;
        this.ledger = ledger;
        this.frontendUrl = frontendUrl.replaceFirst("/+$", "");
    }

    record Referral(UUID id, String status, String rejection, Instant createdAt, Instant qualifiedAt, Instant reviewedAt) { }
    record Overview(boolean enabled, boolean canInvite, boolean canReview, String code, String link,
                    int minimumAgeHours, int qualificationDays, BigDecimal rewardInr, BigDecimal rewardUsd, long pending, long successful,
                    VirtualRewardLedger.Totals earned, List<Referral> referrals, List<VirtualRewardLedger.Entry> history) { }
    record Review(UUID id, UUID referrerId, UUID referredId, Instant createdAt, Instant qualifiedAt) { }
    record Outcome(String status, UUID transactionId) { }
    record Reconciliation(long unbalancedAccounts, long unmatchedReferrals) { }
    private record Owners(UUID referrer, UUID referred) { }

    @Transactional(readOnly = true)
    public Overview overview(UUID user) {
        String code = db.queryForList("SELECT code FROM referral_code WHERE referrer_id=?", String.class, user)
            .stream().findFirst().orElse(null);
        var counts = db.queryForMap("""
            SELECT count(*) FILTER (WHERE status IN ('PENDING','QUALIFIED')) AS pending,
                   count(*) FILTER (WHERE status='REWARDED') AS successful
            FROM referral_attribution WHERE referrer_id=?
            """, user);
        List<Referral> referrals = db.query("""
            SELECT id,status,rejection,created_at,qualified_at,reviewed_at FROM referral_attribution
            WHERE referrer_id=? ORDER BY created_at DESC,id LIMIT 50
            """, (rs,n) -> new Referral(rs.getObject("id",UUID.class),rs.getString("status"),rs.getString("rejection"),
                instant(rs.getTimestamp("created_at")),instant(rs.getTimestamp("qualified_at")),
                instant(rs.getTimestamp("reviewed_at"))),user);
        return new Overview(properties.isEnabled(), properties.isEnabled() && eligibleInviter(user), isReviewer(user), code,
            code == null ? null : frontendUrl + "/invite?ref=" + code, properties.getMinimumAgeHours(), properties.getQualificationDays(),
            VirtualRewardLedger.INR, VirtualRewardLedger.USD, ((Number) counts.get("pending")).longValue(),
            ((Number) counts.get("successful")).longValue(), ledger.totals(user), referrals, ledger.entries(user));
    }

    @Transactional
    public Overview createCode(UUID user) {
        requireEnabled();
        lockAccounts(user,user);
        if (!eligibleInviter(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,
            "Verify your email to create a referral link.");
        for (int attempt = 0; attempt < 3; attempt++) {
            StringBuilder code = new StringBuilder(16);
            for (int i = 0; i < 16; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            db.update("INSERT INTO referral_code(code,referrer_id) VALUES (?,?) ON CONFLICT DO NOTHING", code.toString(), user);
            var result = overview(user);
            if (result.code() != null) return result;
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not create a referral link.");
    }

    /** Called only for accounts created in the current registration transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void attribute(UUID user, String email, String googleSubject, String rawCode) {
        if (!properties.isEnabled() || rawCode == null) return;
        String code = rawCode.strip().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z2-7]{16}")) return;
        if (!Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM app_user WHERE id=? AND platform_role='USER' AND NOT is_test_account)
            """,Boolean.class,user))) return;
        var inviters = db.query("""
            SELECT u.id,u.email FROM referral_code c JOIN app_user u ON u.id=c.referrer_id
            WHERE c.code=? AND u.id<>? AND u.email_verified_at IS NOT NULL
              AND u.platform_role='USER' AND NOT u.is_test_account FOR UPDATE OF u
            """, (rs,n) -> new String[]{rs.getString("id"),rs.getString("email")},code,user);
        if (inviters.isEmpty()) return;
        UUID inviter = UUID.fromString(inviters.getFirst()[0]);
        if (canonicalEmail(email).equals(canonicalEmail(inviters.getFirst()[1]))) return;
        int recent = db.queryForObject("""
            SELECT count(*) FROM referral_attribution
            WHERE referrer_id=? AND created_at>=date_trunc('day',now(),'UTC')
            """,Integer.class,inviter);
        if (recent >= properties.getDailyAttributionLimit()) return;
        boolean fresh = !Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM app_user WHERE email_verified_at IS NOT NULL AND id<>?
                AND canonical_referral_email(email)=canonical_referral_email(CAST(? AS text)))
            """,Boolean.class,user,email));
        for (String proof : proofs(email,googleSubject)) fresh = claimIdentity(proof) && fresh;
        db.update("""
            INSERT INTO referral_attribution(referrer_id,referred_id,status,rejection)
            VALUES (?,?,?,?) ON CONFLICT (referred_id) DO NOTHING
            """, inviter, user, fresh ? "PENDING" : "REJECTED", fresh ? null : "DUPLICATE_IDENTITY");
    }

    /** Hold only pseudonymous identity keys after deletion, including accounts that never used a referral. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void rememberDeletedIdentity(UUID user, String email) {
        if (!properties.retainsIdentityHolds()) return;
        var subjects = db.queryForList("SELECT subject FROM google_identity WHERE user_id=?", String.class, user);
        for (String proof : proofs(email,subjects.stream().findFirst().orElse(null))) {
            db.update("""
                INSERT INTO referral_identity_hold(identity_hash,expires_at) VALUES (?,now()+interval '180 days')
                ON CONFLICT (identity_hash) DO UPDATE SET expires_at=excluded.expires_at
                """, proof);
        }
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    @Transactional
    public void qualifyPending() {
        if (!properties.isEnabled()) return;
        db.update("""
            WITH eligible AS (
                SELECT r.id FROM referral_attribution r JOIN app_user inviter ON inviter.id=r.referrer_id
                JOIN app_user learner ON learner.id=r.referred_id
                WHERE r.status='PENDING' AND r.created_at>now()-(? * interval '1 day') AND %s
                ORDER BY r.created_at,r.id LIMIT 100 FOR UPDATE OF r SKIP LOCKED
            )
            UPDATE referral_attribution r SET status='QUALIFIED',qualified_at=now()
            FROM eligible WHERE r.id=eligible.id
            """.formatted(ELIGIBILITY), properties.getQualificationDays(), properties.getMinimumAgeHours(), properties.getQualificationDays());
        db.update("""
            UPDATE referral_attribution SET status='REJECTED',rejection='EXPIRED',reviewed_at=now()
            WHERE status='PENDING' AND created_at<=now()-(? * interval '1 day')
            """, properties.getQualificationDays());
    }

    @Scheduled(initialDelayString = "PT1H", fixedDelayString = "PT24H")
    @Transactional
    public void purgeExpiredHolds() {
        db.update("DELETE FROM referral_identity_hold WHERE expires_at<=now()");
    }

    @Transactional(readOnly = true)
    public List<Review> reviewQueue(UUID reviewer) {
        requireReviewer(reviewer);
        return db.query("""
            SELECT id,referrer_id,referred_id,created_at,qualified_at FROM referral_attribution
            WHERE status='QUALIFIED' ORDER BY qualified_at,id LIMIT 100
            """, (rs,n) -> new Review(rs.getObject("id",UUID.class),rs.getObject("referrer_id",UUID.class),
                rs.getObject("referred_id",UUID.class),instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("qualified_at"))));
    }

    @Transactional(readOnly = true)
    public Reconciliation reconcile(UUID reviewer) {
        requireReviewer(reviewer);
        return db.queryForObject("""
            SELECT (
                SELECT count(*) FROM virtual_account a
                WHERE a.account_kind='REWARD' AND a.starting_capital <> (
                    SELECT coalesce(sum(e.amount),0) FROM virtual_reward_entry e
                    WHERE e.account_id=a.id AND e.currency=a.currency
                )
            ), (
                SELECT count(*) FROM referral_attribution r
                FULL JOIN (SELECT source_id FROM virtual_reward_transaction WHERE source='REFERRAL') t
                    ON t.source_id=r.id
                WHERE coalesce(r.status='REWARDED',false) <> (t.source_id IS NOT NULL)
            )
            """, (rs,n) -> new Reconciliation(rs.getLong(1),rs.getLong(2)));
    }

    @Transactional
    public Outcome decide(UUID reviewer, UUID id, boolean approve) {
        requireReviewer(reviewer);
        if (approve) requireEnabled();
        Owners owners = owners(id);
        lockAccounts(owners.referrer(),owners.referred());
        String status = lockAttribution(id);
        if ("REWARDED".equals(status) && approve) return new Outcome(status, grantId(id));
        if ("REJECTED".equals(status) && !approve) return new Outcome(status,null);
        if (!"QUALIFIED".equals(status)) throw conflict("This referral is no longer awaiting review.");
        if (!approve) {
            db.update("""
                UPDATE referral_attribution SET status='REJECTED',rejection='ADMIN_REJECTED',reviewer_id=?,reviewed_at=now()
                WHERE id=?
                """,reviewer,id);
            return new Outcome("REJECTED",null);
        }
        if (!Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM referral_attribution r JOIN app_user inviter ON inviter.id=r.referrer_id
                JOIN app_user learner ON learner.id=r.referred_id WHERE r.id=? AND %s
            )
            """.formatted(ELIGIBILITY),Boolean.class,id,properties.getMinimumAgeHours(),properties.getQualificationDays()))) {
            throw conflict("The referral no longer meets qualification rules.");
        }
        int issued = db.queryForObject("""
            SELECT count(*) FROM referral_attribution
            WHERE referrer_id=? AND status='REWARDED' AND reviewed_at>now()-interval '7 days'
            """,Integer.class,owners.referrer());
        if (issued>=properties.getWeeklyRewardLimit()) throw conflict("This member has reached the weekly referral reward limit.");
        UUID grant = ledger.grantReferral(id,owners.referrer(),reviewer);
        db.update("UPDATE referral_attribution SET status='REWARDED',reviewer_id=?,reviewed_at=now() WHERE id=?",
            reviewer,id);
        return new Outcome("REWARDED",grant);
    }

    @Transactional
    public Outcome reverse(UUID reviewer, UUID id) {
        requireReviewer(reviewer);
        Owners owners = owners(id);
        lockAccounts(owners.referrer(),owners.referred());
        if (!"REWARDED".equals(lockAttribution(id))) throw conflict("This referral has no grant to correct.");
        return new Outcome("REVERSED",ledger.reverseReferral(grantId(id),reviewer));
    }

    private Owners owners(UUID id) {
        return db.query("SELECT referrer_id,referred_id FROM referral_attribution WHERE id=?",
            (rs,n) -> new Owners(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class)),id)
            .stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Referral not found."));
    }

    private void lockAccounts(UUID inviter, UUID learner) {
        db.queryForList("SELECT id FROM app_user WHERE id IN (?,?) ORDER BY id FOR UPDATE",UUID.class,inviter,learner);
    }

    private String lockAttribution(UUID id) {
        return db.queryForObject("SELECT status FROM referral_attribution WHERE id=? FOR UPDATE",String.class,id);
    }

    private UUID grantId(UUID id) {
        return db.queryForObject("SELECT id FROM virtual_reward_transaction WHERE source='REFERRAL' AND source_id=?",
            UUID.class,id);
    }

    private boolean eligibleInviter(UUID user) {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM app_user WHERE id=? AND email_verified_at IS NOT NULL
                           AND platform_role='USER' AND NOT is_test_account)
            """,Boolean.class,user));
    }

    private boolean isReviewer(UUID user) {
        return Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM app_user WHERE id=? AND email_verified_at IS NOT NULL AND platform_role='ADMIN')
            """,Boolean.class,user));
    }

    private void requireReviewer(UUID reviewer) {
        if (!isReviewer(reviewer)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Verified administrator required.");
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Referral rewards are paused.");
    }

    private boolean claimIdentity(String hash) {
        return db.update("""
            INSERT INTO referral_identity_hold(identity_hash,expires_at) VALUES (?,now()+interval '180 days')
            ON CONFLICT (identity_hash) DO UPDATE SET expires_at=excluded.expires_at
            WHERE referral_identity_hold.expires_at<=now()
            """, hash) == 1;
    }

    private List<String> proofs(String email, String googleSubject) {
        String address = hash("email:" + canonicalEmail(email));
        return googleSubject == null ? List.of(address) :
            java.util.stream.Stream.of(address,hash("google:" + googleSubject)).sorted().toList();
    }

    static String canonicalEmail(String email) {
        String value = email.strip().toLowerCase(Locale.ROOT);
        int at = value.lastIndexOf('@');
        if (at < 0 || !List.of("gmail.com","googlemail.com").contains(value.substring(at+1))) return value;
        String local = value.substring(0,at);
        int plus = local.indexOf('+');
        if (plus>=0) local = local.substring(0,plus);
        return local.replace(".","") + "@gmail.com";
    }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.identityKey().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Referral identity protection is unavailable",e);
        }
    }

    private static Instant instant(java.sql.Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
