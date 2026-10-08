package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.math.BigDecimal;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.service.*;
import com.stoxsim.account.domain.VirtualAccount;
import com.stoxsim.account.repository.VirtualAccountRepository;
import com.stoxsim.market.domain.MarketRegion;
import com.stoxsim.instrument.domain.*;
import com.stoxsim.instrument.repository.TradableInstrumentRepository;
import com.stoxsim.instrument.service.InstrumentSnapshot;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(properties={"stoxsim.market-data.upstox.stream-enabled=false", "stoxsim.market-data.upstox.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.instrument-sync-on-startup=false", "stoxsim.market-data.alpaca.polling-enabled=false", "stoxsim.portfolio.history.enabled=false", "stoxsim.security.rate-limit.enabled=false"})
class RoutingSecurityIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @Autowired WebApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired VirtualAccountRepository accounts;
    @Autowired TokenService tokens;
    @Autowired AuthenticationService authentication;
    @Autowired AccountLifecycleService lifecycle;
    @Autowired JdbcTemplate db;
    @Autowired TradableInstrumentRepository instruments;
    @Autowired com.stoxsim.order.repository.PaperOrderRepository orders;
    @Autowired com.stoxsim.watchlist.repository.WatchlistRepository watchlists;
    @Autowired com.stoxsim.watchlist.repository.WatchlistItemRepository items;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    @Autowired AccountTokenService accountTokens;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    MockMvc mvc;
    AppUser owner, other;
    UUID account;
    TokenService.TokenPair session;
    @BeforeEach void setup() {
        db.execute("TRUNCATE app_user RESTART IDENTITY CASCADE");
        owner = users.saveAndFlush(new AppUser("security@example.test", "hash", "Security"));
        other = users.saveAndFlush(new AppUser("other@example.test", "hash", "Other"));
        account = accounts.saveAndFlush(new VirtualAccount(other, MarketRegion.INDIA, new BigDecimal("500000"))).getId();
        session = tokens.issueTokenPair(owner);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Test void emailChangeRequiresPasswordAndInvalidatesOldLinksAndSession() throws Exception {
        db.update("UPDATE app_user SET password_hash=? WHERE id=?", passwordEncoder.encode("correct-password"), owner.getId());
        String reset = accountTokens.issue(owner.getId(), AccountTokenService.PASSWORD_RESET, java.time.Duration.ofMinutes(30));
        String verify = accountTokens.issue(owner.getId(), AccountTokenService.EMAIL_VERIFICATION, java.time.Duration.ofHours(24));
        mvc.perform(patch("/api/v1/auth/me").header("Authorization", "Bearer " + session.accessToken())
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"new@example.test\",\"displayName\":\"Owner\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/auth/me").header("Authorization", "Bearer " + session.accessToken())
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"new@example.test\",\"displayName\":\"Owner\",\"currentPassword\":\"correct-password\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + session.accessToken()))
            .andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertThrows(com.stoxsim.common.error.UnauthorizedException.class,
            () -> accountTokens.consume(reset, AccountTokenService.PASSWORD_RESET));
        org.junit.jupiter.api.Assertions.assertThrows(com.stoxsim.common.error.UnauthorizedException.class,
            () -> accountTokens.consume(verify, AccountTokenService.EMAIL_VERIFICATION));
    }

    private static final Set<String> PUBLIC_POST = Set.of(
        "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh", "/api/v1/auth/logout",
        "/api/v1/auth/password/forgot", "/api/v1/auth/password/reset", "/api/v1/auth/email-verification/confirm", "/api/v1/billing/test/webhook");

    @Test void everyProtectedControllerRouteRejectsAnonymousMalformedAndRevokedTokensBeforeBinding() throws Exception {
        authentication.logout(session.refreshToken());
        int routes = 0;
        var mappings = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            if (!entry.getValue().getBeanType().getPackageName().startsWith("com.stoxsim")) continue;
            for (String pattern : entry.getKey().getPatternValues()) {
                for (var method : entry.getKey().getMethodsCondition().getMethods()) {
                    if (pattern.equals("/api/v1/system/status") || method.name().equals("POST") && PUBLIC_POST.contains(pattern)) continue;
                    String path = pattern.replaceAll("\\{[^}]+\\}", UUID.randomUUID().toString());
                    for (String bearer : new String[] {"", "invalid", session.accessToken()}) {
                        var request = request(HttpMethod.valueOf(method.name()), path).contentType(MediaType.APPLICATION_JSON)
                            .content("{}").header("Idempotency-Key", UUID.randomUUID().toString());
                        if (!bearer.isEmpty()) request.header("Authorization", "Bearer " + bearer);
                        var response = mvc.perform(request).andReturn().getResponse();
                        assertThat(response.getStatus()).as("%s %s (%s)", method, pattern, bearer.isEmpty() ? "anonymous" : "invalid/revoked").isEqualTo(401);
                    }
                    routes++;
                }
            }
        }
        assertThat(routes).isGreaterThan(80);
        System.out.println("Security inventory: verified " + routes + " protected controller routes with anonymous, malformed and revoked JWTs");
    }
    @Test void ownerScopingRejectsForeignAccountAndNestedResources() throws Exception {
        for (String suffix : List.of("portfolio", "portfolio/analytics", "portfolio/insights", "holdings", "trades", "ledger", "orders", "portfolio/history", "portfolio/sectors")) {
            // Only registered routes belong in this matrix; route inventory below determines coverage independently.
            var mapping = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
            String pattern = "/api/v1/accounts/{accountId}/" + suffix;
            assertThat(mapping.getHandlerMethods().keySet().stream().anyMatch(m -> m.getPatternValues().contains(pattern)))
                .as("registered ownership test route %s", pattern).isTrue();
            mvc.perform(get("/api/v1/accounts/" + account + "/" + suffix).header("Authorization", "Bearer " + session.accessToken()))
                .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/v1/accounts/" + account + "/scenarios").header("Authorization", "Bearer " + session.accessToken())
            .contentType(MediaType.APPLICATION_JSON).content("{\"scenarioId\":\"selloff\",\"version\":1}")) .andExpect(status().isNotFound());
        var foreign = tokens.issueTokenPair(other);
        var sid = db.queryForObject("SELECT session_id FROM refresh_token WHERE token_hash=?", UUID.class, tokens.hash(foreign.refreshToken()));
        org.junit.jupiter.api.Assertions.assertThrows(com.stoxsim.common.error.UnauthorizedException.class,
            () -> lifecycle.revokeSession(owner.getId(), sid));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + foreign.accessToken())).andExpect(status().isOk());
    }
    private TradableInstrument instrument() {
        return instruments.saveAndFlush(new TradableInstrument(new InstrumentSnapshot("TEST", "test-" + UUID.randomUUID(), MarketRegion.INDIA,
            MarketExchange.NSE, "EQ", "SAFE", "Safe company", null, InstrumentType.EQUITY, "INR", 1,
            new BigDecimal("0.05"), "EQ"), UUID.randomUUID(), Instant.now()));
    }
    @Test void cannotReadModifyCancelOrDeleteForeignOrdersAndWatchlistItems() throws Exception {
        var instrument = instrument();
        var order = orders.saveAndFlush(new com.stoxsim.order.domain.PaperOrder(accounts.findById(account).orElseThrow(), instrument,
            UUID.randomUUID().toString(), com.stoxsim.order.domain.OrderSide.BUY, com.stoxsim.order.domain.OrderType.LIMIT,
            1, new BigDecimal("10"), BigDecimal.ZERO, java.time.LocalDate.now()));
        var ownAccount = accounts.saveAndFlush(new VirtualAccount(owner, MarketRegion.INDIA, new BigDecimal("500000"))).getId();
        for (String path : List.of("/api/v1/orders/" + order.getId(), "/api/v1/accounts/" + account + "/orders/" + order.getId(),
            "/api/v1/accounts/" + ownAccount + "/orders/" + order.getId())) {
            mvc.perform(get(path).header("Authorization", "Bearer " + session.accessToken())).andExpect(status().isNotFound());
            mvc.perform(put(path).header("Authorization", "Bearer " + session.accessToken()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":2,\"limitPrice\":20}")) .andExpect(status().isNotFound());
            mvc.perform(delete(path).header("Authorization", "Bearer " + session.accessToken())).andExpect(status().isNotFound());
        }
        assertThat(orders.findById(order.getId()).orElseThrow().getQuantity()).isEqualTo(1);
        var list = watchlists.saveAndFlush(new com.stoxsim.watchlist.domain.Watchlist(other, "Private", true));
        var item = items.saveAndFlush(new com.stoxsim.watchlist.domain.WatchlistItem(list, instrument));
        mvc.perform(delete("/api/v1/watchlists/default/items/" + item.getId()).header("Authorization", "Bearer " + session.accessToken()))
            .andExpect(status().isNotFound());
        assertThat(items.existsById(item.getId())).isTrue();
    }
    @Test void sharedRoutesRejectSubjectSessionMismatchAndDeletedOrLoggedOutUsers() throws Exception {
        var foreign = tokens.issueTokenPair(other);
        var sid = db.queryForObject("SELECT session_id FROM refresh_token WHERE token_hash=?", UUID.class, tokens.hash(foreign.refreshToken()));
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer(TokenService.ISSUER)
            .subject(owner.getId().toString()).claim("sid", sid.toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        var forgedOwnership = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
            org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/v1/scenarios").header("Authorization", "Bearer " + forgedOwnership)).andExpect(status().isUnauthorized());
        lifecycle.logoutAll(owner.getId());
        mvc.perform(get("/api/v1/scenarios").header("Authorization", "Bearer " + session.accessToken())).andExpect(status().isUnauthorized());
        db.update("DELETE FROM app_user WHERE id=?", other.getId());
        mvc.perform(get("/api/v1/scenarios").header("Authorization", "Bearer " + foreign.accessToken())).andExpect(status().isUnauthorized());
    }
    @Test void userSessionDoesNotGrantAdminAccess() throws Exception {
        for (String route : List.of("/api/v1/admin/analytics/overview", "/api/v1/admin/analytics/activity", "/api/v1/billing/test", "/api/v1/campus/admin/verification-requests"))
            mvc.perform(get(route).header("Authorization", "Bearer " + session.accessToken())).andExpect(status().isForbidden());
    }
    @Test void refreshRotationRejectsReplayAndLogoutOfOldRefreshRevokesRotatedAccess() throws Exception {
        var rotated = authentication.refresh(session.refreshToken());
        org.junit.jupiter.api.Assertions.assertThrows(com.stoxsim.common.error.UnauthorizedException.class,
            () -> authentication.refresh(session.refreshToken()));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + rotated.accessToken())).andExpect(status().isOk());
        authentication.logout(session.refreshToken());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + rotated.accessToken())).andExpect(status().isUnauthorized());
    }
    @Test void simultaneousRefreshCannotCreateTwoActiveTokens() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            Callable<Boolean> refresh = () -> { start.await(); try { authentication.refresh(session.refreshToken()); return true; }
                catch (com.stoxsim.common.error.UnauthorizedException rejected) { return false; } };
            var first = pool.submit(refresh); var second = pool.submit(refresh); start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(db.queryForObject("SELECT count(*) FROM refresh_token WHERE user_id=? AND revoked_at IS NULL", Integer.class, owner.getId())).isEqualTo(1);
    }
    @Test void sqlAndLikeMetacharactersAreLiteralSearchData() {
        instruments.deleteAll();
        instruments.saveAndFlush(new TradableInstrument(new InstrumentSnapshot("TEST", "test-key", MarketRegion.INDIA, MarketExchange.NSE,
            "EQ", "SAFE", "Safe company", null, InstrumentType.EQUITY, "INR", 1, new BigDecimal("0.05"), "EQ"), UUID.randomUUID(), Instant.now()));
        assertThat(instruments.search(MarketRegion.INDIA, "saFe", PageRequest.of(0, 10))).hasSize(1);
        for (String payload : List.of("' OR 1=1 --", "%_", "<img src=x onerror=alert(1)>", "{\"$ne\":null}"))
            assertThat(instruments.search(MarketRegion.INDIA, payload, PageRequest.of(0, 10))).isEmpty();
        assertThat(instruments.count()).isEqualTo(1);
    }
}
