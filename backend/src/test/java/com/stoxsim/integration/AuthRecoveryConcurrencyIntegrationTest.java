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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.stoxsim.auth.service.AuthRecordCleanup;
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
    @Autowired AuthRecordCleanup cleanup;
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

    @Test void overlongUnicodePasswordsReturnBadRequestWithoutMutatingCredentialsOrLinks() throws Exception {
        String password = "é".repeat(37); // 37 characters, 74 UTF-8 bytes.
        String reset = resetToken();
        String access = tokens.issueTokenPair(owner).accessToken();
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
            .content("{\"email\":\"new@example.test\",\"displayName\":\"New\",\"termsAccepted\":true,\"password\":\"" + password + "\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
            .content("{\"email\":\"" + owner.getEmail() + "\",\"password\":\"" + password + "\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/password/reset").contentType("application/json")
            .content("{\"token\":\"" + reset + "\",\"newPassword\":\"" + password + "\"}"))
            .andExpect(status().isBadRequest());
        for (String payload : new String[] {
            "{\"currentPassword\":\"" + OLD_PASSWORD + "\",\"newPassword\":\"" + password + "\"}",
            "{\"currentPassword\":\"" + password + "\",\"newPassword\":\"valid-new-password\"}"}) {
            mvc.perform(patch("/api/v1/auth/me/password").header("Authorization", "Bearer " + access)
                .contentType("application/json").content(payload)).andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/v1/auth/me").header("Authorization", "Bearer " + access).contentType("application/json")
            .content("{\"email\":\"changed@example.test\",\"displayName\":\"Owner\",\"currentPassword\":\"" + password + "\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/auth/me").header("Authorization", "Bearer " + access).contentType("application/json")
            .content("{\"password\":\"" + password + "\"}")).andExpect(status().isBadRequest());
        assertThat(passwords.matches(OLD_PASSWORD, users.findById(owner.getId()).orElseThrow().getPasswordHash())).isTrue();
        assertThat(accountTokens.consume(reset, AccountTokenService.PASSWORD_RESET)).isEqualTo(owner.getId());
    }

    @Test void cleanupIsBoundedAndPreservesLiveTokensRotationHistoryAndRecoveryBudget() {
        var original = tokens.issueTokenPair(owner);
        var rotated = authentication.refresh(original.refreshToken());
        for (int i = 0; i < 5; i++) resetToken();
        // A consumed/expired recent link still belongs to the recipient budget.
        db.update("UPDATE account_token SET consumed_at=now(),expires_at=now()-interval '1 minute' WHERE user_id=?", owner.getId());
        db.update("""
            INSERT INTO refresh_token(id,user_id,token_hash,expires_at,session_id,session_started_at,last_used_at,user_agent)
            SELECT gen_random_uuid(),?,md5(n::text)||md5(n::text),now()-interval '2 days',gen_random_uuid(),now(),now(),'cleanup fixture'
            FROM generate_series(1,5001) n
            """, owner.getId());
        db.update("""
            INSERT INTO account_token(id,user_id,purpose,token_hash,expires_at,created_at)
            SELECT gen_random_uuid(),?,'PASSWORD_RESET',md5(n::text)||md5(n::text),now()-interval '2 days',now()-interval '3 days'
            FROM generate_series(1,5001) n
            """, owner.getId());
        db.update("""
            INSERT INTO account_event(user_id,event_type,created_at)
            VALUES (?,'CLEANUP_FIXTURE',now()-interval '181 days')
            """, owner.getId());
        cleanup.purgeExpired();
        assertThat(db.queryForObject("SELECT count(*) FROM refresh_token WHERE expires_at<now()-interval '1 day'", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM account_token WHERE expires_at<now()-interval '1 day'", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT count(*) FROM account_event WHERE created_at<now()-interval '180 days'", Integer.class)).isEqualTo(1);
        cleanup.purgeExpired();
        assertThat(db.queryForObject("SELECT count(*) FROM refresh_token", Integer.class)).isEqualTo(2);
        assertThat(resetCount()).isEqualTo(5);
        assertThat(db.queryForObject("SELECT count(*) FROM account_event WHERE created_at<now()-interval '180 days'", Integer.class)).isEqualTo(1);
        lifecycle.requestPasswordReset(owner.getEmail());
        verifyNoInteractions(mail);
        assertThat(resetCount()).isEqualTo(5);
        // The old rotated token must still identify and revoke the active session.
        authentication.logout(original.refreshToken());
        assertThatThrownBy(() -> authentication.refresh(rotated.refreshToken())).isInstanceOf(UnauthorizedException.class);
    }

    @Test void cleanupSkipsRowsLockedByAnotherTransaction() throws Exception {
        String reset = resetToken();
        db.update("UPDATE account_token SET expires_at=now()-interval '2 days',created_at=now()-interval '3 days' WHERE user_id=?", owner.getId());
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            try {
                var holding = pool.submit(() -> transactions.executeWithoutResult(status -> {
                    db.queryForObject("SELECT id FROM account_token WHERE token_hash=? FOR UPDATE", UUID.class, tokens.hash(reset));
                    locked.countDown(); waitFor(release);
                }));
                waitFor(locked);
                cleanup.purgeExpired();
                assertThat(resetCount()).isEqualTo(1);
                release.countDown(); holding.get(10, TimeUnit.SECONDS);
            } finally { release.countDown(); }
        }
        cleanup.purgeExpired();
        assertThat(resetCount()).isZero();
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
