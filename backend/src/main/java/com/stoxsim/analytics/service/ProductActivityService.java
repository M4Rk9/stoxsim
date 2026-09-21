package com.stoxsim.analytics.service;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class ProductActivityService {
    public static final int RETENTION_DAYS = 180;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ProductActivityService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // Same transaction: rolled-back actions never contribute to analytics.
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void recordDomainEvent(ProductActivityEvent event) {
        record(event.userId(), event.kind());
    }

    @Transactional(timeout = 5)
    public void record(UUID userId, ProductActivityEvent.Kind kind) {
        var now = clock.instant();
        jdbc.update("""
            INSERT INTO product_activity(user_id, event_day, event_name, first_at)
            SELECT id, ?, ?, ? FROM app_user
            WHERE id = ? AND platform_role = 'USER'
            ON CONFLICT (user_id, event_day, event_name) DO NOTHING
            """, Date.valueOf(now.atZone(ZoneOffset.UTC).toLocalDate()), kind.name(), Timestamp.from(now), userId);
    }

    // Bounded batches avoid a long cleanup transaction on a busy primary database.
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    @Transactional(timeout = 10)
    public void purgeExpired() {
        var cutoff = clock.instant().atZone(ZoneOffset.UTC).toLocalDate().minusDays(RETENTION_DAYS - 1);
        jdbc.update("""
            DELETE FROM product_activity WHERE ctid IN (
                SELECT ctid FROM product_activity WHERE event_day < ? LIMIT 5000
            )
            """, Date.valueOf(cutoff));
    }
}
