package com.stoxsim.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.stoxsim.auth.domain.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("""
        select token from RefreshToken token
        where token.user.id = :userId
          and token.revokedAt is null
          and token.expiresAt > :now
        order by token.lastUsedAt desc
        """)
    List<RefreshToken> findActiveByUserId(
        @Param("userId") UUID userId,
        @Param("now") Instant now
    );

    @Query("""
        select token from RefreshToken token
        where token.user.id = :userId
          and token.sessionId = :sessionId
          and token.revokedAt is null
          and token.expiresAt > :now
        """)
    Optional<RefreshToken> findActiveSession(
        @Param("userId") UUID userId,
        @Param("sessionId") UUID sessionId,
        @Param("now") Instant now
    );

    @Modifying
    @Query("""
        update RefreshToken token
        set token.revokedAt = :now, token.lastUsedAt = :now
        where token.user.id = :userId
          and token.revokedAt is null
        """)
    int revokeAllActive(
        @Param("userId") UUID userId,
        @Param("now") Instant now
    );
}
