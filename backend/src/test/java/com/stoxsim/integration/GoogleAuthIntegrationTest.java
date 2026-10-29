package com.stoxsim.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.UUID;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.service.TokenService;
import com.stoxsim.auth.google.*;
import com.stoxsim.common.error.ConflictException;
import com.stoxsim.common.error.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(properties = {
    "stoxsim.auth.google.enabled=true", "stoxsim.auth.google.client-id=test.apps.googleusercontent.com",
    "stoxsim.frontend-url=https://stoxsim.test", "stoxsim.auth.cookie-secure=true",
    "stoxsim.market-data.upstox.stream-enabled=false", "stoxsim.market-data.upstox.instrument-sync-on-startup=false",
    "stoxsim.market-data.alpaca.instrument-sync-on-startup=false", "stoxsim.market-data.alpaca.polling-enabled=false",
    "stoxsim.portfolio.history.enabled=false", "stoxsim.security.rate-limit.enabled=false"
})
class GoogleAuthIntegrationTest {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @Autowired WebApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired GoogleAccountService google;
    @Autowired TokenService tokens;
    @Autowired JwtDecoder decoder;
    @Autowired JdbcTemplate db;
    @MockitoBean GoogleIdentityVerifier verifier;
    @MockitoBean GoogleLoginChallenge challenges;
    MockMvc mvc;
    AppUser user;
    @BeforeEach void setup() {
        db.execute("TRUNCATE app_user RESTART IDENTITY CASCADE");
        user = users.saveAndFlush(new AppUser("local@example.test", passwords.encode("correct-password"), "Learner"));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @Test void explicitLinkLoginRevocationAndDeletionUseExistingLocalAccount() {
        assertThrows(UnauthorizedException.class, () -> google.login("subject", "test"));
        assertThrows(UnauthorizedException.class, () -> google.link(user.getId(), "wrong", "subject"));
        google.link(user.getId(), "correct-password", "subject");
        assertThat(google.linked(user.getId())).isTrue();
        var login = google.login("subject", "test");
        assertThat(decoder.decode(login.accessToken()).getSubject()).isEqualTo(user.getId().toString());
        assertThat(users.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();
        google.unlink(user.getId(), "correct-password");
        assertThrows(JwtException.class, () -> decoder.decode(login.accessToken()));
        assertThrows(UnauthorizedException.class, () -> google.login("subject", "test"));
        google.link(user.getId(), "correct-password", "subject");
        users.deleteById(user.getId()); users.flush();
        assertThat(db.queryForObject("SELECT count(*) FROM google_identity", Long.class)).isZero();
    }
    @Test void cannotAttachOneGoogleIdentityToMultipleAccountsOrOverwriteALink() {
        var other = users.saveAndFlush(new AppUser("other@example.test", passwords.encode("correct-password"), "Other"));
        google.link(user.getId(), "correct-password", "subject");
        assertThrows(ConflictException.class, () -> google.link(other.getId(), "correct-password", "subject"));
        assertThrows(ConflictException.class, () -> google.link(user.getId(), "correct-password", "another-subject"));
    }
    @Test void challengeRequiresTrustedOriginAndSetsBoundCookie() throws Exception {
        when(challenges.create()).thenReturn(new GoogleLoginChallenge.Challenge("binding", "nonce"));
        mvc.perform(post("/api/v1/auth/google/challenge")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/google/challenge").header("Origin", "https://attacker.test")).andExpect(status().isForbidden());
        verifyNoInteractions(challenges);
        mvc.perform(post("/api/v1/auth/google/challenge").header("Origin", "https://stoxsim.test"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(cookie().httpOnly(GoogleLoginChallenge.COOKIE, true))
            .andExpect(cookie().secure(GoogleLoginChallenge.COOKIE, true))
            .andExpect(cookie().path(GoogleLoginChallenge.COOKIE, "/api/v1/auth/google"));
    }
    @Test void anonymousLinkAndUnlinkAreNeverPermitted() throws Exception {
        for (String action : new String[] {"link", "unlink"}) {
            mvc.perform(post("/api/v1/auth/google/" + action).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        }
    }
    @Test void loginIssuesHttpOnlyRefreshCookieWithoutLeakingRefreshToken() throws Exception {
        google.link(user.getId(), "correct-password", "subject");
        when(challenges.consume("binding")).thenReturn("nonce");
        when(verifier.verify("credential", "nonce")).thenReturn("subject");
        mvc.perform(post("/api/v1/auth/google/login").header("Origin", "https://stoxsim.test")
            .cookie(new jakarta.servlet.http.Cookie(GoogleLoginChallenge.COOKIE, "binding"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"credential\":\"credential\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andExpect(jsonPath("$.user.id").value(user.getId().toString()))
            .andExpect(cookie().httpOnly("stoxsim_refresh", true));
    }
}
