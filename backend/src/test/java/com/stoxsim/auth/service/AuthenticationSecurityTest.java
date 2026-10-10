package com.stoxsim.auth.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.stoxsim.auth.repository.*;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.api.dto.*;
import com.stoxsim.account.service.AccountService;
import com.stoxsim.subscription.service.SubscriptionService;
import com.stoxsim.common.error.UnauthorizedException;

@ExtendWith(MockitoExtension.class)
class AuthenticationSecurityTest {
    @Mock AppUserRepository users;
    @Mock RefreshTokenRepository sessions;
    @Mock PasswordEncoder passwords;
    @Mock AccountService accounts;
    @Mock TokenService tokens;
    @Mock AccountLifecycleService lifecycle;
    @Mock SubscriptionService subscriptions;
    AuthenticationService service;
    UUID id = UUID.randomUUID();
    AppUser user;
    @BeforeEach void setup() {
        service = new AuthenticationService(users, sessions, passwords, accounts, tokens, lifecycle, subscriptions);
        user = new AppUser("old@example.test", "hash", "Owner");
    }
    private void prepareLockedUser() {
        when(users.findByIdForUpdate(id)).thenReturn(Optional.of(user));
    }
    @Test void stolenSessionCannotChangeEmailWithoutPassword() {
        prepareLockedUser();
        assertThatThrownBy(() -> service.updateProfile(id,
            new ProfileUpdateRequest("attacker@example.test", "Owner")))
            .isInstanceOf(UnauthorizedException.class);
        assertThat(user.getEmail()).isEqualTo("old@example.test");
        verifyNoInteractions(lifecycle);
    }
    @Test void wrongPasswordCannotChangeEmail() {
        prepareLockedUser();
        assertThatThrownBy(() -> service.updateProfile(id,
            new ProfileUpdateRequest("attacker@example.test", "Owner", "wrong")))
            .isInstanceOf(UnauthorizedException.class);
        assertThat(user.getEmail()).isEqualTo("old@example.test");
    }
    @Test void emailChangeInvalidatesLinksAndSessions() {
        prepareLockedUser();
        when(passwords.matches("correct", "hash")).thenReturn(true);
        service.updateProfile(id, new ProfileUpdateRequest("new@example.test", "Owner", "correct"));
        assertThat(user.getEmail()).isEqualTo("new@example.test");
        var order = inOrder(lifecycle);
        order.verify(lifecycle).invalidateAccountLinks(id);
        order.verify(lifecycle).revokeAllSessions(id);
        order.verify(lifecycle).sendVerification(id);
    }
    @Test void nameChangeDoesNotRequirePasswordOrRevokeSessions() {
        prepareLockedUser();
        service.updateProfile(id, new ProfileUpdateRequest("old@example.test", "New name"));
        assertThat(user.getDisplayName()).isEqualTo("New name");
        verify(lifecycle, never()).invalidateAccountLinks(any());
        verify(lifecycle, never()).revokeAllSessions(any());
        verifyNoInteractions(passwords);
    }
    @Test void passwordChangeInvalidatesOutstandingRecoveryLinks() {
        prepareLockedUser();
        when(passwords.matches("correct", "hash")).thenReturn(true);
        service.updatePassword(id, new PasswordUpdateRequest("correct", "new-password"));
        verify(lifecycle).invalidateAccountLinks(id);
        verify(lifecycle).revokeAllSessions(id);
    }
    @Test void loginLocksTheNormalizedAccountBeforePasswordVerificationAndTokenCreation() {
        when(users.findByEmailIgnoreCaseForUpdate("old@example.test")).thenReturn(Optional.of(user));
        when(passwords.matches("correct", "hash")).thenReturn(true);
        when(tokens.issueTokenPair(user, "Browser")).thenReturn(new TokenService.TokenPair("access", "refresh", 900L));

        assertThat(service.login(new LoginRequest(" OLD@EXAMPLE.TEST ", "correct"), "Browser").accessToken())
            .isEqualTo("access");

        var order = inOrder(users, passwords, tokens);
        order.verify(users).findByEmailIgnoreCaseForUpdate("old@example.test");
        order.verify(passwords).matches("correct", "hash");
        order.verify(tokens).issueTokenPair(user, "Browser");
        verify(users, never()).findByEmailIgnoreCase(anyString());
    }
    @Test void loginRejectsTheUpdatedHashWithoutCreatingASession() {
        user.changePassword("updated-hash");
        when(users.findByEmailIgnoreCaseForUpdate("old@example.test")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.login(new LoginRequest("old@example.test", "old-password")))
            .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid email or password");

        verify(passwords).matches("old-password", "updated-hash");
        verifyNoInteractions(tokens, lifecycle, accounts);
    }
}
