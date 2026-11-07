package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.instrument.domain.*;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.instrument.service.InstrumentSnapshot;
import com.stoxsim.learner.api.LearnerProfile;
import com.stoxsim.learner.service.LearnerService;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.order.domain.*;
import com.stoxsim.order.repository.PaperOrderRepository;

@Testcontainers
@Transactional
@SpringBootTest(properties={"stoxsim.finwiz.enabled=false", "stoxsim.security.rate-limit.enabled=false",
    "stoxsim.market-data.upstox.stream-enabled=false", "stoxsim.market-data.upstox.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.instrument-sync-on-startup=false", "stoxsim.market-data.alpaca.polling-enabled=false"})
class LearnerPersonalizationIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @Autowired AppUserRepository users;
    @Autowired VirtualAccountRepository accounts;
    @Autowired TradableInstrumentRepository instruments;
    @Autowired PaperOrderRepository orders;
    @Autowired LearnerService learners;
    @Autowired com.stoxsim.auth.service.AccountLifecycleService lifecycle;
    @Autowired JdbcTemplate db;
    @Autowired WebApplicationContext context;
    AppUser user, other;
    MockMvc mvc;
    @BeforeEach void setup() {
        user=users.saveAndFlush(new AppUser(UUID.randomUUID()+"@learner.test", "hash", "Learner"));
        other=users.saveAndFlush(new AppUser(UUID.randomUUID()+"@learner.test", "hash", "Other"));
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Test void preferencesAreOptionalPrivateValidatedAndClearable() throws Exception {
        mvc.perform(get("/api/v1/learner/profile")).andExpect(status().isUnauthorized());
        assertThat(learners.profile(user.getId())).isEqualTo(LearnerProfile.empty());
        mvc.perform(put("/api/v1/learner/profile").with(jwt().jwt(j->j.subject(user.getId().toString())))
            .contentType("application/json").content("{\"experienceLevel\":\"ADVANCED\",\"learningObjective\":\"FUNDAMENTALS\",\"preferredMarket\":\"UNITED_STATES\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.experienceLevel").value("ADVANCED"));
        assertThat(learners.profile(other.getId())).isEqualTo(LearnerProfile.empty());
        mvc.perform(put("/api/v1/learner/profile").with(jwt().jwt(j->j.subject(user.getId().toString())))
            .contentType("application/json").content("{\"preferredMarket\":\"CRYPTO\"}"))
            .andExpect(status().isBadRequest());
        learners.update(user.getId(),new LearnerProfile.Update(null,null,null));
        assertThat(learners.profile(user.getId()).experienceLevel()).isNull();
        assertThat(learners.profile(user.getId()).updatedAt()).isNotNull();
    }
    @Test void recommendationReadsNeverRecordActivityAndActualUseIsDailyIdempotent() throws Exception {
        mvc.perform(get("/api/v1/learner/home").with(jwt().jwt(j->j.subject(user.getId().toString()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.recommendation.id").value("FIRST_TRADE"))
            .andExpect(header().string("Cache-Control",org.hamcrest.Matchers.containsString("no-store")));
        assertThat(events()).isZero();
        for(int i=0;i<2;i++) mvc.perform(post("/api/v1/learner/recommendations/FIRST_TRADE/used")
            .with(jwt().jwt(j->j.subject(user.getId().toString())))).andExpect(status().isNoContent());
        assertThat(events()).isEqualTo(1);
        mvc.perform(post("/api/v1/learner/recommendations/FORGED/used")
            .with(jwt().jwt(j->j.subject(user.getId().toString())))).andExpect(status().isConflict());
        assertThat(events()).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM product_activity WHERE user_id=?",Integer.class,other.getId())).isZero();
    }
    @Test void pendingAndExecutedFactsDriveRecommendationsWithoutChangingBalances() {
        var account=accounts.saveAndFlush(new VirtualAccount(user,MarketRegion.INDIA,new BigDecimal("500000")));
        var instrument=instruments.saveAndFlush(new TradableInstrument(new InstrumentSnapshot("UPSTOX",UUID.randomUUID().toString(),MarketRegion.INDIA,
            MarketExchange.NSE,"NSE_EQ","LEARNER","Learner","INE040A01034",InstrumentType.EQUITY,"INR",1,new BigDecimal("0.05"),"NORMAL"),UUID.randomUUID(),Instant.now()));
        var order=orders.saveAndFlush(new PaperOrder(account,instrument,UUID.randomUUID().toString(),OrderSide.BUY,OrderType.MARKET,1,null,BigDecimal.ZERO,LocalDate.now()));
        assertThat(learners.home(user.getId()).recommendation().id()).isEqualTo("REVIEW_PENDING_ORDER");
        db.update("UPDATE paper_order SET status='EXECUTED' WHERE id=?",order.getId());
        db.update("INSERT INTO trade(order_id,account_id,instrument_id,side,quantity,price,gross_value,executed_at) VALUES (?,?,?,'BUY',1,100,100,now())",order.getId(),account.getId(),instrument.getId());
        learners.update(user.getId(),new LearnerProfile.Update(LearnerProfile.ExperienceLevel.BEGINNER,LearnerProfile.LearningObjective.FUNDAMENTALS,MarketRegion.INDIA));
        assertThat(learners.home(user.getId()).recommendation().id()).isEqualTo("FUNDAMENTALS");
        db.update("INSERT INTO holding(account_id,instrument_id,quantity,blocked_quantity,average_price,version) VALUES (?,?,1,0,100,0)",account.getId(),instrument.getId());
        assertThat(learners.home(user.getId()).recommendation().id()).isEqualTo("DIVERSIFICATION");
        assertThat(learners.home(user.getId()).recommendation().description()).contains("purchase cost","differ from current market values");
        assertThat(db.queryForObject("SELECT available_cash FROM virtual_account WHERE id=?",BigDecimal.class,account.getId())).isEqualByComparingTo("500000");
    }
    @Test void deletingUserCascadesPreferences() {
        learners.update(user.getId(),new LearnerProfile.Update(LearnerProfile.ExperienceLevel.BEGINNER,null,null));
        users.delete(user); users.flush();
        assertThat(db.queryForObject("SELECT count(*) FROM learner_profile WHERE user_id=?",Integer.class,user.getId())).isZero();
    }
    @Test void accountExportIncludesOnlyOwnedLearningPreferences() {
        learners.update(user.getId(),new LearnerProfile.Update(LearnerProfile.ExperienceLevel.BEGINNER,
            LearnerProfile.LearningObjective.DISCIPLINE,MarketRegion.INDIA));
        var own=(java.util.List<?>)lifecycle.exportAccount(user.getId()).get("learnerProfile");
        var foreign=(java.util.List<?>)lifecycle.exportAccount(other.getId()).get("learnerProfile");
        assertThat(own).hasSize(1);
        assertThat(own.getFirst().toString()).contains("BEGINNER", "DISCIPLINE", "INDIA");
        assertThat(foreign).isEmpty();
    }
    private int events(){return db.queryForObject("SELECT count(*) FROM product_activity WHERE user_id=? AND event_name='RECOMMENDATION_USED'",Integer.class,user.getId());}
}
