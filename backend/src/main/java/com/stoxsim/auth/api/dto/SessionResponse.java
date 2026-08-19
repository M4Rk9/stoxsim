package com.stoxsim.auth.api.dto;

import java.time.Instant;
import java.util.UUID;

import com.stoxsim.auth.domain.RefreshToken;

public record SessionResponse(
    UUID id,
    String device,
    Instant startedAt,
    Instant lastUsedAt,
    Instant expiresAt,
    boolean current
) {
    public static SessionResponse from(RefreshToken token, boolean current) {
        return new SessionResponse(
            token.getSessionId(),
            token.getUserAgent(),
            token.getSessionStartedAt(),
            token.getLastUsedAt(),
            token.getExpiresAt(),
            current
        );
    }
}
