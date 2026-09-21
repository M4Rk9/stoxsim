package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.analytics.service.AnalyticsService;
import com.stoxsim.analytics.api.AnalyticsOverview.OrderCount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.campus.domain.CampusInstitution;
import com.stoxsim.campus.domain.CampusMemberRole;
import com.stoxsim.campus.domain.CampusMembership;
import com.stoxsim.campus.domain.CampusVerificationRequest;
import com.stoxsim.campus.repository.CampusInstitutionRepository;
import com.stoxsim.campus.repository.CampusMembershipRepository;
import com.stoxsim.campus.repository.CampusVerificationRequestRepository;
import com.stoxsim.competition.domain.LeagueMember;
import com.stoxsim.competition.domain.PrivateLeague;
import com.stoxsim.competition.repository.CompetitionEntryRepository;
import com.stoxsim.competition.repository.CompetitionSeasonRepository;
import com.stoxsim.competition.repository.LeagueMemberRepository;
import com.stoxsim.competition.repository.PrivateLeagueRepository;
import com.stoxsim.instrument.domain.InstrumentType;
import com.stoxsim.instrument.domain.MarketExchange;
import com.stoxsim.instrument.domain.TradableInstrument;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.instrument.service.InstrumentSnapshot;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.order.domain.OrderSide;
import com.stoxsim.order.domain.OrderStatus;
import com.stoxsim.order.domain.OrderType;
import com.stoxsim.order.domain.PaperOrder;
import com.stoxsim.order.repository.PaperOrderRepository;
import com.stoxsim.order.service.TradingValidationException;
import com.stoxsim.progression.domain.MissionCompletion;
import com.stoxsim.progression.repository.LearnerProgressionRepository;
import com.stoxsim.progression.repository.MissionCompletionRepository;
import com.stoxsim.subscription.domain.SubscriptionPlan;
import com.stoxsim.subscription.domain.SubscriptionStatus;
import com.stoxsim.subscription.domain.UserSubscription;
import com.stoxsim.subscription.provider.BillingSubscriptionUpdate;
import com.stoxsim.subscription.repository.UserSubscriptionRepository;
import com.stoxsim.subscription.service.SubscriptionService;
import com.stoxsim.watchlist.domain.Watchlist;
import com.stoxsim.watchlist.domain.WatchlistItem;
import com.stoxsim.watchlist.repository.WatchlistItemRepository;
import com.stoxsim.watchlist.repository.WatchlistRepository;

@Testcontainers
@SpringBootTest(properties = {
    "stoxsim.market-data.upstox.stream-enabled=false",
    "stoxsim.market-data.upstox.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.instrument-sync-on-startup=false",
    "spring.task.scheduling.enabled=false"
})
class PostgresConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @Autowired private AppUserRepository users;
    @Autowired private VirtualAccountRepository accounts;
    @Autowired private TradableInstrumentRepository instruments;
    @Autowired private PaperOrderRepository orders;
    @Autowired private WatchlistRepository watchlists;
    @Autowired private WatchlistItemRepository watchlistItems;
    @Autowired private LearnerProgressionRepository progressions;
    @Autowired private MissionCompletionRepository missionCompletions;
    @Autowired private CompetitionSeasonRepository competitionSeasons;
    @Autowired private CompetitionEntryRepository competitionEntries;
    @Autowired private PrivateLeagueRepository privateLeagues;
    @Autowired private LeagueMemberRepository leagueMembers;
    @Autowired private UserSubscriptionRepository subscriptions;
    @Autowired private SubscriptionService subscriptionService;
    @Autowired private CampusInstitutionRepository campusInstitutions;
    @Autowired private CampusMembershipRepository campusMemberships;
    @Autowired private CampusVerificationRequestRepository campusRequests;
    @Autowired private AnalyticsService analytics;
    @Autowired private WebApplicationContext webContext;

    @Autowired private com.stoxsim.analytics.repository.ActivityRepository activityRepository;
    @Autowired private org.springframework.context.ApplicationEventPublisher activityEvents;
    @Autowired private com.stoxsim.auth.service.AccountLifecycleService lifecycle;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("""
            TRUNCATE TABLE
                watchlist_item,
                watchlist,
                account_ledger,
                trade,
                paper_order,
                holding,
                virtual_account,
                app_user,
                instrument
            RESTART IDENTITY CASCADE
            """);
    }


    @Test
    void activityCaptureAuthenticatesAndRejectsSpoofedIdentityTimeAndServerEvents() throws Exception {
        var learner = analyticsUser("capture", "2026-01-01T00:00:00Z");
        var mvc = MockMvcBuilders.webAppContextSetup(webContext).apply(springSecurity()).build();
        var endpoint = "/api/v1/analytics/events";
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(endpoint)
            .contentType("application/json").content("{\"version\":1,\"event\":\"ACTIVE\"}"))
            .andExpect(status().isUnauthorized());
        for (String payload : List.of(
            "{\"version\":1,\"event\":\"ORDER_EXECUTED\"}",
            "{\"version\":1,\"event\":\"WATCHLIST_ADDED\"}",
            "{\"version\":2,\"event\":\"ACTIVE\"}",
            "{\"version\":1,\"event\":\"ACTIVE\",\"userId\":\"other\"}",
            "{\"version\":1,\"event\":\"ACTIVE\",\"occurredAt\":\"2020-01-01\"}")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(endpoint)
                .with(jwt().jwt(token -> token.subject(learner.getId().toString())))
                .contentType("application/json").content(payload)).andExpect(status().isBadRequest());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(endpoint)
            .with(jwt().jwt(token -> token.subject(learner.getId().toString())))
            .contentType("application/json").content(" ".repeat(2048) + "{}"))
            .andExpect(status().is(413));
        for (int i = 0; i < 2; i++) mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(endpoint)
            .with(jwt().jwt(token -> token.subject(learner.getId().toString())))
            .contentType("application/json").content("{\"version\":1,\"event\":\"ACTIVE\"}"))
            .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_activity", Long.class)).isEqualTo(1);
        mvc.perform(get("/api/v1/admin/analytics/activity")
            .with(jwt().jwt(token -> token.subject(learner.getId().toString())))).andExpect(status().isForbidden());
        jdbc.update("UPDATE app_user SET platform_role='ADMIN' WHERE id=?", learner.getId());
        mvc.perform(get("/api/v1/admin/analytics/activity")
            .with(jwt().jwt(token -> token.subject(learner.getId().toString()))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.version").value("product-activity-v1"))
            .andExpect(content().string(not(containsString(learner.getEmail()))));
        mvc.perform(get("/api/v1/admin/analytics/activity?from=invalid")
            .with(jwt().jwt(token -> token.subject(learner.getId().toString())))).andExpect(status().isBadRequest());
        jdbc.update("DELETE FROM app_user WHERE id=?", learner.getId());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(endpoint)
            .with(jwt().jwt(token -> token.subject(learner.getId().toString())))
            .contentType("application/json").content("{\"version\":1,\"event\":\"ACTIVE\"}"))
            .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_activity", Long.class)).isZero();
    }

    @Test
    void activationUsesFullSevenDaysAndAllStepsWithoutRequiringActionOrder() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z"), now = Instant.parse("2026-02-01T12:00:00Z");
        var yes = analyticsUser("activated", "2026-01-01T12:00:00Z");
        var boundary = analyticsUser("too-late", "2026-01-02T00:00:00Z");
        analyticsUser("immature", "2026-01-30T00:00:00Z");
        analyticsUser("unobserved", "2025-12-31T23:59:59Z");
        recordActivity(yes, "WATCHLIST_ADDED", "2026-01-01T13:00:00Z");
        recordActivity(yes, "ORDER_SUBMITTED", "2026-01-01T14:00:00Z");
        recordActivity(yes, "STOCK_OPENED", "2026-01-01T15:00:00Z");
        recordActivity(yes, "ORDER_EXECUTED", "2026-01-02T00:00:00Z");
        recordActivity(boundary, "STOCK_OPENED", "2026-01-02T00:01:00Z");
        recordActivity(boundary, "WATCHLIST_ADDED", "2026-01-02T00:02:00Z");
        recordActivity(boundary, "ORDER_SUBMITTED", "2026-01-09T00:00:00Z"); // exactly 7d: excluded
        var result = activityRepository.activation(start.minusSeconds(1), now, start, now);
        assertThat(result.eligible()).isEqualTo(2);
        assertThat(result.immature()).isEqualTo(1);
        assertThat(result.excluded()).isEqualTo(1);
        assertThat(result.researched()).isEqualTo(2);
        assertThat(result.watchlisted()).isEqualTo(2);
        assertThat(result.activated()).isEqualTo(1);
        assertThat(result.executed()).isEqualTo(1);
        assertThat(result.percent()).isEqualTo(50.0);
        assertThat(result.medianHours()).isEqualTo(3.0);
    }

    @Test
    void retentionUsesExactUtcReturnDayWithDistinctMatureDenominators() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z"), end = Instant.parse("2026-02-01T12:00:00Z");
        var returned = analyticsUser("returned", "2026-01-01T23:59:59Z");
        var fillOnly = analyticsUser("background-fill", "2026-01-01T12:00:00Z");
        analyticsUser("recent", "2026-01-31T00:00:00Z");
        var owner = analyticsUser("ignored-owner", "2026-01-01T12:00:00Z");
        jdbc.update("UPDATE app_user SET platform_role='ADMIN' WHERE id=?", owner.getId());
        recordActivity(owner, "ACTIVE", "2026-01-02T12:00:00Z");
        recordActivity(returned, "ACTIVE", "2026-01-02T00:00:00Z");
        recordActivity(returned, "STOCK_OPENED", "2026-01-02T23:59:59Z");
        recordActivity(returned, "ACTIVE", "2026-01-07T12:00:00Z"); // D6, not D7
        recordActivity(returned, "ACTIVE", "2026-01-31T23:59:59Z"); // D30
        recordActivity(fillOnly, "ORDER_EXECUTED", "2026-01-02T12:00:00Z");
        var d1 = activityRepository.retention(1, start, end, start, LocalDate.of(2026,2,1));
        var d7 = activityRepository.retention(7, start, end, start, LocalDate.of(2026,2,1));
        var d30 = activityRepository.retention(30, start, end, start, LocalDate.of(2026,2,1));
        assertThat(d1.eligible()).isEqualTo(2);
        assertThat(d1.returned()).isEqualTo(1);
        assertThat(d1.immature()).isEqualTo(1);
        assertThat(d1.percent()).isEqualTo(50.0);
        assertThat(d7.returned()).isZero();
        assertThat(d30.returned()).isEqualTo(1);
        assertThat(activityRepository.active(Instant.parse("2026-01-02T00:00:00Z"), Instant.parse("2026-01-03T00:00:00Z"))).isEqualTo(1);
        var empty = activityRepository.retention(30, end, end.plusSeconds(1), start, LocalDate.of(2026,2,1));
        assertThat(empty.eligible()).isZero();
        assertThat(empty.percent()).isNull();
    }

    @Test
    void activityIsDeduplicatedConcurrentlyAndExcludedForAdministrators() throws Exception {
        var learner = analyticsUser("concurrent-activity", "2026-01-01T00:00:00Z");
        var service = new com.stoxsim.analytics.service.ProductActivityService(jdbc,
            java.time.Clock.fixed(Instant.parse("2026-02-01T12:00:00Z"), java.time.ZoneOffset.UTC));
        try (var pool = Executors.newFixedThreadPool(4)) {
            var jobs = new java.util.ArrayList<Callable<Void>>();
            for (int i = 0; i < 12; i++) jobs.add(() -> {
                service.record(learner.getId(), com.stoxsim.analytics.service.ProductActivityEvent.Kind.ACTIVE); return null;
            });
            for (var task : pool.invokeAll(jobs)) task.get(10, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_activity", Long.class)).isEqualTo(1);
        jdbc.update("UPDATE app_user SET platform_role='ADMIN' WHERE id=?", learner.getId());
        service.record(learner.getId(), com.stoxsim.analytics.service.ProductActivityEvent.Kind.STOCK_OPENED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_activity", Long.class)).isEqualTo(1);
    }

    @Test
    void rolledBackDomainActionsLeaveNoActivityAndCommittedActionsDo() {
        var learner = user("transaction-activity");
        transactions.executeWithoutResult(status -> {
            activityEvents.publishEvent(new com.stoxsim.analytics.service.ProductActivityEvent(learner.getId(),
                com.stoxsim.analytics.service.ProductActivityEvent.Kind.WATCHLIST_ADDED));
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_activity", Long.class)).isZero();
        transactions.executeWithoutResult(status -> activityEvents.publishEvent(
            new com.stoxsim.analytics.service.ProductActivityEvent(learner.getId(),
                com.stoxsim.analytics.service.ProductActivityEvent.Kind.WATCHLIST_ADDED)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_activity", Long.class)).isEqualTo(1);
    }

    @Test
    void activityExportAndDeletionAndRetentionRespectTheBoundary() {
        var learner = user("activity-lifecycle");
        Instant now = Instant.now();
        LocalDate today = now.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        recordActivity(learner, "ACTIVE", today.minusDays(180).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toString());
        recordActivity(learner, "ACTIVE", today.minusDays(179).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toString());
        var exported = (List<?>) lifecycle.exportAccount(learner.getId()).get("productActivity");
        assertThat(exported).hasSize(1);
        new com.stoxsim.analytics.service.ProductActivityService(jdbc,
            java.time.Clock.fixed(now, java.time.ZoneOffset.UTC)).purgeExpired();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_activity", Long.class)).isEqualTo(1);
        jdbc.update("DELETE FROM app_user WHERE id=?", learner.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_activity", Long.class)).isZero();
    }

    private void recordActivity(AppUser user, String kind, String when) {
        var at = Instant.parse(when);
        new com.stoxsim.analytics.service.ProductActivityService(jdbc, java.time.Clock.fixed(at, java.time.ZoneOffset.UTC))
            .record(user.getId(), com.stoxsim.analytics.service.ProductActivityEvent.Kind.valueOf(kind));
    }

    @Test
    void analyticsCountsUtcSignupCohortOnceAndExcludesSandboxesAdminsAndDeletedUsers() {
        AppUser owner = analyticsUser("owner", "2020-01-01T08:00:00Z");
        jdbc.update("UPDATE app_user SET platform_role = 'ADMIN' WHERE id = ?", owner.getId());
        AppUser first = analyticsUser("first", "2020-01-01T00:00:00Z");
        AppUser last = analyticsUser("last", "2020-01-02T23:59:59.999999Z");
        AppUser before = analyticsUser("before", "2019-12-31T23:59:59Z");
        AppUser after = analyticsUser("after", "2020-01-03T00:00:00Z");
        AppUser deleted = analyticsUser("deleted", "2020-01-01T08:00:00Z");
        jdbc.update("UPDATE app_user SET email_verified_at = ? WHERE id = ?",
            Timestamp.from(Instant.parse("2020-01-02T00:00:00Z")), first.getId());

        TradableInstrument instrument = instrument("ANALYTICS", "analytics-test");
        VirtualAccount india = accounts.save(new VirtualAccount(first, MarketRegion.INDIA, new BigDecimal("500000")));
        VirtualAccount usa = accounts.save(new VirtualAccount(first, MarketRegion.UNITED_STATES, new BigDecimal("10000")));
        VirtualAccount sandbox = accounts.save(VirtualAccount.sandbox(last, SubscriptionPlan.PLUS, 1, new BigDecimal("2500000")));
        // Multiple fills (and both markets) still count as one converted learner.
        analyticsOrder(india, instrument, "2020-01-01T00:00:00Z", "EXECUTED", "2020-01-05T10:00:00Z");
        analyticsOrder(india, instrument, "2020-01-02T12:00:00Z", "EXECUTED", "2020-01-05T11:00:00Z");
        analyticsOrder(usa, instrument, "2020-01-02T13:00:00Z", "EXECUTED", "2020-01-05T12:00:00Z");
        analyticsOrder(sandbox, instrument, "2020-01-02T12:00:00Z", "EXECUTED", "2020-01-02T13:00:00Z");
        analyticsOrder(accounts.save(new VirtualAccount(before, MarketRegion.INDIA, new BigDecimal("500000"))),
            instrument, "2020-01-02T12:00:00Z", "REJECTED", null);
        analyticsOrder(accounts.save(new VirtualAccount(after, MarketRegion.INDIA, new BigDecimal("500000"))),
            instrument, "2020-01-03T00:00:00Z", "OPEN", null);
        analyticsOrder(accounts.save(new VirtualAccount(owner, MarketRegion.INDIA, new BigDecimal("500000"))),
            instrument, "2020-01-01T09:00:00Z", "EXECUTED", "2020-01-01T10:00:00Z");
        analyticsOrder(accounts.save(new VirtualAccount(deleted, MarketRegion.INDIA, new BigDecimal("500000"))),
            instrument, "2020-01-01T09:00:00Z", "EXECUTED", "2020-01-01T10:00:00Z");
        jdbc.update("DELETE FROM app_user WHERE id = ?", deleted.getId());

        var result = analytics.overview(owner.getId(), LocalDate.of(2020, 1, 1), LocalDate.of(2020, 1, 2));
        assertThat(result.users().registered()).isEqualTo(4);
        assertThat(result.users().emailVerified()).isEqualTo(1);
        assertThat(result.cohort().registered()).isEqualTo(2);
        assertThat(result.cohort().firstTradeCompleted()).isEqualTo(1);
        assertThat(result.cohort().firstTradePercent()).isEqualTo(50.0);
        assertThat(result.signups()).extracting(day -> day.registered()).containsExactly(1L, 1L);
        assertThat(result.orders()).containsExactlyInAnyOrder(
            new OrderCount("INDIA", "EXECUTED", 2), new OrderCount("INDIA", "REJECTED", 1),
            new OrderCount("UNITED_STATES", "EXECUTED", 1));

        var empty = analytics.overview(owner.getId(), LocalDate.of(2018, 1, 1), LocalDate.of(2018, 1, 1));
        assertThat(empty.cohort().registered()).isZero();
        assertThat(empty.cohort().firstTradePercent()).isNull();
        assertThat(empty.orders()).isEmpty();
        assertThat(empty.signups().getFirst().registered()).isZero();
    }

    @Test
    void analyticsHttpBoundaryRequiresDatabaseAdminAndReturnsNoPersonalData() throws Exception {
        var mvc = MockMvcBuilders.webAppContextSetup(webContext)
            .apply(springSecurity())
            .build();
        String path = "/api/v1/admin/analytics/overview";
        AppUser owner = analyticsUser("http-owner", "2020-01-01T00:00:00Z");
        var identity = jwt().jwt(jwt -> jwt.subject(owner.getId().toString()).claim("platformAdmin", true));
        mvc.perform(get(path))
            .andExpect(status().isUnauthorized());
        // A forged admin claim cannot elevate a database USER.
        mvc.perform(get(path).with(identity))
            .andExpect(status().isForbidden());
        jdbc.update("UPDATE app_user SET platform_role = 'ADMIN' WHERE id = ?", owner.getId());
        mvc.perform(get(path).with(identity)
                .param("from", "2020-01-01").param("to", "2020-01-02"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.version").value("owner-analytics-v1"))
            .andExpect(content().string(
                not(containsString(owner.getEmail()))))
            .andExpect(content().string(
                not(containsString(owner.getId().toString()))));
        mvc.perform(get(path).with(identity)
                .param("from", "not-a-date"))
            .andExpect(status().isBadRequest());
        jdbc.update("UPDATE app_user SET platform_role = 'USER' WHERE id = ?", owner.getId());
        mvc.perform(get(path).with(identity))
            .andExpect(status().isForbidden());
        jdbc.update("DELETE FROM app_user WHERE id = ?", owner.getId());
        mvc.perform(get(path).with(identity))
            .andExpect(status().isUnauthorized());
    }

    private AppUser analyticsUser(String label, String createdAt) {
        AppUser user = user(label);
        jdbc.update("UPDATE app_user SET created_at = ? WHERE id = ?",
            Timestamp.from(Instant.parse(createdAt)), user.getId());
        return user;
    }

    private void analyticsOrder(VirtualAccount account, TradableInstrument instrument,
        String createdAt, String status, String executedAt) {
        PaperOrder order = orders.save(new PaperOrder(account, instrument, UUID.randomUUID().toString(),
            OrderSide.BUY, OrderType.MARKET, 1, null, BigDecimal.ZERO, LocalDate.of(2020, 1, 1)));
        jdbc.update("UPDATE paper_order SET created_at = ?, status = ?, executed_at = ? WHERE id = ?",
            Timestamp.from(Instant.parse(createdAt)), status,
            executedAt == null ? null : Timestamp.from(Instant.parse(executedAt)), order.getId());
    }

    @Test
    void flywaySchemaPersistsWatchlistsAndRejectsDuplicateMembership() {
        AppUser user = user("watchlist");
        TradableInstrument instrument = instrument("RELIANCE", "NSE_EQ|INE002A01018");
        Watchlist watchlist = watchlists.save(new Watchlist(user, "My Watchlist", true));
        watchlistItems.saveAndFlush(new WatchlistItem(watchlist, instrument));

        var saved = watchlistItems.findAllByWatchlistIdOrderByCreatedAtDesc(watchlist.getId());

        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getInstrument().getTradingSymbol()).isEqualTo("RELIANCE");
        assertThatThrownBy(() -> watchlistItems.saveAndFlush(
            new WatchlistItem(watchlist, instrument)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void flywaySchemaAcceptsEverySupportedUnitedStatesExchange() {
        List<MarketExchange> supported = List.of(
            MarketExchange.NASDAQ,
            MarketExchange.NYSE,
            MarketExchange.NYSE_ARCA,
            MarketExchange.AMEX,
            MarketExchange.CBOE
        );

        for (MarketExchange exchange : supported) {
            String symbol = exchange.name().replace("_", "");
            instruments.saveAndFlush(new TradableInstrument(
                new InstrumentSnapshot(
                    "ALPACA",
                    symbol,
                    MarketRegion.UNITED_STATES,
                    exchange,
                    "US_EQUITY",
                    symbol,
                    symbol + " Test Security",
                    null,
                    InstrumentType.EQUITY,
                    "USD",
                    1,
                    new BigDecimal("0.0001"),
                    "test-asset-id-" + symbol
                ),
                UUID.randomUUID(),
                Instant.now()
            ));
        }

        Integer persisted = jdbc.queryForObject(
            "SELECT COUNT(*) FROM instrument WHERE provider = 'ALPACA'",
            Integer.class
        );
        assertThat(persisted).isEqualTo(supported.size());
    }

    @Test
    void paidSandboxCannotReplaceTheStandardCompetitionAccount() {
        AppUser user = user("sandbox-isolation");
        VirtualAccount standard = accounts.saveAndFlush(new VirtualAccount(
            user,
            MarketRegion.INDIA,
            SubscriptionPlan.STANDARD_COMPETITIVE_CAPITAL_INR
        ));
        VirtualAccount sandbox = accounts.saveAndFlush(VirtualAccount.sandbox(
            user,
            SubscriptionPlan.PLUS,
            1,
            SubscriptionPlan.PLUS.sandboxCapitalInr()
        ));

        VirtualAccount selected = accounts
            .findByUserIdAndMarketRegion(user.getId(), MarketRegion.INDIA)
            .orElseThrow();

        assertThat(selected.getId()).isEqualTo(standard.getId());
        assertThat(selected.isLeaderboardEligible()).isTrue();
        assertThat(sandbox.isLeaderboardEligible()).isFalse();
        assertThat(accounts.findSandboxesByUserId(user.getId()))
            .extracting(VirtualAccount::getId)
            .containsExactly(sandbox.getId());
        assertThatThrownBy(() -> accounts.saveAndFlush(new VirtualAccount(
            user,
            MarketRegion.INDIA,
            SubscriptionPlan.STANDARD_COMPETITIVE_CAPITAL_INR
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sandboxSlotsArePlanScopedAndProvisioningKeysAreIdempotent() {
        AppUser user = user("plan-scoped-sandboxes");
        VirtualAccount plus = accounts.saveAndFlush(VirtualAccount.sandbox(
            user,
            SubscriptionPlan.PLUS,
            1,
            SubscriptionPlan.PLUS.sandboxCapitalInr()
        ));
        VirtualAccount pro = accounts.saveAndFlush(VirtualAccount.sandbox(
            user,
            SubscriptionPlan.PRO,
            1,
            SubscriptionPlan.PRO.sandboxCapitalInr()
        ));
        VirtualAccount provisioned = accounts.saveAndFlush(VirtualAccount.sandbox(
            user,
            SubscriptionPlan.PRO,
            2,
            SubscriptionPlan.PRO.sandboxCapitalInr(),
            "provision-once"
        ));

        assertThat(accounts.findSandboxesByUserId(user.getId()))
            .extracting(VirtualAccount::getId)
            .containsExactly(plus.getId(), pro.getId(), provisioned.getId());
        assertThat(accounts.findByUserIdAndProvisioningKey(
            user.getId(),
            "provision-once"
        ).orElseThrow().getId()).isEqualTo(provisioned.getId());
        assertThatThrownBy(() -> accounts.saveAndFlush(VirtualAccount.sandbox(
            user,
            SubscriptionPlan.PRO,
            3,
            SubscriptionPlan.PRO.sandboxCapitalInr(),
            "provision-once"
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void subscriptionLockAllocatesDifferentSlotsToConcurrentProRequests() throws Exception {
        AppUser user = user("concurrent-provisioning");
        subscriptions.saveAndFlush(new UserSubscription(user));
        Instant now = Instant.now();
        subscriptionService.applyProviderUpdate(new BillingSubscriptionUpdate(
            user.getId(),
            SubscriptionPlan.PRO,
            SubscriptionStatus.ACTIVE,
            "integration-provider",
            "customer-" + user.getId(),
            "subscription-" + user.getId(),
            now.plusSeconds(2_592_000)
        ));

        List<Boolean> results = runConcurrently(() -> {
            subscriptionService.createAdditionalSandbox(
                user.getId(),
                UUID.randomUUID().toString()
            );
            return true;
        });

        assertThat(results).containsExactly(true, true);
        assertThat(accounts.findSandboxesByUserIdAndPlan(
            user.getId(),
            SubscriptionPlan.PRO
        ))
            .extracting(VirtualAccount::getSandboxSlot)
            .containsExactly(1, 2, 3);
    }

    @Test
    void campusSchemaPersistsVerifiedInstitutionAndOrganizerMembership() {
        AppUser admin = user("campus-admin");
        AppUser organizer = user("campus-organizer");
        Instant now = Instant.now();
        CampusInstitution institution = campusInstitutions.saveAndFlush(
            new CampusInstitution(
                "Birla Institute of Technology, Mesra",
                "birla institute of technology, mesra",
                "bitmesra.ac.in",
                "https://www.bitmesra.ac.in",
                admin,
                now
            )
        );
        CampusMembership membership = campusMemberships.saveAndFlush(
            new CampusMembership(
                institution,
                organizer,
                CampusMemberRole.ORGANIZER,
                now
            )
        );

        assertThat(campusMemberships.findByUserId(organizer.getId()))
            .map(CampusMembership::getId)
            .contains(membership.getId());
        assertThatThrownBy(() -> campusInstitutions.saveAndFlush(
            new CampusInstitution(
                "Different Institution Name",
                "different institution name",
                "bitmesra.ac.in",
                null,
                admin,
                now
            )
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void campusSchemaAllowsOnlyOnePendingRequestPerLearner() {
        AppUser learner = user("campus-requester");
        Instant now = Instant.now();
        campusRequests.saveAndFlush(new CampusVerificationRequest(
            learner,
            "First Institution",
            "first institution",
            "first.example.edu",
            null,
            now
        ));

        assertThatThrownBy(() -> campusRequests.saveAndFlush(
            new CampusVerificationRequest(
                learner,
                "Second Institution",
                "second institution",
                "second.example.edu",
                null,
                now.plusSeconds(1)
            )
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void accountScopedOrdersCannotLeakIntoStandardRoutes() {
        AppUser user = user("scoped-orders");
        VirtualAccount standard = accounts.saveAndFlush(new VirtualAccount(
            user,
            MarketRegion.INDIA,
            SubscriptionPlan.STANDARD_COMPETITIVE_CAPITAL_INR
        ));
        VirtualAccount sandbox = accounts.saveAndFlush(VirtualAccount.sandbox(
            user,
            SubscriptionPlan.PLUS,
            1,
            SubscriptionPlan.PLUS.sandboxCapitalInr()
        ));
        TradableInstrument instrument = instrument("RELIANCE", "NSE_EQ|SCOPED");
        PaperOrder standardOrder = orders.saveAndFlush(new PaperOrder(
            standard,
            instrument,
            "standard-order",
            OrderSide.BUY,
            OrderType.MARKET,
            1,
            null,
            BigDecimal.ZERO,
            LocalDate.now()
        ));
        PaperOrder sandboxOrder = orders.saveAndFlush(new PaperOrder(
            sandbox,
            instrument,
            "sandbox-order",
            OrderSide.BUY,
            OrderType.MARKET,
            1,
            null,
            BigDecimal.ZERO,
            LocalDate.now()
        ));

        assertThat(orders
            .findAllByAccountUserIdAndAccountMarketRegionOrderByCreatedAtDesc(
                user.getId(),
                MarketRegion.INDIA
            ))
            .extracting(PaperOrder::getId)
            .containsExactly(standardOrder.getId());
        assertThat(orders.findByIdAndAccountUserId(
            sandboxOrder.getId(),
            user.getId()
        )).isEmpty();
        assertThat(orders.findAllOwnedByAccountId(user.getId(), sandbox.getId()))
            .extracting(PaperOrder::getId)
            .containsExactly(sandboxOrder.getId());
        assertThat(orders.findOwnedByIdAndAccountId(
            user.getId(),
            standard.getId(),
            sandboxOrder.getId()
        )).isEmpty();
    }

    @Test
    void freeSubscriptionCanBeResolvedThroughItsOwnedUser() {
        AppUser user = user("subscription-owner");
        subscriptions.saveAndFlush(new UserSubscription(user));

        UserSubscription saved = subscriptions.findByUserId(user.getId()).orElseThrow();
        assertThat(saved.getPlan()).isEqualTo(SubscriptionPlan.FREE);
    }

    @Test
    void progressionInitializationAndMissionAwardsAreIdempotent() {
        AppUser user = user("progression");
        Instant now = Instant.now();

        transactions.executeWithoutResult(status -> {
            progressions.ensureExists(user.getId(), now);
            progressions.ensureExists(user.getId(), now);
        });
        assertThat(progressions.findById(user.getId())).isPresent();

        missionCompletions.saveAndFlush(new MissionCompletion(
            user.getId(),
            "FIRST_ORDER",
            50,
            now
        ));
        assertThatThrownBy(() -> missionCompletions.saveAndFlush(new MissionCompletion(
            user.getId(),
            "FIRST_ORDER",
            50,
            now
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void competitionEnrollmentAndPrivateLeagueMembershipAreUnique() {
        AppUser user = user("competition");
        Instant now = Instant.now();
        String seasonCode = "integration-" + UUID.randomUUID().toString().substring(0, 16);
        transactions.executeWithoutResult(status -> {
            competitionSeasons.ensureSeason(
                seasonCode,
                "Integration Learning Season",
                now.minusSeconds(60),
                now.plusSeconds(3600),
                now
            );
        });
        var season = competitionSeasons.findByCode(seasonCode).orElseThrow();

        transactions.executeWithoutResult(status -> {
            competitionEntries.ensureEntry(
                season.getId(), user.getId(), new BigDecimal("500000.00"), "CLOSED", now
            );
            competitionEntries.ensureEntry(
                season.getId(), user.getId(), new BigDecimal("500000.00"), "CLOSED", now
            );
        });
        assertThat(competitionEntries.countBySeasonId(season.getId())).isEqualTo(1);

        String inviteHash = "a".repeat(64);
        PrivateLeague league = privateLeagues.saveAndFlush(new PrivateLeague(
            season, user, "Integration League", inviteHash, 25, now
        ));
        leagueMembers.saveAndFlush(new LeagueMember(
            league, user, LeagueMember.Role.OWNER, now
        ));

        assertThatThrownBy(() -> leagueMembers.saveAndFlush(new LeagueMember(
            league, user, LeagueMember.Role.MEMBER, now
        ))).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> privateLeagues.saveAndFlush(new PrivateLeague(
            season, user, "Duplicate Invite", inviteHash, 25, now
        ))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void competitionEntryLockKeepsTheOwnedLeagueCapAtomic() throws Exception {
        AppUser user = user("league-cap");
        Instant now = Instant.now();
        String seasonCode = "cap-" + UUID.randomUUID().toString().substring(0, 16);
        transactions.executeWithoutResult(status -> competitionSeasons.ensureSeason(
            seasonCode,
            "League Cap Test Season",
            now.minusSeconds(60),
            now.plusSeconds(3600),
            now
        ));
        var season = competitionSeasons.findByCode(seasonCode).orElseThrow();
        transactions.executeWithoutResult(status -> competitionEntries.ensureEntry(
            season.getId(), user.getId(), new BigDecimal("500000.00"), "CLOSED", now
        ));
        for (int index = 0; index < 4; index++) {
            privateLeagues.saveAndFlush(new PrivateLeague(
                season,
                user,
                "Existing League " + index,
                String.format("%064d", index),
                25,
                now
            ));
        }

        List<Boolean> results = runConcurrently(() -> transactions.execute(status -> {
            competitionEntries.findForUpdate(season.getId(), user.getId()).orElseThrow();
            if (privateLeagues.countByOwnerIdAndSeasonId(user.getId(), season.getId()) >= 5) {
                return false;
            }
            privateLeagues.save(new PrivateLeague(
                season,
                user,
                "Concurrent League",
                UUID.randomUUID().toString().replace("-", ""),
                25,
                now
            ));
            return true;
        }));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(privateLeagues.countByOwnerIdAndSeasonId(user.getId(), season.getId()))
            .isEqualTo(5);
    }

    @Test
    void accountLockPreventsTwoOrdersFromSpendingTheSameCash() throws Exception {
        AppUser user = user("cash-lock");
        VirtualAccount account = accounts.save(new VirtualAccount(
            user,
            MarketRegion.INDIA,
            new BigDecimal("100.00")
        ));

        List<Boolean> results = runConcurrently(() -> {
            try {
                return transactions.execute(status -> {
                    VirtualAccount locked = accounts.findByIdForUpdate(account.getId()).orElseThrow();
                    locked.reserveCash(new BigDecimal("80.00"));
                    return true;
                });
            } catch (TradingValidationException expected) {
                return false;
            }
        });

        assertThat(results).containsExactlyInAnyOrder(true, false);
        VirtualAccount reloaded = accounts.findById(account.getId()).orElseThrow();
        assertThat(reloaded.getAvailableCash()).isEqualByComparingTo("20.0000");
        assertThat(reloaded.getBlockedCash()).isEqualByComparingTo("80.0000");
    }

    @Test
    void orderLockAllowsOnlyOneExecutionTransition() throws Exception {
        AppUser user = user("order-lock");
        VirtualAccount account = accounts.save(new VirtualAccount(
            user,
            MarketRegion.INDIA,
            new BigDecimal("500000.00")
        ));
        TradableInstrument instrument = instrument("TCS", "NSE_EQ|INE467B01029");
        PaperOrder order = orders.save(new PaperOrder(
            account,
            instrument,
            "concurrent-fill",
            OrderSide.BUY,
            OrderType.LIMIT,
            1,
            new BigDecimal("3500.00"),
            new BigDecimal("3500.00"),
            LocalDate.now()
        ));

        List<Boolean> results = runConcurrently(() -> transactions.execute(status -> {
            PaperOrder locked = orders.findByIdForUpdate(order.getId()).orElseThrow();
            if (!locked.isOpen()) {
                return false;
            }
            locked.markExecuted(
                new BigDecimal("3499.95"),
                new BigDecimal("3499.95"),
                Instant.now()
            );
            return true;
        }));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(orders.findById(order.getId()).orElseThrow().getStatus())
            .isEqualTo(OrderStatus.EXECUTED);
    }

    private List<Boolean> runConcurrently(Callable<Boolean> operation) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> synchronizedOperation = () -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent test did not start in time");
                }
                return operation.call();
            };
            Future<Boolean> first = executor.submit(synchronizedOperation);
            Future<Boolean> second = executor.submit(synchronizedOperation);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private AppUser user(String prefix) {
        return users.save(new AppUser(
            prefix + "-" + UUID.randomUUID() + "@stoxsim.test",
            "integration-test-password-hash",
            "Integration Test"
        ));
    }

    private TradableInstrument instrument(String symbol, String instrumentKey) {
        return instruments.save(new TradableInstrument(
            new InstrumentSnapshot(
                "UPSTOX",
                instrumentKey,
                MarketRegion.INDIA,
                MarketExchange.NSE,
                "NSE_EQ",
                symbol,
                symbol + " Limited",
                null,
                InstrumentType.EQUITY,
                "INR",
                1,
                new BigDecimal("0.05"),
                "NORMAL"
            ),
            UUID.randomUUID(),
            Instant.now()
        ));
    }
}
