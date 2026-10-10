package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.stoxsim.auth.api.dto.LoginRequest;
import com.stoxsim.auth.api.dto.PasswordUpdateRequest;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.service.AccountLifecycleService;
import com.stoxsim.auth.service.AccountMailService;
import com.stoxsim.auth.service.AccountTokenService;
import com.stoxsim.auth.service.AuthenticationService;
import com.stoxsim.auth.service.TokenService;
import com.stoxsim.common.error.UnauthorizedException;

@Testcontainers
@SpringBootTest(properties = {
    "stoxsim.market-data.upstox.stream-enabled=false",
    "stoxsim.market-data.upstox.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.polling-enabled=false",
    "stoxsim.portfolio.history.enabled=false",
    "stoxsim.security.rate-limit.enabled=false"
})
class AuthRecoveryConcurrencyIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static final String OLD_PASSWORD = "old-password-123";
    private static final String NEW_PASSWORD = "new-password-456";

    @Autowired AppUserRepository users;
    @Autowired AuthenticationService authentication;
    @Autowired AccountLifecycleService lifecycle;
    @Autowired AccountTokenService accountTokens;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate db;
    @Autowired TransactionTemplate transactions;
    @Autowired WebApplicationContext context;
    @MockitoBean AccountMailService mail;
    @MockitoSpyBean TokenService tokens;
    AppUser owner;
    MockMvc mvc;

    @BeforeEach void setup() {
        db.execute("TRUNCATE app_user RESTART IDENTITY CASCADE");
        owner = users.saveAndFlush(new AppUser("recovery@example.test", passwords.encode(OLD_PASSWORD), "Owner"));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    enum CredentialUpdate { CHANGE, RESET }

    @ParameterizedTest @EnumSource(CredentialUpdate.class)
    void loginWaitingForCredentialUpdateRechecksTheCommittedPassword(CredentialUpdate update) throws Exception {
        String reset = resetToken();
        var changed = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var loginPid = new AtomicInteger();
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var change = pool.submit(() -> transactions.executeWithoutResult(status -> {
                    changeCredentials(update, reset);
                    changed.countDown();
                    waitFor(release);
                }));
                waitFor(changed);
                var login = pool.submit(() -> {
                    assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
                        loginPid.set(db.queryForObject("SELECT pg_backend_pid()", Integer.class));
                        authentication.login(new LoginRequest(owner.getEmail(), OLD_PASSWORD));
                    })).isInstanceOf(UnauthorizedException.class);
                    return true;
                });
                awaitBlocked(loginPid);
                release.countDown();
                change.get(15, TimeUnit.SECONDS);
                assertThat(login.get(15, TimeUnit.SECONDS)).isTrue();
            } finally {
                release.countDown();
            }
        }
        assertThat(activeSessions()).isZero();
        assertThat(authentication.login(new LoginRequest(owner.getEmail(), NEW_PASSWORD)).accessToken()).isNotBlank();
    }

    @ParameterizedTest @EnumSource(CredentialUpdate.class)
    void credentialUpdateWaitingForLoginRevokesItsNewlyCreatedSession(CredentialUpdate update) throws Exception {
        String reset = resetToken();
        var issuing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var changePid = new AtomicInteger();
        doAnswer(invocation -> {
            issuing.countDown();
            waitFor(release);
            return invocation.callRealMethod();
        }).when(tokens).issueTokenPair(any(AppUser.class), anyString());
        com.stoxsim.auth.api.dto.AuthResponse loggedIn;
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var login = pool.submit(() -> authentication.login(new LoginRequest(owner.getEmail(), OLD_PASSWORD)));
                waitFor(issuing);
                var change = pool.submit(() -> transactions.executeWithoutResult(status -> {
                    changePid.set(db.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    changeCredentials(update, reset);
                }));
                awaitBlocked(changePid);
                release.countDown();
                loggedIn = login.get(15, TimeUnit.SECONDS);
                change.get(15, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        assertThat(activeSessions()).isZero();
        assertThatThrownBy(() -> authentication.refresh(loggedIn.refreshToken())).isInstanceOf(UnauthorizedException.class);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + loggedIn.accessToken()))
            .andExpect(status().isUnauthorized());
    }

    @Test void concurrentRequestsSendOnlyOneEmailAndKeepThatLinkUsable() throws Exception {
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(8)) {
            var requests = new ArrayList<Future<?>>();
            for (int i = 0; i < 8; i++) requests.add(pool.submit(() -> {
                waitFor(start);
                lifecycle.requestPasswordReset(" RECOVERY@EXAMPLE.TEST ");
            }));
            start.countDown();
            for (var request : requests) request.get(15, TimeUnit.SECONDS);
        }
        var raw = ArgumentCaptor.forClass(String.class);
        verify(mail, times(1)).sendPasswordReset(any(AppUser.class), raw.capture());
        assertThat(resetCount()).isEqualTo(1);
        assertThat(accountTokens.consume(raw.getValue(), AccountTokenService.PASSWORD_RESET)).isEqualTo(owner.getId());
        // Consuming a link cannot erase the recipient's issuance budget.
        lifecycle.requestPasswordReset(owner.getEmail());
        assertThat(resetCount()).isEqualTo(1);
        verify(mail, times(1)).sendPasswordReset(any(AppUser.class), anyString());
    }

    @Test void requestsAfterCooldownCanReplaceTheLink() {
        String original = resetToken();
        db.update("UPDATE account_token SET created_at=? WHERE user_id=?", Timestamp.from(Instant.now().minusSeconds(61)), owner.getId());
        lifecycle.requestPasswordReset(owner.getEmail());
        assertThat(resetCount()).isEqualTo(2);
        verify(mail).sendPasswordReset(any(AppUser.class), anyString());
        assertThatThrownBy(() -> accountTokens.consume(original, AccountTokenService.PASSWORD_RESET))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test void rollingHourlyLimitPreservesTheLatestLinkAndExpires() {
        String latest = null;
        for (int i = 0; i < 5; i++) {
            latest = resetToken();
            db.update("UPDATE account_token SET created_at=? WHERE token_hash=?",
                Timestamp.from(Instant.now().minusSeconds(120L * (5 - i))), tokens.hash(latest));
        }
        lifecycle.requestPasswordReset(owner.getEmail());
        verifyNoInteractions(mail);
        assertThat(resetCount()).isEqualTo(5);
        assertThat(accountTokens.consume(latest, AccountTokenService.PASSWORD_RESET)).isEqualTo(owner.getId());
        db.update("UPDATE account_token SET created_at=? WHERE user_id=?", Timestamp.from(Instant.now().minusSeconds(3601)), owner.getId());
        lifecycle.requestPasswordReset(owner.getEmail());
        assertThat(resetCount()).isEqualTo(6);
        verify(mail).sendPasswordReset(any(AppUser.class), anyString());
    }

    @Test void unknownAndThrottledRecipientsGetTheSameAcceptedResponse() throws Exception {
        resetToken();
        String accepted = null;
        for (String email : new String[] {owner.getEmail(), "missing@example.test"}) {
            var result = mvc.perform(post("/api/v1/auth/password/forgot").contentType("application/json")
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
            if (accepted == null) accepted = result;
            else assertThat(result).isEqualTo(accepted);
        }
        verifyNoInteractions(mail);
        assertThat(resetCount()).isEqualTo(1);
    }

    private String resetToken() {
        return accountTokens.issue(owner.getId(), AccountTokenService.PASSWORD_RESET, Duration.ofMinutes(30));
    }

    private void changeCredentials(CredentialUpdate update, String reset) {
        if (update == CredentialUpdate.RESET) lifecycle.resetPassword(reset, NEW_PASSWORD);
        else authentication.updatePassword(owner.getId(), new PasswordUpdateRequest(OLD_PASSWORD, NEW_PASSWORD));
    }

    private int activeSessions() {
        return db.queryForObject("SELECT count(*) FROM refresh_token WHERE user_id=? AND revoked_at IS NULL", Integer.class, owner.getId());
    }

    private int resetCount() {
        return db.queryForObject("SELECT count(*) FROM account_token WHERE user_id=? AND purpose=?", Integer.class,
            owner.getId(), AccountTokenService.PASSWORD_RESET);
    }

    private void awaitBlocked(AtomicInteger pid) {
        // Observe an actual PostgreSQL lock wait instead of assuming scheduling order.
        await().atMost(Duration.ofSeconds(10)).until(() -> pid.get() != 0
            && db.queryForObject("SELECT cardinality(pg_blocking_pids(?))", Integer.class, pid.get()) > 0);
    }

    private static void waitFor(CountDownLatch latch) {
        try {
            if (!latch.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for concurrent operation");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
