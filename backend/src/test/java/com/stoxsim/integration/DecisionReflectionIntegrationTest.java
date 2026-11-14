package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.finwiz.api.*;
import com.stoxsim.finwiz.service.FinwizService;
import com.stoxsim.instrument.domain.*;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.instrument.service.InstrumentSnapshot;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.order.domain.*;
import com.stoxsim.order.repository.PaperOrderRepository;
import com.stoxsim.reflection.api.ReflectionRequest;
import com.stoxsim.reflection.service.ReflectionService;

@Testcontainers
@SpringBootTest(properties = {"stoxsim.finwiz.enabled=false", "stoxsim.security.rate-limit.enabled=false",
    "stoxsim.market-data.upstox.stream-enabled=false", "stoxsim.market-data.upstox.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.instrument-sync-on-startup=false", "stoxsim.market-data.alpaca.polling-enabled=false"})
class DecisionReflectionIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @Autowired AppUserRepository users;
    @Autowired VirtualAccountRepository accounts;
    @Autowired TradableInstrumentRepository instruments;
    @Autowired PaperOrderRepository orders;
    @Autowired ReflectionService reflections;
    @Autowired JdbcTemplate db;
    @Autowired WebApplicationContext context;
    @MockitoBean FinwizService finwiz;
    AppUser user, other;
    MockMvc mvc;

    @BeforeEach void setup() {
        user = users.saveAndFlush(new AppUser(UUID.randomUUID() + "@reflection.test", "hash", "Learner"));
        other = users.saveAndFlush(new AppUser(UUID.randomUUID() + "@reflection.test", "hash", "Other"));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    ReflectionRequest note(UUID account, UUID order) {
        return new ReflectionRequest(account, order, "My private reason", "Demand holds", "Concentration", "Learn the process", "Review later");
    }

    @Test void notesArePrivateAndCrudNeverCallsAi() throws Exception {
        var saved = reflections.create(user.getId(), note(null, null));
        assertThat(reflections.list(other.getId())).isEmpty();
        mvc.perform(get("/api/v1/reflections")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/reflections/" + saved.id()).with(jwt().jwt(j -> j.subject(other.getId().toString())))
            .contentType("application/json").content("{\"reason\":\"Overwrite\"}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/reflections/" + saved.id()).with(jwt().jwt(j -> j.subject(other.getId().toString()))))
            .andExpect(status().isNotFound());
        reflections.update(user.getId(), saved.id(), new ReflectionRequest(null, null, "Updated reason", null, null, null, "New reflection"));
        assertThat(reflections.get(user.getId(), saved.id()).reflection()).isEqualTo("New reflection");
        assertThat(db.queryForObject("SELECT count(*) FROM product_activity WHERE user_id=? AND event_name='REFLECTION_SAVED'", Integer.class, user.getId())).isEqualTo(1);
        reflections.delete(user.getId(), saved.id());
        assertThat(reflections.list(user.getId())).isEmpty();
        verifyNoInteractions(finwiz);
    }

    @Test void assistanceRequiresExplicitConsentAndOwnershipOnEveryRequest() throws Exception {
        var saved = reflections.create(user.getId(), note(null, null));
        String path = "/api/v1/reflections/" + saved.id() + "/assistance";
        for (String body : List.of("{}", "{\"consent\":false}", "{\"consent\":null}")) {
            mvc.perform(post(path).with(jwt().jwt(j -> j.subject(user.getId().toString())))
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(path).with(jwt().jwt(j -> j.subject(other.getId().toString())))
            .contentType("application/json").content("{\"consent\":true}")).andExpect(status().isNotFound());
        verifyNoInteractions(finwiz);
        when(finwiz.ask(eq(user.getId()), any())).thenReturn(new FinwizResponse("Review assumptions", "STOXSIM_EDUCATIONAL_FALLBACK",
            "deterministic-v1", false, Instant.now(), null, List.of(), "Education only", "BASIC"));
        mvc.perform(post(path).with(jwt().jwt(j -> j.subject(user.getId().toString())))
            .contentType("application/json").content("{\"consent\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.finwizTier").value("BASIC"));
        var request = ArgumentCaptor.forClass(FinwizRequest.class);
        verify(finwiz).ask(eq(user.getId()), request.capture());
        assertThat(request.getValue().question()).contains("unverified claims", "My private reason").hasSizeLessThanOrEqualTo(2000);
        mvc.perform(post(path).with(jwt().jwt(j -> j.subject(user.getId().toString())))
            .contentType("application/json").content("{\"consent\":false}")).andExpect(status().isBadRequest());
        verifyNoMoreInteractions(finwiz);
    }

    @Test void linksCannotCrossAccountsOrOwnersAndNeverChangeFinancialState() throws Exception {
        var mine = accounts.saveAndFlush(new VirtualAccount(user, MarketRegion.INDIA, new BigDecimal("500000")));
        var theirs = accounts.saveAndFlush(new VirtualAccount(other, MarketRegion.INDIA, new BigDecimal("500000")));
        var instrument = instruments.saveAndFlush(new TradableInstrument(new InstrumentSnapshot("UPSTOX", UUID.randomUUID().toString(), MarketRegion.INDIA,
            MarketExchange.NSE, "NSE_EQ", "REFLECT", "Reflection", "INE040A01034", InstrumentType.EQUITY, "INR", 1, new BigDecimal("0.05"), "NORMAL"), UUID.randomUUID(), Instant.now()));
        var theirOrder = orders.saveAndFlush(new PaperOrder(theirs, instrument, UUID.randomUUID().toString(), OrderSide.BUY, OrderType.MARKET, 1, null, BigDecimal.ZERO, LocalDate.now()));
        var myOrder = orders.saveAndFlush(new PaperOrder(mine, instrument, UUID.randomUUID().toString(), OrderSide.BUY, OrderType.MARKET, 1, null, BigDecimal.ZERO, LocalDate.now()));
        assertThatThrownBy(() -> reflections.create(user.getId(), note(theirs.getId(), null))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> reflections.create(user.getId(), note(mine.getId(), theirOrder.getId()))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        var saved = reflections.create(user.getId(), note(null, myOrder.getId()));
        assertThat(saved.accountId()).isEqualTo(mine.getId());
        var unlinked = reflections.create(user.getId(), note(null, null));
        String linkPath = "/api/v1/reflections/" + unlinked.id() + "/order";
        String myOrderBody = "{\"orderId\":\"" + myOrder.getId() + "\"}";
        mvc.perform(post(linkPath).contentType("application/json").content(myOrderBody))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(linkPath).with(jwt().jwt(j -> j.subject(other.getId().toString())))
            .contentType("application/json").content(myOrderBody)).andExpect(status().isNotFound());
        mvc.perform(post(linkPath).with(jwt().jwt(j -> j.subject(user.getId().toString())))
            .contentType("application/json").content("{\"orderId\":\"" + theirOrder.getId() + "\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(post(linkPath).with(jwt().jwt(j -> j.subject(user.getId().toString())))
            .contentType("application/json").content("{\"orderId\":\"not-a-uuid\"}")).andExpect(status().isBadRequest());
        assertThat(reflections.get(user.getId(), unlinked.id()).orderId()).isNull();
        mvc.perform(post(linkPath).with(jwt().jwt(j -> j.subject(user.getId().toString())))
            .contentType("application/json").content(myOrderBody))
            .andExpect(status().isOk()).andExpect(jsonPath("$.orderId").value(myOrder.getId().toString()));
        assertThatThrownBy(() -> reflections.linkOrder(other.getId(), unlinked.id(), theirOrder.getId()))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(() -> reflections.linkOrder(user.getId(), unlinked.id(), theirOrder.getId()))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(reflections.linkOrder(user.getId(), unlinked.id(), myOrder.getId()).linkedOrder().status()).isEqualTo("OPEN");
        assertThat(reflections.linkOrder(user.getId(), unlinked.id(), myOrder.getId()).orderId()).isEqualTo(myOrder.getId());
        var wrong = reflections.create(user.getId(), new ReflectionRequest(null, null, "Different company", "", "", "", "",
            new com.stoxsim.reflection.api.StrategyPlan("NSE", "OTHER", "", "", "", null), false));
        assertThatThrownBy(() -> reflections.linkOrder(user.getId(), wrong.id(), myOrder.getId()))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(reflections.get(user.getId(), wrong.id()).orderId()).isNull();
        var second = accounts.saveAndFlush(new VirtualAccount(user, MarketRegion.UNITED_STATES, new BigDecimal("10000")));
        assertThatThrownBy(() -> reflections.create(user.getId(), note(second.getId(), myOrder.getId()))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(db.queryForObject("SELECT available_cash FROM virtual_account WHERE id=?", BigDecimal.class, mine.getId())).isEqualByComparingTo("500000");
        // Even a direct persistence write cannot attach another user's order to this account.
        assertThatThrownBy(() -> db.update("UPDATE decision_reflection SET order_id=? WHERE id=?", theirOrder.getId(), saved.id()))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void validationAndAccountDeletionProtectPrivateNotes() throws Exception {
        for (String reason : List.of("", " ", "a".repeat(2001))) {
            mvc.perform(post("/api/v1/reflections").with(jwt().jwt(j -> j.subject(user.getId().toString())))
                .contentType("application/json").content("{\"reason\":\"" + reason + "\"}")).andExpect(status().isBadRequest());
        }
        reflections.create(user.getId(), note(null, null));
        users.delete(user); users.flush();
        assertThat(db.queryForObject("SELECT count(*) FROM decision_reflection WHERE user_id=?", Integer.class, user.getId())).isZero();
        verifyNoInteractions(finwiz);
    }

    @Test void storageAndReadBoundsAreEnforced() {
        db.update("""
            INSERT INTO decision_reflection(id,user_id,reason,created_at,updated_at)
            SELECT gen_random_uuid(),?,'Existing note',now(),now() FROM generate_series(1,1000)
            """, user.getId());
        assertThat(reflections.list(user.getId())).hasSize(50);
        assertThatThrownBy(() -> reflections.create(user.getId(), note(null, null)))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("storage limit");
        assertThat(reflections.list(other.getId())).isEmpty();
    }
    @Test void strategyFieldsSurviveLegacyUpdatesAndReviewIsExplicit() throws Exception {
        String body = """
            {"reason":"Margins should improve", "plan":{"exchange":"NSE","symbol":"INFY",
             "horizon":"Next results","target":"Evidence of margin improvement","invalidation":"Margin contraction","reviewOn":"2026-12-01"}}
            """;
        mvc.perform(post("/api/v1/reflections").with(jwt().jwt(j -> j.subject(user.getId().toString())))
            .contentType("application/json").content(body)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.plan.reviewOn").value("2026-12-01")).andExpect(jsonPath("$.reviewedAt").isEmpty())
            .andReturn();
        var saved = reflections.list(user.getId()).getFirst();
        reflections.update(user.getId(), saved.id(), note(null, null)); // old native payload omits plan
        assertThat(reflections.get(user.getId(), saved.id()).plan().symbol()).isEqualTo("INFY");
        assertThat(reflections.get(user.getId(), saved.id()).reviewedAt()).isNull();
        mvc.perform(put("/api/v1/reflections/" + saved.id()).with(jwt().jwt(j -> j.subject(user.getId().toString())))
            .contentType("application/json").content("{\"reason\":\"Thesis\",\"completeReview\":true}"))
            .andExpect(status().isBadRequest());
        var request = new ReflectionRequest(null, null, "My thesis", "", "", "", "The evidence contradicted my assumptions", saved.plan(), true);
        var reviewed = reflections.update(user.getId(), saved.id(), request);
        assertThat(reviewed.reviewedAt()).isNotNull();
        reflections.update(user.getId(), saved.id(), request);
        assertThat(reflections.get(user.getId(), saved.id()).reviewedAt()).isEqualTo(reviewed.reviewedAt());
        assertThat(db.queryForObject("SELECT count(*) FROM product_activity WHERE user_id=? AND event_name='REVIEW_COMPLETED'", Integer.class, user.getId())).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM product_activity WHERE user_id=? AND event_name='STRATEGY_CREATED'", Integer.class, user.getId())).isEqualTo(1);
        verifyNoInteractions(finwiz);
    }

    @Test void invalidStrategyInputIsRejectedWithoutSaving() throws Exception {
        for (String plan : List.of("{\"exchange\":\"FAKE\",\"symbol\":\"INFY\"}",
            "{\"exchange\":\"NSE\",\"symbol\":\"<script>\"}", "{\"exchange\":\"NSE\"}",
            "{\"reviewOn\":\"2200-01-01\"}")) {
            mvc.perform(post("/api/v1/reflections").with(jwt().jwt(j -> j.subject(user.getId().toString())))
                .contentType("application/json").content("{\"reason\":\"Thesis\",\"plan\":" + plan + "}"))
                .andExpect(status().isBadRequest());
        }
        assertThat(reflections.list(user.getId())).isEmpty();
    }

}
