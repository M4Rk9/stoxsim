package com.stoxsim.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.subscription.domain.UserSubscription;
import com.stoxsim.subscription.repository.UserSubscriptionRepository;

@Testcontainers
@SpringBootTest(properties = {"stoxsim.finwiz.enabled=false", "stoxsim.market-data.upstox.stream-enabled=false",
    "stoxsim.market-data.upstox.instrument-sync-on-startup=false", "stoxsim.market-data.alpaca.instrument-sync-on-startup=false"})
class FinwizTierIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @Container @ServiceConnection(name="redis") static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    @Autowired AppUserRepository users;
    @Autowired UserSubscriptionRepository subscriptions;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    MockMvc mvc;
    UUID user;
    @BeforeEach void setup() {
        var learner = users.saveAndFlush(new AppUser("finwiz-" + UUID.randomUUID() + "@example.test", "unused-hash", "Finwiz learner"));
        user = learner.getId();
        subscriptions.saveAndFlush(new UserSubscription(learner));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    private org.springframework.test.web.servlet.ResultActions ask(String extra) throws Exception {
        return mvc.perform(post("/api/v1/finwiz/ask").with(jwt().jwt(token -> token.subject(user.toString()).claim("plan", "PRO")))
            .contentType("application/json").content("{\"question\":\"Explain valuation\",\"topic\":\"VALUATION\"" + extra + "}"));
    }
    @Test void authenticatedDatabasePlanControlsDepthNotClientClaims() throws Exception {
        mvc.perform(post("/api/v1/finwiz/ask").contentType("application/json").content("{\"question\":\"Explain valuation\",\"topic\":\"VALUATION\"}"))
            .andExpect(status().isUnauthorized());
        ask("").andExpect(status().isOk()).andExpect(jsonPath("$.finwizTier").value("BASIC"));
        jdbc.update("UPDATE user_subscription SET plan='PLUS',current_period_end=now()+interval '1 day' WHERE user_id=?", user);
        ask("").andExpect(status().isOk()).andExpect(jsonPath("$.finwizTier").value("EXPANDED"));
        jdbc.update("UPDATE user_subscription SET plan='PRO' WHERE user_id=?", user);
        ask("").andExpect(status().isOk()).andExpect(jsonPath("$.finwizTier").value("FULL"));
        jdbc.update("UPDATE user_subscription SET current_period_end=now()-interval '1 second' WHERE user_id=?", user);
        ask("").andExpect(status().isOk()).andExpect(jsonPath("$.finwizTier").value("BASIC"));
        mvc.perform(get("/api/v1/subscription").with(jwt().jwt(token -> token.subject(user.toString()).claim("plan", "PRO"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.entitlements.finwizTier").value("BASIC"));
    }
}
