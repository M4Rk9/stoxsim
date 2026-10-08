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
        when(users.findByIdForUpdate(id)).thenReturn(Optional.of(user));
    }
    @Test void stolenSessionCannotChangeEmailWithoutPassword() {
        assertThatThrownBy(() -> service.updateProfile(id,
            new ProfileUpdateRequest("attacker@example.test", "Owner")))
            .isInstanceOf(UnauthorizedException.class);
        assertThat(user.getEmail()).isEqualTo("old@example.test");
        verifyNoInteractions(lifecycle);
    }
    @Test void wrongPasswordCannotChangeEmail() {
        assertThatThrownBy(() -> service.updateProfile(id,
            new ProfileUpdateRequest("attacker@example.test", "Owner", "wrong")))
            .isInstanceOf(UnauthorizedException.class);
        assertThat(user.getEmail()).isEqualTo("old@example.test");
    }
    @Test void emailChangeInvalidatesLinksAndSessions() {
        when(passwords.matches("correct", "hash")).thenReturn(true);
        service.updateProfile(id, new ProfileUpdateRequest("new@example.test", "Owner", "correct"));
        assertThat(user.getEmail()).isEqualTo("new@example.test");
        var order = inOrder(lifecycle);
        order.verify(lifecycle).invalidateAccountLinks(id);
        order.verify(lifecycle).revokeAllSessions(id);
        order.verify(lifecycle).sendVerification(id);
    }
    @Test void nameChangeDoesNotRequirePasswordOrRevokeSessions() {
        service.updateProfile(id, new ProfileUpdateRequest("old@example.test", "New name"));
        assertThat(user.getDisplayName()).isEqualTo("New name");
        verify(lifecycle, never()).invalidateAccountLinks(any());
        verify(lifecycle, never()).revokeAllSessions(any());
        verifyNoInteractions(passwords);
    }
    @Test void passwordChangeInvalidatesOutstandingRecoveryLinks() {
        when(passwords.matches("correct", "hash")).thenReturn(true);
        service.updatePassword(id, new PasswordUpdateRequest("correct", "new-password"));
        verify(lifecycle).invalidateAccountLinks(id);
        verify(lifecycle).revokeAllSessions(id);
    }
}
