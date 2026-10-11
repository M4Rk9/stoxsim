package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import com.stoxsim.analytics.repository.ActivityRepository;
import com.stoxsim.analytics.repository.AnalyticsRepository;
import com.stoxsim.analytics.service.ProductActivityEvent;
import com.stoxsim.analytics.service.ProductActivityService;

@Testcontainers
class ControlledTestAnalyticsIntegrationTest {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final Instant SIGNUP = NOW.minusSeconds(10 * 86400);
    private static final Instant COVERAGE = NOW.minusSeconds(30 * 86400);
    JdbcTemplate db;
    UUID legacy;
    UUID[] additionalLegacy;

    @BeforeEach void setup() throws Exception {
        db = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        // Minimal domain fixture; execute the actual production migrations for
        // activity storage and test classification, not duplicated predicates.
        db.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        db.execute("""
            CREATE TABLE app_user(id uuid PRIMARY KEY, email text NOT NULL,
              display_name text NOT NULL, platform_role text NOT NULL DEFAULT 'USER',
              created_at timestamptz NOT NULL, email_verified_at timestamptz);
            CREATE TABLE virtual_account(id uuid PRIMARY KEY, user_id uuid REFERENCES app_user(id),
              account_kind text NOT NULL DEFAULT 'STANDARD', market_region text NOT NULL DEFAULT 'INDIA');
            CREATE TABLE paper_order(id uuid PRIMARY KEY, account_id uuid REFERENCES virtual_account(id),
              status text NOT NULL, created_at timestamptz NOT NULL, executed_at timestamptz);
            """);
        legacy = insert("support.stoxsim+production-smoke-1700000000-42@gmail.com", "Production Smoke", "USER");
        migrate("V111__product_activity.sql");
        migrate("V119__exclude_controlled_test_accounts.sql");
        additionalLegacy = new UUID[]{
            insert("browser-1700000000000-abcdef1234567@stoxsim.test", "Browser Learner", "USER"),
            insert("browser-auth-1700000000000-abcdef1234567@stoxsim.test", "Browser Learner", "USER"),
            insert("recovery-1700000000-42@stoxsim.test", "Recovery Drill", "USER"),
            insert("m7-12@stoxsim.test", "Synthetic capacity learner", "USER")
        };
        for (var id : additionalLegacy) assertThat(marked(id)).isFalse();
        assertThat(audit(false)).contains("5|1|4|0|4");
        migrate("V120__classify_browser_and_recovery_test_accounts.sql");
    }

    private void migrate(String name) throws Exception {
        try (var source = getClass().getResourceAsStream("/db/migration/" + name)) {
            db.execute(new String(source.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private UUID insert(String email, String name, String role) {
        var id = UUID.randomUUID();
        db.update("INSERT INTO app_user(id,email,display_name,platform_role,created_at,email_verified_at) VALUES (?,?,?,?,?,?)",
            id, email, name, role, java.sql.Timestamp.from(SIGNUP), java.sql.Timestamp.from(SIGNUP));
        return id;
    }

    private boolean marked(UUID id) {
        return db.queryForObject("SELECT is_test_account FROM app_user WHERE id=?", Boolean.class, id);
    }

    @Test void backfillsLegacyAndMarksOnlyReservedIdentitiesAtInsert() {
        assertThat(marked(legacy)).isTrue();
        assertThat(marked(insert("SUPPORT.STOXSIM+production-smoke-1700000001-7@GMAIL.COM", "Production Smoke", "USER"))).isTrue();
        assertThat(marked(insert("authorization-a-1700000001-7-8@stoxsim.test", "Authorization A", "USER"))).isTrue();
        assertThat(marked(insert("authorization-b-1700000001-7-8@stoxsim.test", "Authorization B", "USER"))).isTrue();
        assertThat(marked(insert("load-1700000001-7@stoxsim.test", "StoxSim Load Test", "USER"))).isTrue();
        assertThat(marked(insert("staging-smoke-1700000001-7@stoxsim.test", "Staging Smoke", "USER"))).isTrue();
        assertThat(marked(insert("m7-0123456789abcdef01234567@stoxsim.test", "CI capacity fixture", "USER"))).isTrue();
        assertThat(marked(insert("real@example.test", "Production Smoke", "USER"))).isFalse();
        assertThat(marked(insert("other+production-smoke-1700000001-7@gmail.com", "Production Smoke", "USER"))).isFalse();
        assertThat(marked(insert("support.stoxsim+production-smoke-1700000001-7@evil.test", "Production Smoke", "USER"))).isFalse();
        assertThat(marked(insert("support.stoxsim+production-smoke-invalid@gmail.com", "Production Smoke", "USER"))).isFalse();
        assertThat(marked(insert("support.stoxsim+production-smoke-1700000001-7@gmail.com", "Real Learner", "USER"))).isFalse();
        assertThat(marked(insert("authorization-a-1700000001-7-8@stoxsim.test", "Authorization B", "USER"))).isFalse();
        db.update("UPDATE app_user SET email='changed@example.test',display_name='Changed' WHERE id=?", legacy);
        assertThat(marked(legacy)).isTrue();
    }

    @Test void backfillsBrowserRecoveryAndCapacityWithoutBroadNameOrDomainExclusion() throws Exception {
        for (var id : additionalLegacy) assertThat(marked(id)).isTrue();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM app_user", Long.class)).isEqualTo(5);
        for (String label : new String[]{"auth", "markets", "finwiz", "lifecycle", "sandbox-switcher",
                "onboarding", "progression", "competitions", "campus-verification", "pro-provisioning"}) {
            assertThat(marked(insert("browser-" + label + "-1700000000000-abc123@stoxsim.test",
                "Browser Learner", "USER"))).isTrue();
        }
        assertThat(marked(insert("browser-1700000000000-abc123@stoxsim.test", "Browser Learner", "USER"))).isTrue();
        assertThat(marked(insert("recovery-1700000000-42@stoxsim.test", "Recovery Drill", "USER"))).isTrue();
        assertThat(marked(insert("m7-1@stoxsim.test", "Synthetic capacity learner", "USER"))).isTrue();
        for (String email : new String[]{"browser-unrecognized-1700000000000-abc123@stoxsim.test",
                "browser-auth-1700000000000-nothex@stoxsim.test", "browser-1700000000000-abc123@example.test"}) {
            assertThat(marked(insert(email, "Browser Learner", "USER"))).isFalse();
        }
        assertThat(marked(insert("browser-auth-1700000000000-abc123@stoxsim.test", "Real Learner", "USER"))).isFalse();
        assertThat(marked(insert("recovery-1700000000-42@stoxsim.test", "Real Learner", "USER"))).isFalse();
        assertThat(marked(insert("recovery-bad@stoxsim.test", "Recovery Drill", "USER"))).isFalse();
        assertThat(marked(insert("recovery-1700000000-42@example.test", "Recovery Drill", "USER"))).isFalse();
        assertThat(marked(insert("m7-13@stoxsim.test", "Synthetic capacity learner", "USER"))).isFalse();
        assertThat(marked(insert("m7-1@stoxsim.test", "Real Learner", "USER"))).isFalse();
        db.update("UPDATE app_user SET email='changed-browser@example.test',display_name='Changed' WHERE id=?", additionalLegacy[0]);
        assertThat(marked(additionalLegacy[0])).isTrue();
    }

    @Test void auditSurfacesUnknownTestDomainCandidatesWithoutMarkingThem() throws Exception {
        var candidate = insert("unrecognized-fixture@stoxsim.test", "Unknown fixture", "USER");
        var real = insert("real@example.com", "Browser Learner", "USER");
        assertThat(audit(false)).contains("7|5|0|2|1");
        assertThat(audit(true)).contains("unrecognized-fixture@stoxsim.test", "real@example.com",
            "browser-auth-1700000000000-abcdef1234567@stoxsim.test", "recovery-1700000000-42@stoxsim.test");
        assertThat(marked(candidate)).isFalse();
        assertThat(marked(real)).isFalse();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM app_user", Long.class)).isEqualTo(7);
    }

    @Test void mailboxAllowlistAndManualMarkerAreOperatorControlled() {
        assertThat(marked(insert("qa+production-smoke-1700000001-7@example.test", "Production Smoke", "USER"))).isFalse();
        db.update("INSERT INTO controlled_test_mailbox(base_email) VALUES ('qa@example.test')");
        assertThat(marked(insert("qa+production-smoke-1700000002-7@example.test", "Production Smoke", "USER"))).isTrue();
        var manual = insert("manual@example.test", "Acceptance", "USER");
        db.update("UPDATE app_user SET is_test_account=true WHERE id=?", manual);
        assertThat(marked(manual)).isTrue();
    }

    private String audit(boolean details) throws Exception {
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(
            Path.of("../deploy/production/audit-test-accounts.sql").toAbsolutePath().normalize()), "/tmp/audit.sql");
        var result = POSTGRES.execInContainer("psql", "-X", "-A", "-t", "-v", "ON_ERROR_STOP=1",
            "-U", POSTGRES.getUsername(), "-d", POSTGRES.getDatabaseName(),
            "-v", "smoke_base_email=support.stoxsim@gmail.com", "-v", "show_details=" + details, "-f", "/tmp/audit.sql");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        return result.getStdout();
    }

    @Test void readOnlyAuditWorksBeforeAndAfterMarkerMigration() throws Exception {
        assertThat(audit(false)).contains("5|5|0|0|0");
        db.execute("ALTER TABLE app_user DROP COLUMN is_test_account");
        assertThat(audit(false)).contains("5|0|5|0|4");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM app_user", Long.class)).isEqualTo(5);
    }

    private void journey(UUID id) {
        var account = UUID.randomUUID();
        db.update("INSERT INTO virtual_account(id,user_id) VALUES (?,?)", account, id);
        db.update("INSERT INTO paper_order(id,account_id,status,created_at,executed_at) VALUES (?,?,'EXECUTED',?,?)",
            UUID.randomUUID(), account, java.sql.Timestamp.from(SIGNUP.plusSeconds(3600)), java.sql.Timestamp.from(SIGNUP.plusSeconds(3600)));
        for (String kind : new String[]{"STOCK_OPENED", "WATCHLIST_ADDED", "ORDER_SUBMITTED", "ORDER_EXECUTED"}) {
            db.update("INSERT INTO product_activity(user_id,event_day,event_name,first_at) VALUES (?,?::date,?,?)",
                id, LocalDate.ofInstant(SIGNUP, ZoneOffset.UTC).toString(), kind, java.sql.Timestamp.from(SIGNUP.plusSeconds(3600)));
        }
        db.update("INSERT INTO product_activity(user_id,event_day,event_name,first_at) VALUES (?,?::date,'ACTIVE',?)",
            id, LocalDate.ofInstant(SIGNUP.plusSeconds(86400), ZoneOffset.UTC).toString(), java.sql.Timestamp.from(SIGNUP.plusSeconds(86400)));
    }

    @Test void excludesTestsAndAdminsAcrossEveryGrowthQueryAndIngestion() {
        var real = insert("real@example.test", "Learner", "USER");
        var sameName = insert("name@example.test", "Production Smoke", "USER");
        var otherMailbox = insert("other+production-smoke-1700000001-7@gmail.com", "Production Smoke", "USER");
        var admin = insert("owner@example.test", "Owner", "ADMIN");
        for (var id : new UUID[]{real, sameName, otherMailbox, legacy, admin}) journey(id);
        for (var id : additionalLegacy) journey(id);
        var overview = new AnalyticsRepository(db);
        assertThat(overview.users(NOW).registered()).isEqualTo(3);
        assertThat(overview.users(NOW).emailVerified()).isEqualTo(3);
        assertThat(overview.cohort(COVERAGE, NOW, NOW).registered()).isEqualTo(3);
        assertThat(overview.cohort(COVERAGE, NOW, NOW).firstTradeCompleted()).isEqualTo(3);
        assertThat(overview.signups(COVERAGE, NOW).getFirst().registered()).isEqualTo(3);
        assertThat(overview.orders(COVERAGE, NOW).getFirst().count()).isEqualTo(3);
        var activity = new ActivityRepository(db);
        assertThat(activity.active(COVERAGE, NOW)).isEqualTo(3);
        assertThat(activity.daily(COVERAGE, NOW)).allSatisfy(day -> assertThat(day.learners()).isEqualTo(3));
        assertThat(activity.activation(COVERAGE, NOW, COVERAGE, NOW).eligible()).isEqualTo(3);
        assertThat(activity.activation(COVERAGE, NOW, COVERAGE, NOW).activated()).isEqualTo(3);
        assertThat(activity.retention(1, COVERAGE, NOW, COVERAGE, LocalDate.ofInstant(NOW, ZoneOffset.UTC)).returned()).isEqualTo(3);
        var capture = new ProductActivityService(db, Clock.fixed(NOW, ZoneOffset.UTC));
        for (var id : additionalLegacy) {
            capture.record(id, ProductActivityEvent.Kind.ACTIVE);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM product_activity WHERE user_id=? AND event_day=?::date",
                Long.class, id, "2026-10-11")).isZero();
        }
        capture.record(legacy, ProductActivityEvent.Kind.ACTIVE);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM product_activity WHERE user_id=? AND event_day=?::date",
            Long.class, legacy, "2026-10-11")).isZero();
        capture.record(real, ProductActivityEvent.Kind.ACTIVE);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM product_activity WHERE user_id=? AND event_day=?::date",
            Long.class, real, "2026-10-11")).isEqualTo(1);
    }
}
