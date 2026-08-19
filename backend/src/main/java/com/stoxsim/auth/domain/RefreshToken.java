package com.stoxsim.auth.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "session_started_at", nullable = false)
    private Instant sessionStartedAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "user_agent", nullable = false, length = 200)
    private String userAgent;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    public RefreshToken(
        AppUser user,
        String tokenHash,
        Instant expiresAt,
        UUID sessionId,
        Instant sessionStartedAt,
        String userAgent
    ) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.sessionId = sessionId;
        this.sessionStartedAt = sessionStartedAt;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
        this.lastUsedAt = this.createdAt;
    }

    public UUID getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public Instant getSessionStartedAt() {
        return sessionStartedAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
            lastUsedAt = now;
        }
    }
}
