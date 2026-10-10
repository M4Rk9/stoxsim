package com.stoxsim.auth.service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthRecordCleanup {
    public static final int BATCH_SIZE = 5000;
    private final JdbcTemplate jdbc;
    public AuthRecordCleanup(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Scheduled(cron = "0 17 * * * *", zone = "UTC")
    @Transactional(timeout = 10)
    public void purgeExpired() {
        // Never erase the rolling recovery budget or unexpired rotation history.
        Timestamp expired = Timestamp.from(Instant.now().minus(Duration.ofDays(1)));
        jdbc.update("""
            DELETE FROM refresh_token WHERE id IN (
                SELECT id FROM refresh_token WHERE expires_at < ?
                ORDER BY expires_at LIMIT ? FOR UPDATE SKIP LOCKED
            )
            """, expired, BATCH_SIZE);
        jdbc.update("""
            DELETE FROM account_token WHERE id IN (
                SELECT id FROM account_token WHERE expires_at < ?
                ORDER BY expires_at LIMIT ? FOR UPDATE SKIP LOCKED
            )
            """, expired, BATCH_SIZE);
        jdbc.update("""
            DELETE FROM account_event WHERE id IN (
                SELECT id FROM account_event WHERE created_at < ?
                ORDER BY created_at LIMIT ? FOR UPDATE SKIP LOCKED
            )
            """, Timestamp.from(Instant.now().minus(Duration.ofDays(180))), BATCH_SIZE);
    }
}
