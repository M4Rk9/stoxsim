package com.stoxsim.integration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.instrument.domain.*;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.instrument.service.InstrumentSnapshot;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.order.domain.*;
import com.stoxsim.order.repository.PaperOrderRepository;
import com.stoxsim.onboarding.service.GuidedJourneyService;
import com.stoxsim.subscription.domain.*;
import com.stoxsim.subscription.repository.UserSubscriptionRepository;

@Testcontainers
@Transactional
@SpringBootTest(properties={"stoxsim.finwiz.enabled=false", "stoxsim.market-data.upstox.stream-enabled=false", "stoxsim.market-data.upstox.instrument-sync-on-startup=false", "stoxsim.market-data.alpaca.instrument-sync-on-startup=false"})
class GuidedJourneyIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @Container @ServiceConnection(name="redis") static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Autowired AppUserRepository users;
    @Autowired VirtualAccountRepository accounts;
    @Autowired TradableInstrumentRepository instruments;
    @Autowired PaperOrderRepository orders;
    @Autowired UserSubscriptionRepository subscriptions;
    @Autowired GuidedJourneyService journey;
    @Autowired com.stoxsim.auth.service.AccountLifecycleService lifecycle;
    @Autowired WebApplicationContext context;
    @Autowired JdbcTemplate db;
    MockMvc mvc; AppUser user; TradableInstrument instrument;
    @BeforeEach void setup() {
        user = new AppUser(UUID.randomUUID()+"@journey.test", "hash", "Learner");
        user.completeOnboardingIntroduction(); users.saveAndFlush(user);
        subscriptions.saveAndFlush(new UserSubscription(user));
        instrument = instruments.saveAndFlush(new TradableInstrument(new InstrumentSnapshot("UPSTOX", UUID.randomUUID().toString(), MarketRegion.INDIA,
            MarketExchange.NSE, "NSE_EQ", "JOURNEY", "Journey", "INE040A01034", InstrumentType.EQUITY, "INR", 1, new BigDecimal("0.05"), "NORMAL"), UUID.randomUUID(), Instant.now()));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    PaperOrder order(VirtualAccount account) {
        return orders.saveAndFlush(new PaperOrder(account, instrument, UUID.randomUUID().toString(), OrderSide.BUY, OrderType.MARKET, 1, null, BigDecimal.ZERO, LocalDate.now()));
    }
    void execute(PaperOrder order) {
        db.update("UPDATE paper_order SET status='EXECUTED' WHERE id=?", order.getId());
        db.update("INSERT INTO trade(order_id,account_id,instrument_id,side,quantity,price,gross_value,executed_at) VALUES (?,?,?,'BUY',1,100,100,now())", order.getId(), order.getAccount().getId(), instrument.getId());
    }
    @Test void onlyExecutedStandardTradesAdvanceAndCancelledOrdersCanBeRetried() {
        var sandbox = accounts.saveAndFlush(VirtualAccount.sandbox(user, SubscriptionPlan.PLUS, 1, new BigDecimal("100000")));
        execute(order(sandbox));
        assertThat(journey.state(user.getId()).nextStep()).isEqualTo("FIRST_TRADE");
        var standard = accounts.saveAndFlush(new VirtualAccount(user, MarketRegion.INDIA, new BigDecimal("500000")));
        var pending = order(standard);
        assertThat(journey.state(user.getId()).nextStep()).isEqualTo("WAITING");
        db.update("UPDATE paper_order SET status='CANCELLED' WHERE id=?", pending.getId());
        assertThat(journey.state(user.getId()).nextStep()).isEqualTo("FIRST_TRADE");
        execute(order(standard));
        assertThat(journey.state(user.getId()).nextStep()).isEqualTo("FINWIZ");
    }
    @Test void eventsAreIdempotentPrivateAndCannotBeForgedByTheClient() throws Exception {
        journey.record(user.getId(), GuidedJourneyService.Event.LEARNING);
        journey.record(user.getId(), GuidedJourneyService.Event.LEARNING);
        assertThat(db.queryForObject("SELECT count(*) FROM guided_journey_event WHERE user_id=?", Integer.class, user.getId())).isEqualTo(1);
        mvc.perform(get("/api/v1/onboarding/journey")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/onboarding/journey").with(jwt().jwt(j -> j.subject(user.getId().toString()))))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.learningViewed").value(true));
        var other = users.saveAndFlush(new AppUser(UUID.randomUUID()+"@other.test", "hash", "Other"));
        assertThat(journey.state(other.getId()).learningViewed()).isFalse();
        var exported = (java.util.List<?>) lifecycle.exportAccount(user.getId()).get("guidedJourneyEvents");
        assertThat(exported).hasSize(1);
        assertThat(exported.getFirst().toString()).contains("LEARNING", "completed_at");
        assertThat((java.util.List<?>) lifecycle.exportAccount(other.getId()).get("guidedJourneyEvents")).isEmpty();
        mvc.perform(post("/api/v1/onboarding/journey").with(jwt().jwt(j -> j.subject(user.getId().toString())))).andExpect(status().isMethodNotAllowed());
    }
    @Test void successfulFinwizAnswerCountsButValidationFailureDoesNot() throws Exception {
        mvc.perform(post("/api/v1/finwiz/ask").with(jwt().jwt(j -> j.subject(user.getId().toString()))).contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest());
        assertThat(journey.state(user.getId()).finwizAsked()).isFalse();
        mvc.perform(post("/api/v1/finwiz/ask").with(jwt().jwt(j -> j.subject(user.getId().toString()))).contentType("application/json")
            .content("{\"question\":\"Explain diversification\",\"topic\":\"LEARN\"}"))
            .andExpect(status().isOk());
        assertThat(journey.state(user.getId()).finwizAsked()).isTrue();
    }

    @Test void featureReadsDoNotRecordVisitsAndAuthenticatedPostsAreIdempotent() throws Exception {
        var other = users.saveAndFlush(new AppUser(UUID.randomUUID()+"@other.test", "hash", "Other"));
        for (String path : java.util.List.of("/api/v1/progression", "/api/v1/competitions/current")) {
            mvc.perform(get(path).with(jwt().jwt(j -> j.subject(user.getId().toString()))))
                .andExpect(status().isOk());
            mvc.perform(head(path).with(jwt().jwt(j -> j.subject(user.getId().toString()))))
                .andExpect(status().isOk());
        }
        assertThat(journey.state(user.getId()).learningViewed()).isFalse();
        assertThat(journey.state(user.getId()).competitionsViewed()).isFalse();

        for (String path : java.util.List.of("/api/v1/progression/visit", "/api/v1/competitions/current/visit")) {
            mvc.perform(post(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(jwt().jwt(j -> j.subject(user.getId().toString()))))
                .andExpect(status().isMethodNotAllowed());
            mvc.perform(post(path).header("Origin", "https://untrusted.example")
                    .with(jwt().jwt(j -> j.subject(user.getId().toString()))))
                .andExpect(status().isForbidden());
            assertThat(db.queryForObject("SELECT count(*) FROM guided_journey_event WHERE user_id=?", Integer.class, user.getId()))
                .isEqualTo(path.contains("progression") ? 0 : 1);
            for (int repeat = 0; repeat < 2; repeat++) {
                mvc.perform(post(path).param("userId", other.getId().toString())
                        .with(jwt().jwt(j -> j.subject(user.getId().toString()))))
                    .andExpect(status().isOk());
            }
        }
        assertThat(journey.state(user.getId()).learningViewed()).isTrue();
        assertThat(journey.state(user.getId()).competitionsViewed()).isTrue();
        assertThat(db.queryForObject("SELECT count(*) FROM guided_journey_event WHERE user_id=?", Integer.class, user.getId())).isEqualTo(2);
        assertThat(journey.state(other.getId()).learningViewed()).isFalse();
        assertThat(journey.state(other.getId()).competitionsViewed()).isFalse();
    }

    @Test void unsuccessfulFeatureLoadsDoNotRecordVisits() throws Exception {
        UUID missingUser = UUID.randomUUID();
        for (String path : java.util.List.of("/api/v1/progression/visit", "/api/v1/competitions/current/visit")) {
            mvc.perform(post(path).with(jwt().jwt(j -> j.subject(missingUser.toString()))))
                .andExpect(status().isUnauthorized());
        }
        assertThat(db.queryForObject("SELECT count(*) FROM guided_journey_event WHERE user_id=?", Integer.class, missingUser)).isZero();
    }
}
