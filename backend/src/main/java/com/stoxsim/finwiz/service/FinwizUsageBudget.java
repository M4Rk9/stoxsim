package com.stoxsim.finwiz.service;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.stoxsim.finwiz.config.FinwizProperties;

/** Shared atomic budgets count provider attempts, including failed or timed-out requests. */
@Service
public class FinwizUsageBudget {
    private final JdbcTemplate db;
    private final Clock clock;
    private final FinwizProperties properties;
    public FinwizUsageBudget(JdbcTemplate db, Clock clock, FinwizProperties properties) {
        this.db = db; this.clock = clock; this.properties = properties;
    }
    public record Reservation(LocalDate day) { }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public Reservation reserve(UUID user, FinwizDepth depth) {
        int userLimit = Math.max(0, Math.min(10000, switch (depth) {
            case BASIC -> properties.getBasicDailyProviderRequests();
            case EXPANDED -> properties.getExpandedDailyProviderRequests();
            case FULL -> properties.getFullDailyProviderRequests();
        }));
        int globalLimit = Math.max(0, Math.min(100000, properties.getGlobalDailyProviderRequests()));
        if (userLimit == 0 || globalLimit == 0) return null;
        Date day = Date.valueOf(clock.instant().atZone(ZoneOffset.UTC).toLocalDate());
        db.update("INSERT INTO finwiz_global_usage_daily(usage_day) VALUES(?) ON CONFLICT DO NOTHING", day);
        // Every reservation acquires locks in the same order. This serializes only a short DB operation, never a provider call.
        int global = db.queryForObject("SELECT provider_attempts FROM finwiz_global_usage_daily WHERE usage_day=? FOR UPDATE", Integer.class, day);
        if (global >= globalLimit) return null;
        db.update("INSERT INTO finwiz_usage_daily(user_id,usage_day) VALUES(?,?) ON CONFLICT DO NOTHING", user, day);
        int used = db.queryForObject("SELECT provider_attempts FROM finwiz_usage_daily WHERE user_id=? AND usage_day=? FOR UPDATE", Integer.class, user, day);
        if (used >= userLimit) return null;
        db.update("UPDATE finwiz_usage_daily SET provider_attempts=provider_attempts+1 WHERE user_id=? AND usage_day=?", user, day);
        db.update("UPDATE finwiz_global_usage_daily SET provider_attempts=provider_attempts+1 WHERE usage_day=?", day);
        return new Reservation(day.toLocalDate());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void recordReportedTokens(LocalDate reservedDay, long tokens) {
        if (tokens < 0 || tokens > 1000000) return;
        db.update("""
            UPDATE finwiz_global_usage_daily
            SET reported_total_tokens=reported_total_tokens+?, responses_with_usage=responses_with_usage+1 WHERE usage_day=?
            """, tokens, Date.valueOf(reservedDay));
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    @Transactional(timeout = 10)
    public void purgeExpired() {
        Date cutoff = Date.valueOf(clock.instant().atZone(ZoneOffset.UTC).toLocalDate().minusDays(179));
        db.update("DELETE FROM finwiz_usage_daily WHERE ctid IN(SELECT ctid FROM finwiz_usage_daily WHERE usage_day<? LIMIT 5000)", cutoff);
        db.update("DELETE FROM finwiz_global_usage_daily WHERE usage_day<?", cutoff);
    }
}
