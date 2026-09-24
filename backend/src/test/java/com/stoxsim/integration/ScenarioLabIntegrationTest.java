package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.portfolio.api.*;
import com.stoxsim.portfolio.service.PortfolioValuationService;
import com.stoxsim.scenario.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(properties={"stoxsim.market-data.upstox.stream-enabled=false","stoxsim.market-data.upstox.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.instrument-sync-on-startup=false","stoxsim.portfolio.history.enabled=false"})
class ScenarioLabIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17-alpine");
    @Autowired JdbcTemplate db;
    @Autowired AppUserRepository users;
    @Autowired VirtualAccountRepository accounts;
    @Autowired WebApplicationContext context;
    @MockitoBean PortfolioValuationService valuation;
    UUID user,account;
    MockMvc mvc;
    static final BigDecimal ZERO=BigDecimal.ZERO;
    static final Instant MARK=Instant.parse("2026-01-02T10:00:00Z");
    @BeforeEach void setup() {
        db.execute("TRUNCATE app_user RESTART IDENTITY CASCADE");
        var owner=users.saveAndFlush(new AppUser("scenario@test.local","hash","Scenario"));user=owner.getId();
        account=accounts.saveAndFlush(new VirtualAccount(owner,MarketRegion.INDIA,new BigDecimal("500000"))).getId();
        db.update("INSERT INTO user_subscription(user_id,plan,subscription_status) VALUES (?,'PRO','ACTIVE')",user);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mockPortfolio("INR",PortfolioPositionResponse.PricingStatus.CLOSED,MARK);
    }
    void mockPortfolio(String currency,PortfolioPositionResponse.PricingStatus status,Instant time) {
        var position=new PortfolioPositionResponse(UUID.randomUUID(),"NSE","TEST","Example",currency,8,2,6,new BigDecimal("90"),new BigDecimal("100"),new BigDecimal("720"),new BigDecimal("800"),new BigDecimal("80"),ZERO,status,time);
        when(valuation.valueForAccount(user,account)).thenReturn(new PortfolioResponse(MarketRegion.INDIA,"INR",new BigDecimal("1000"),new BigDecimal("150"),new BigDecimal("50"),new BigDecimal("720"),new BigDecimal("800"),ZERO,new BigDecimal("80"),ZERO,new BigDecimal("1000"),ZERO,status,MARK,List.of(position)));
    }
    org.springframework.test.web.servlet.ResultActions run(String body) throws Exception {
        return mvc.perform(post("/api/v1/accounts/"+account+"/scenarios").with(jwt().jwt(j->j.subject(user.toString()))).contentType(MediaType.APPLICATION_JSON).content(body));
    }
    Map<String,Object> balances() { return db.queryForMap("SELECT available_cash,blocked_cash,starting_capital,realized_profit_loss FROM virtual_account WHERE id=?",account); }
    @Test void authenticatedOwnedProRunIsReproducibleAndDoesNotWriteAccountState() throws Exception {
        var before=balances();
        var first=run("{\"scenarioId\":\"selloff\",\"version\":1}").andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.projection.steps[3].equity").value(760)).andExpect(jsonPath("$.projection.returnPercent").value(-24))
            .andExpect(jsonPath("$.cash").value(200)).andExpect(jsonPath("$.positions[0].quantity").value(8)).andReturn().getResponse().getContentAsString();
        var second=run("{\"scenarioId\":\"selloff\",\"version\":1}").andReturn().getResponse().getContentAsString();
        assertThat(second).isEqualTo(first); assertThat(balances()).isEqualTo(before);
        for(var table:List.of("holding","paper_order","account_ledger","portfolio_history","campus_competition_entry","competition_entry"))
            assertThat(db.queryForObject("SELECT count(*) FROM "+table,Integer.class)).isZero();
    }
    @Test void authenticationOwnershipAndEffectiveProAreCheckedBeforeValuation() throws Exception {
        mvc.perform(get("/api/v1/scenarios")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/accounts/"+account+"/scenarios").contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/accounts/"+account+"/scenarios").with(jwt().jwt(j->j.subject(UUID.randomUUID().toString()))).contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isNotFound());
        for(var plan:List.of("FREE","PLUS")) { db.update("UPDATE user_subscription SET plan=? WHERE user_id=?",plan,user);run("{\"scenarioId\":\"selloff\",\"version\":1}").andExpect(status().isForbidden()); }
        db.update("UPDATE user_subscription SET plan='PRO',subscription_status='PAST_DUE' WHERE user_id=?",user);
        run("{\"scenarioId\":\"selloff\",\"version\":1}").andExpect(status().isForbidden());
        db.update("UPDATE app_user SET platform_role='ADMIN',email_verified_at=now() WHERE id=?",user);
        db.update("UPDATE user_subscription SET subscription_status='ACTIVE',billing_provider='RAZORPAY_TEST',provider_customer_reference='cust_scenario_fixture',provider_subscription_reference='sub_scenario_fixture',test_access_until=now()-interval '1 day' WHERE user_id=?",user);
        run("{\"scenarioId\":\"selloff\",\"version\":1}").andExpect(status().isForbidden());
        verifyNoInteractions(valuation);
    }
    @Test void catalogAndInputContractAreVersionedAndBounded() throws Exception {
        mvc.perform(get("/api/v1/scenarios").with(jwt().jwt(j->j.subject(user.toString())))).andExpect(status().isOk()).andExpect(jsonPath("$[0].inputType").value("SYNTHETIC")).andExpect(jsonPath("$[0].version").value(1));
        for(var body:List.of("{}","{\"scenarioId\":\"selloff\",\"version\":2}","{\"scenarioId\":\"unknown\",\"version\":1}","{\"scenarioId\":\"custom\",\"version\":1,\"customShockPercent\":-101}","{\"scenarioId\":\"custom\",\"version\":1,\"customShockPercent\":1.234}","{\"scenarioId\":\"selloff\",\"version\":1,\"customShockPercent\":10}")) run(body).andExpect(status().isBadRequest());
        run("{\"scenarioId\":\"custom\",\"version\":1,\"customShockPercent\":-100}").andExpect(status().isOk()).andExpect(jsonPath("$.projection.steps[1].equity").value(200));
    }
    @Test void staleMissingFutureAndMixedCurrencyMarksNeverProduceResults() throws Exception {
        for(var status:List.of(PortfolioPositionResponse.PricingStatus.STALE,PortfolioPositionResponse.PricingStatus.UNAVAILABLE)) {
            mockPortfolio("INR",status,MARK); run("{\"scenarioId\":\"selloff\",\"version\":1}").andExpect(status().isConflict());
        }
        mockPortfolio("USD",PortfolioPositionResponse.PricingStatus.CLOSED,MARK);run("{\"scenarioId\":\"selloff\",\"version\":1}").andExpect(status().isConflict());
        mockPortfolio("INR",PortfolioPositionResponse.PricingStatus.CLOSED,null);run("{\"scenarioId\":\"selloff\",\"version\":1}").andExpect(status().isConflict());
        mockPortfolio("INR",PortfolioPositionResponse.PricingStatus.LIVE,Instant.now().plusSeconds(3600));run("{\"scenarioId\":\"selloff\",\"version\":1}").andExpect(status().isConflict());
    }
}
