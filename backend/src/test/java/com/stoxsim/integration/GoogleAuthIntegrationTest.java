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
    "stoxsim.auth.google.enabled=true", "stoxsim.release.public-registration-enabled=true",
    "stoxsim.market-data.upstox.public-serving-enabled=true", "stoxsim.market-data.alpaca.public-serving-enabled=true", "stoxsim.auth.google.client-id=test.apps.googleusercontent.com",
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
    @Autowired com.stoxsim.release.PublicReleaseProperties release;
    @Autowired com.stoxsim.auth.service.AuthenticationService authentication;
    @Autowired com.stoxsim.auth.service.AccountLifecycleService lifecycle;
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
    @Test void registrationProvisionsConsentAccountsAndSubscriptionWithoutAPassword() {
        var response = google.register(new GoogleIdentityVerifier.Identity("new-subject", "new@gmail.com", true), "New Learner", true, "test");
        var registered = users.findById(response.user().id()).orElseThrow();
        assertThat(registered.getPasswordHash()).isNull();
        assertThat(response.user().passwordLoginEnabled()).isFalse();
        assertThat(response.user().emailVerified()).isTrue();
        assertThat(response.user().accounts()).isNotEmpty();
        assertThat(registered.getTermsVersion()).isEqualTo(com.stoxsim.auth.domain.LegalDocumentVersions.TERMS);
        assertThat(db.queryForObject("SELECT plan FROM user_subscription WHERE user_id = ?", String.class, registered.getId())).isEqualTo("FREE");
        assertThrows(ConflictException.class, () -> google.unlink(registered.getId(), "anything"));
        assertThrows(UnauthorizedException.class, () -> authentication.login(new com.stoxsim.auth.api.dto.LoginRequest("new@gmail.com", "anything")));
        google.setInitialPassword(registered.getId(), "new-subject", "new-local-password");
        assertThrows(JwtException.class, () -> decoder.decode(response.accessToken()));
        assertThat(authentication.login(new com.stoxsim.auth.api.dto.LoginRequest("new@gmail.com", "new-local-password")).user().passwordLoginEnabled()).isTrue();
        google.unlink(registered.getId(), "new-local-password");
        assertThrows(UnauthorizedException.class, () -> google.login("new-subject", "test"));
    }
    @Test void registrationNeverSilentlyLinksExistingEmailsOrBypassesReleaseGate() {
        assertThrows(ConflictException.class, () -> google.register(new GoogleIdentityVerifier.Identity("collision", user.getEmail(), true), "Learner", true, "test"));
        assertThat(google.linked(user.getId())).isFalse();
        var identity = new GoogleIdentityVerifier.Identity("new-subject", "new@gmail.com", true);
        assertThrows(IllegalArgumentException.class, () -> google.register(identity, "Learner", false, "test"));
        assertThrows(ConflictException.class, () -> google.register(new GoogleIdentityVerifier.Identity("untrusted", "thirdparty@example.test", false), "Learner", true, "test"));
        release.setPublicRegistrationEnabled(false);
        try { assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> google.register(identity, "Learner", true, "test")); }
        finally { release.setPublicRegistrationEnabled(true); }
        assertThat(users.count()).isEqualTo(1);
    }
    @Test void freshGoogleProofMustBelongToTheAuthenticatedAccount() {
        var response = google.register(new GoogleIdentityVerifier.Identity("new-subject", "new@gmail.com", true), "Learner", true, "test");
        var id = response.user().id();
        assertThrows(UnauthorizedException.class, () -> google.setInitialPassword(id, "wrong-subject", "new-password"));
        assertThrows(UnauthorizedException.class, () -> google.deleteWithGoogle(id, "wrong-subject"));
        google.deleteWithGoogle(id, "new-subject");
        assertThat(users.existsById(id)).isFalse();
        assertThat(db.queryForObject("SELECT count(*) FROM google_identity WHERE subject = 'new-subject'", Long.class)).isZero();
        assertThrows(JwtException.class, () -> decoder.decode(response.accessToken()));
    }
    @Test void browserRegistrationRequiresConsentAndOrigin() throws Exception {
        String payload = "{\"credential\":\"credential\",\"displayName\":\"Learner\",\"termsAccepted\":true}";
        mvc.perform(post("/api/v1/auth/google/register").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/google/register").header("Origin", "https://stoxsim.test").contentType(MediaType.APPLICATION_JSON)
            .content(payload.replace("true", "false"))).andExpect(status().isBadRequest());
        when(challenges.consume("binding")).thenReturn("nonce");
        when(verifier.identity("credential", "nonce", false)).thenReturn(new GoogleIdentityVerifier.Identity("registered", "register@gmail.com", true));
        mvc.perform(post("/api/v1/auth/google/register").header("Origin", "https://stoxsim.test")
            .cookie(new jakarta.servlet.http.Cookie(GoogleLoginChallenge.COOKIE, "binding")).contentType(MediaType.APPLICATION_JSON).content(payload))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.user.passwordLoginEnabled").value(false))
            .andExpect(jsonPath("$.refreshToken").doesNotExist()).andExpect(cookie().httpOnly("stoxsim_refresh", true));
    }
    @Test void nativeEndpointsRejectBrowserRequestsAndRequireExplicitConfiguration() throws Exception {
        mvc.perform(post("/api/v1/auth/google/native/challenge").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());
        when(verifier.nativeEnabled()).thenReturn(true);
        mvc.perform(post("/api/v1/auth/google/native/challenge").header("Origin", "https://stoxsim.test").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/google/native/challenge").contentType(MediaType.APPLICATION_FORM_URLENCODED)).andExpect(status().isUnsupportedMediaType());
        when(challenges.createNative()).thenReturn(new GoogleLoginChallenge.Challenge("binding", "nonce"));
        mvc.perform(post("/api/v1/auth/google/native/challenge").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.binding").value("binding")).andExpect(cookie().doesNotExist(GoogleLoginChallenge.COOKIE));
    }
    @Test void nativeLoginUsesItsOwnSingleUseChallengeAndExistingSessionCookie() throws Exception {
        google.link(user.getId(), "correct-password", "native-subject");
        when(verifier.nativeEnabled()).thenReturn(true);
        String binding = "a".repeat(43);
        when(challenges.consumeNative(binding)).thenReturn("nonce");
        when(verifier.identity("credential", "nonce", true)).thenReturn(new GoogleIdentityVerifier.Identity("native-subject", null, false));
        mvc.perform(post("/api/v1/auth/google/native/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"credential\":\"credential\",\"binding\":\"" + binding + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.id").value(user.getId().toString()))
            .andExpect(jsonPath("$.refreshToken").doesNotExist()).andExpect(cookie().secure("stoxsim_refresh", true));
        verify(challenges).consumeNative(binding);
        verify(challenges, never()).consume(any());
        for (String action : new String[]{"password", "delete"}) {
            mvc.perform(post("/api/v1/auth/google/native/" + action).contentType(MediaType.APPLICATION_JSON).content("{}"))
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
