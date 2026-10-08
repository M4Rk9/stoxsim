package com.stoxsim.auth.service;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stoxsim.common.error.UnauthorizedException;

@Service
public class AccountTokenService {

    public static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;

    public AccountTokenService(JdbcTemplate jdbcTemplate, TokenService tokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
    }

    @Transactional
    public String issue(UUID userId, String purpose, Duration lifetime) {
        lockUser(userId);
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
            UPDATE account_token
            SET consumed_at = ?
            WHERE user_id = ?
              AND purpose = ?
              AND consumed_at IS NULL
            """,
            Timestamp.from(now),
            userId,
            purpose
        );

        String rawToken = generate();
        jdbcTemplate.update(
            """
            INSERT INTO account_token (
                id, user_id, purpose, token_hash, expires_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            userId,
            purpose,
            tokenService.hash(rawToken),
            Timestamp.from(now.plus(lifetime)),
            Timestamp.from(now)
        );
        return rawToken;
    }

    @Transactional
    public UUID consume(String rawToken, String purpose) {
        String hash = tokenService.hash(rawToken);
        List<UUID> owners = jdbcTemplate.query(
            "SELECT user_id FROM account_token WHERE token_hash = ? AND purpose = ?",
            (resultSet, rowNumber) -> resultSet.getObject("user_id", UUID.class), hash, purpose
        );
        if (owners.isEmpty()) {
            throw new UnauthorizedException("The link is invalid, expired, or already used");
        }
        // Always lock the user before tokens, matching email/password updates.
        lockUser(owners.getFirst());
        List<UUID> users = jdbcTemplate.query(
            """
            UPDATE account_token
            SET consumed_at = CURRENT_TIMESTAMP
            WHERE token_hash = ?
              AND purpose = ?
              AND consumed_at IS NULL
              AND expires_at > CURRENT_TIMESTAMP
            RETURNING user_id
            """,
            (resultSet, rowNumber) -> resultSet.getObject("user_id", UUID.class),
            hash,
            purpose
        );
        if (users.isEmpty()) {
            throw new UnauthorizedException("The link is invalid, expired, or already used");
        }
        return users.getFirst();
    }

    @Transactional
    public void invalidateAll(UUID userId) {
        lockUser(userId);
        jdbcTemplate.update("""
            UPDATE account_token SET consumed_at = CURRENT_TIMESTAMP
            WHERE user_id = ? AND consumed_at IS NULL
            """, userId);
    }

    private void lockUser(UUID userId) {
        List<UUID> users = jdbcTemplate.query(
            "SELECT id FROM app_user WHERE id = ? FOR UPDATE",
            (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class), userId
        );
        if (users.isEmpty()) throw new UnauthorizedException("User no longer exists");
    }

    private String generate() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
