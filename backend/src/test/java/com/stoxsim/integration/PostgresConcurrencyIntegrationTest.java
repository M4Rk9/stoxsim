package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stoxsim.account.domain.VirtualAccount;
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
