package com.stoxsim.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import com.stoxsim.auth.repository.RefreshTokenRepository;

/** Shared by HTTP and STOMP: a signed token must still belong to a live session. */
@Component
public class ActiveSessionJwtValidator implements OAuth2TokenValidator<Jwt> {
    private static final OAuth2Error INVALID = new OAuth2Error("invalid_token", "The session is invalid or expired", null);
    private final RefreshTokenRepository sessions;
    // Security expiry follows wall-clock time, never the injectable simulation clock.
    private final Clock clock = Clock.systemUTC();

    public ActiveSessionJwtValidator(RefreshTokenRepository sessions) { this.sessions = sessions; }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        UUID user;
        UUID session;
        Instant now = clock.instant();
        try {
            user = UUID.fromString(token.getSubject());
            session = UUID.fromString(token.getClaimAsString("sid"));
            if (token.getExpiresAt() == null || !token.getExpiresAt().isAfter(now)
                || token.getIssuedAt() == null || token.getIssuedAt().isAfter(now.plusSeconds(60))) {
                return OAuth2TokenValidatorResult.failure(INVALID);
            }
        } catch (IllegalArgumentException | NullPointerException | ClassCastException invalidClaims) {
            return OAuth2TokenValidatorResult.failure(INVALID);
        }
        return sessions.existsByUser_IdAndSessionIdAndRevokedAtIsNullAndExpiresAtGreaterThan(user, session, now)
            ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(INVALID);
    }
}
