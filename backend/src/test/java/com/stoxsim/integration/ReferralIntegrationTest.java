package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.stoxsim.auth.google.GoogleLoginChallenge;
import com.stoxsim.auth.google.GoogleIdentityVerifier;
import com.stoxsim.rewards.ReferralProperties;
import com.stoxsim.rewards.ReferralService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(properties = {
    "stoxsim.auth.google.enabled=true", "stoxsim.auth.google.client-id=fixture.apps.googleusercontent.com",
    "stoxsim.rewards.enabled=true", "stoxsim.rewards.identity-key=referral-integration-test-value-only",
    "stoxsim.rewards.minimum-age-hours=0", "stoxsim.security.rate-limit.enabled=false",
    "stoxsim.market-data.upstox.stream-enabled=false",
    "stoxsim.market-data.upstox.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.polling-enabled=false"
})
class ReferralIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @Autowired JdbcTemplate db;
    @Autowired WebApplicationContext context;
    @Autowired ObjectMapper json;
    @Autowired ReferralService referrals;
    @Autowired ReferralProperties properties;
    @MockitoBean GoogleIdentityVerifier verifier;
    @MockitoBean GoogleLoginChallenge challenges;
    private MockMvc mvc;
    private UUID inviter;
    private UUID reviewer;
    private String inviterEmail;
    private String code;

    @BeforeEach void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        inviterEmail = "inviter" + UUID.randomUUID().toString().replace("-", "") + "@gmail.com";
        inviter = register(inviterEmail, "");
        verifyEmail(inviter);
        reviewer = register(randomEmail(), "");
        verifyEmail(reviewer);
        db.update("UPDATE app_user SET platform_role='ADMIN' WHERE id=?", reviewer);
        var response = mvc.perform(post("/api/v1/rewards/code").with(actor(inviter)))
            .andExpect(status().isOk()).andReturn().getResponse();
        code = json.readTree(response.getContentAsString()).get("code").asText();
    }

    @Test void verifiesAttributionEligibilityAndExactlyOneConcurrentDualCredit() throws Exception {
        UUID learner = register(randomEmail(), code);
        UUID id = attribution(learner);
        mvc.perform(get("/api/v1/rewards")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/rewards/review").with(actor(inviter))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/rewards").with(actor(learner)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.referrals").isEmpty());
        mvc.perform(post("/api/v1/rewards/code").with(actor(learner))).andExpect(status().isForbidden());

        executePaperTrade(learner);
        referrals.qualifyPending();
        assertThat(state(id)).isEqualTo("PENDING");
        verifyEmail(learner);
        referrals.qualifyPending();
        assertThat(state(id)).isEqualTo("PENDING");
        executePaperTrade(learner);
        referrals.qualifyPending();
        assertThat(state(id)).isEqualTo("QUALIFIED");
        mvc.perform(get("/api/v1/rewards/review").with(actor(reviewer))).andExpect(status().isOk());

        var originals = db.queryForList("""
            SELECT id,available_cash,starting_capital FROM virtual_account
            WHERE user_id=? AND account_kind='STANDARD' ORDER BY id
            """, inviter);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return (Object) referrals.decide(reviewer, id, true); });
            var second = executor.submit(() -> { start.await(); return (Object) referrals.decide(reviewer, id, true); });
            start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
        }
        assertThat(state(id)).isEqualTo("REWARDED");
        assertThat(db.queryForList("SELECT amount FROM virtual_reward_entry WHERE transaction_id=(SELECT id FROM virtual_reward_transaction WHERE source_id=?) ORDER BY currency",
            BigDecimal.class,id)).containsExactly(new BigDecimal("100000.0000"),new BigDecimal("1000.0000"));
        assertThat(db.queryForList("""
            SELECT id,available_cash,starting_capital FROM virtual_account
            WHERE user_id=? AND account_kind='STANDARD' ORDER BY id
            """, inviter)).isEqualTo(originals);
        assertThat(db.queryForObject("""
            SELECT count(*) FROM virtual_account WHERE user_id=? AND account_kind='REWARD'
                AND NOT leaderboard_eligible AND sandbox_plan IS NULL
                AND available_cash=starting_capital AND realized_profit_loss=0
            """,Integer.class,inviter)).isEqualTo(2);
        UUID wallet = rewardAccount("INR");
        mvc.perform(get("/api/v1/accounts/" + wallet + "/portfolio").with(actor(inviter)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalProfitLoss").value(0));
        mvc.perform(get("/api/v1/rewards").with(actor(inviter)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.successful").value(1))
            .andExpect(jsonPath("$.earned.inr").value(100000)).andExpect(jsonPath("$.earned.usd").value(1000));
        assertThatThrownBy(() -> db.update("UPDATE virtual_reward_entry SET amount=1 WHERE account_id=?",wallet))
            .hasStackTraceContaining("Virtual reward records are append-only");
        mvc.perform(get("/api/v1/rewards/reconciliation").with(actor(reviewer)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unbalancedAccounts").value(0))
            .andExpect(jsonPath("$.unmatchedReferrals").value(0));
    }

    @Test void failedSecondCreditAndFailedReversalRollBackEveryWrite() throws Exception {
        UUID learner = register(randomEmail(), code);
        verifyEmail(learner);
        executePaperTrade(learner);
        referrals.qualifyPending();
        UUID id = attribution(learner);

        UUID usd = db.queryForObject("""
            INSERT INTO virtual_account(user_id,market_region,currency,available_cash,starting_capital,
                account_kind,sandbox_slot,account_label,active,leaderboard_eligible)
            VALUES (?,'UNITED_STATES','USD',999999999999999.9999,999999999999999.9999,
                'REWARD',0,'Test overflow balance',true,false) RETURNING id
            """, UUID.class, inviter);
        assertThatThrownBy(() -> referrals.decide(reviewer,id,true)).hasStackTraceContaining("numeric field overflow");
        assertThat(state(id)).isEqualTo("QUALIFIED");
        assertThat(rewardAccount("INR")).isNull();
        assertThat(db.queryForObject("SELECT count(*) FROM virtual_reward_transaction WHERE source_id=?",Integer.class,id)).isZero();
        db.update("DELETE FROM virtual_account WHERE id=?",usd);

        referrals.decide(reviewer,id,true);
        db.update("UPDATE virtual_account SET available_cash=1 WHERE id=?",rewardAccount("USD"));
        assertThatThrownBy(() -> referrals.reverse(reviewer,id)).hasMessageContaining("enough available practice cash");
        assertThat(balance("INR")).isEqualByComparingTo("100000");
        db.update("UPDATE virtual_account SET available_cash=1000 WHERE id=?",rewardAccount("USD"));
        Object reversal = referrals.reverse(reviewer,id);
        assertThat(reversal).isEqualTo(referrals.reverse(reviewer,id));
        assertThat(balance("INR")).isZero();
        assertThat(balance("USD")).isZero();
        mvc.perform(get("/api/v1/rewards").with(actor(inviter))).andExpect(status().isOk())
            .andExpect(jsonPath("$.earned.inr").value(0)).andExpect(jsonPath("$.earned.usd").value(0))
            .andExpect(jsonPath("$.history.length()").value(2));
    }

    @Test void rejectsSelfReferralsDeletedAccountsAndKnownGoogleAliases() throws Exception {
        String alias = inviterEmail.replace("@", "+alias@");
        assertThat(attribution(register(alias,code))).isNull();
        assertThat(attribution(register(randomEmail(),"invalid"))).isNull();

        String email = randomEmail();
        UUID learner = register(email,code);
        UUID id = attribution(learner);
        mvc.perform(delete("/api/v1/auth/me").with(actor(learner)).contentType("application/json")
            .content("{\"password\":\"Referral-test-123!\"}")).andExpect(status().isNoContent());
        assertThat(state(id)).isEqualTo("REJECTED");
        UUID repeat = register(email,code);
        assertThat(state(attribution(repeat))).isEqualTo("REJECTED");

        String local = "google" + UUID.randomUUID().toString().replace("-", "");
        UUID original = register(local + "@gmail.com","");
        verifyEmail(original);
        UUID duplicate = registerNativeGoogle(local + "+again@googlemail.com");
        assertThat(state(attribution(duplicate))).isEqualTo("REJECTED");
        UUID fresh = registerNativeGoogle(local + "new@gmail.com");
        assertThat(state(attribution(fresh))).isEqualTo("PENDING");
        properties.setEnabled(false);
        try { assertThat(attribution(register(randomEmail(),code))).isNull(); }
        finally { properties.setEnabled(true); }
    }

    private UUID register(String email, String referral) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/register").contentType("application/json")
            .content(json.writeValueAsString(Map.of("email",email,"password","Referral-test-123!",
                "displayName","Referral fixture","termsAccepted",true,"referralCode",referral))))
            .andExpect(status().isCreated()).andReturn().getResponse();
        return UUID.fromString(json.readTree(response.getContentAsString()).get("user").get("id").asText());
    }

    private UUID registerNativeGoogle(String email) throws Exception {
        String binding = "x".repeat(43);
        when(verifier.nativeEnabled()).thenReturn(true);
        when(challenges.consumeNative(binding)).thenReturn("fixture-nonce");
        when(verifier.identity("fixture-credential","fixture-nonce",true))
            .thenReturn(new GoogleIdentityVerifier.Identity(UUID.randomUUID().toString(),email,true));
        var response = mvc.perform(post("/api/v1/auth/google/native/register").contentType("application/json")
            .content(json.writeValueAsString(Map.of("credential","fixture-credential","binding",binding,
                "displayName","Google referral fixture","termsAccepted",true,"referralCode",code))))
            .andExpect(status().isCreated()).andReturn().getResponse();
        return UUID.fromString(json.readTree(response.getContentAsString()).get("user").get("id").asText());
    }

    private void executePaperTrade(UUID user) {
        UUID stock = db.queryForObject("""
            INSERT INTO instrument(provider,instrument_key,market_region,exchange,segment,trading_symbol,name,
                instrument_type,currency,lot_size,tick_size,last_seen_sync_id,synced_at)
            VALUES ('UPSTOX',?,'INDIA','NSE','NSE_EQ','REFTEST','Referral fixture','EQUITY','INR',1,0.05,gen_random_uuid(),now())
            RETURNING id
            """,UUID.class,UUID.randomUUID().toString());
        db.update("""
            WITH filled AS (
                INSERT INTO paper_order(account_id,instrument_id,idempotency_key,side,order_type,product_type,
                    validity,status,quantity,submitted_for_date,execution_price,executed_at)
                SELECT id,?,?,'BUY','MARKET','DELIVERY','DAY','EXECUTED',1,current_date,100,now()
                FROM virtual_account WHERE user_id=? AND account_kind='STANDARD' AND market_region='INDIA'
                RETURNING id,account_id,instrument_id
            )
            INSERT INTO trade(order_id,account_id,instrument_id,side,quantity,price,gross_value,executed_at)
            SELECT id,account_id,instrument_id,'BUY',1,100,100,now() FROM filled
            """,stock, UUID.randomUUID().toString(), user);
    }

    private static String randomEmail() { return UUID.randomUUID() + "@example.test"; }
    private void verifyEmail(UUID user) { db.update("UPDATE app_user SET email_verified_at=now() WHERE id=?",user); }
    private static RequestPostProcessor actor(UUID user) { return jwt().jwt(token -> token.subject(user.toString())); }
    private UUID attribution(UUID user) {
        return db.queryForList("SELECT id FROM referral_attribution WHERE referred_id=?",UUID.class,user)
            .stream().findFirst().orElse(null);
    }
    private String state(UUID referral) {
        return db.queryForObject("SELECT status FROM referral_attribution WHERE id=?",String.class,referral);
    }
    private UUID rewardAccount(String currency) {
        return db.queryForList("SELECT id FROM virtual_account WHERE user_id=? AND account_kind='REWARD' AND currency=?",
            UUID.class,inviter,currency).stream().findFirst().orElse(null);
    }
    private BigDecimal balance(String currency) {
        return db.queryForObject("SELECT available_cash FROM virtual_account WHERE id=?",BigDecimal.class,rewardAccount(currency));
    }
}
