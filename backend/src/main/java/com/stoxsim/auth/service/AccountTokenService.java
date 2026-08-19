package com.stoxsim.auth.service;

import java.security.SecureRandom;
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
        Instant now = Instant.now();
        jdbcTemplate.update(
            """
            UPDATE account_token
            SET consumed_at = ?
            WHERE user_id = ?
              AND purpose = ?
              AND consumed_at IS NULL
            """,
            now,
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
            now.plus(lifetime),
            now
        );
        return rawToken;
    }

    @Transactional
    public UUID consume(String rawToken, String purpose) {
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
            tokenService.hash(rawToken),
            purpose
        );
        if (users.isEmpty()) {
            throw new UnauthorizedException("The link is invalid, expired, or already used");
        }
        return users.getFirst();
    }

    private String generate() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
