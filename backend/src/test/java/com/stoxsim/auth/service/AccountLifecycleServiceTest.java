package com.stoxsim.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.stoxsim.auth.config.AuthProperties;
import com.stoxsim.auth.domain.AppUser;
import com.stoxsim.auth.repository.AppUserRepository;
import com.stoxsim.auth.repository.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
class AccountLifecycleServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Duration TOKEN_LIFETIME = Duration.ofHours(24);

    @Mock private AppUserRepository users;
    @Mock private RefreshTokenRepository refreshTokens;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenService tokenService;
    @Mock private AccountTokenService accountTokens;
    @Mock private AccountMailService mailService;
    @Mock private AuthProperties properties;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private AppUser user;

    private AccountLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new AccountLifecycleService(
            users,
            refreshTokens,
            passwordEncoder,
            tokenService,
            accountTokens,
            mailService,
            properties,
            jdbcTemplate,
            transactionTemplate
        );
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        }).when(transactionTemplate).execute(any());
    }

    @Test
    void reportsWhenSmtpAcceptsAResentVerification() {
        prepareUnverifiedUser();
        when(mailService.resendVerification(user, "verification-token")).thenReturn(true);

        assertThat(service.resendVerification(USER_ID)).isTrue();

        verify(mailService).resendVerification(user, "verification-token");
    }

    @Test
    void reportsWhenSmtpRejectsAResentVerification() {
        prepareUnverifiedUser();
        when(mailService.resendVerification(user, "verification-token")).thenReturn(false);

        assertThat(service.resendVerification(USER_ID)).isFalse();

        verify(mailService).resendVerification(user, "verification-token");
    }

    @Test
    void doesNotIssueAnotherTokenForAnAlreadyVerifiedUser() {
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(user.isEmailVerified()).thenReturn(true);

        assertThat(service.resendVerification(USER_ID)).isTrue();

        verify(accountTokens, never()).issue(
            USER_ID,
            AccountTokenService.EMAIL_VERIFICATION,
            TOKEN_LIFETIME
        );
        verify(mailService, never()).resendVerification(user, "verification-token");
    }

    private void prepareUnverifiedUser() {
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        when(users.findById(USER_ID)).thenReturn(Optional.of(user));
        when(user.isEmailVerified()).thenReturn(false);
        when(properties.getEmailVerificationMinutes()).thenReturn(1_440L);
        when(accountTokens.issue(
            USER_ID,
            AccountTokenService.EMAIL_VERIFICATION,
            TOKEN_LIFETIME
        )).thenReturn("verification-token");
    }
}
