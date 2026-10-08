package com.stoxsim.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.UUID;
import com.stoxsim.auth.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class ActiveSessionJwtValidatorTest {
    private final RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
    private final ActiveSessionJwtValidator validator = new ActiveSessionJwtValidator(repository);
    private final UUID user = UUID.randomUUID(), session = UUID.randomUUID();

    private Jwt token(String subject, String sid, Instant expiry) {
        var builder = Jwt.withTokenValue("signed").header("alg", "HS256").subject(subject)
            .issuedAt(Instant.now().minusSeconds(10)).expiresAt(expiry);
        if (sid != null) builder.claim("sid", sid);
        return builder.build();
    }
    @Test void requiresActiveSessionOwnedByTokenSubject() {
        when(repository.existsByUser_IdAndSessionIdAndRevokedAtIsNullAndExpiresAtGreaterThan(eq(user), eq(session), any())).thenReturn(true);
        assertThat(validator.validate(token(user.toString(), session.toString(), Instant.now().plusSeconds(300))).hasErrors()).isFalse();
        verify(repository).existsByUser_IdAndSessionIdAndRevokedAtIsNullAndExpiresAtGreaterThan(eq(user), eq(session), any());
        assertThat(validator.validate(token(UUID.randomUUID().toString(), session.toString(), Instant.now().plusSeconds(300))).hasErrors()).isTrue();
    }
    @Test void rejectsRevokedMissingAndMalformedSessionsAndExpiredTokens() {
        assertThat(validator.validate(token(user.toString(), session.toString(), Instant.now().plusSeconds(300))).hasErrors()).isTrue();
        clearInvocations(repository);
        for (String sid : new String[] {null, "invalid"})
            assertThat(validator.validate(token(user.toString(), sid, Instant.now().plusSeconds(300))).hasErrors()).isTrue();
        assertThat(validator.validate(token("not-a-user", session.toString(), Instant.now().plusSeconds(300))).hasErrors()).isTrue();
        assertThat(validator.validate(token(user.toString(), session.toString(), Instant.now().minusSeconds(1))).hasErrors()).isTrue();
        verifyNoInteractions(repository);
    }
}
