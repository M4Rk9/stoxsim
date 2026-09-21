package com.stoxsim.analytics.repository;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.stoxsim.analytics.api.ActivityOverview.*;

@Repository
public class ActivityRepository {
    private final JdbcTemplate jdbc;
    public ActivityRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static Date utcDate(Instant instant) {
        return Date.valueOf(instant.atZone(java.time.ZoneOffset.UTC).toLocalDate());
    }

    public Instant startedAt() {
        return jdbc.queryForObject("SELECT started_at FROM analytics_coverage WHERE singleton", Timestamp.class).toInstant();
    }

    public long active(Instant start, Instant end) {
        return jdbc.queryForObject("""
            SELECT COUNT(DISTINCT a.user_id) FROM product_activity a
            JOIN app_user u ON u.id = a.user_id
            WHERE u.platform_role = 'USER' AND a.event_name <> 'ORDER_EXECUTED'
                AND a.event_day >= ? AND a.event_day <= ?
                AND a.first_at >= ? AND a.first_at < ?
            """, Long.class, utcDate(start), utcDate(end), Timestamp.from(start), Timestamp.from(end));
    }

    public List<DailyActive> daily(Instant start, Instant end) {
        return jdbc.query("""
            SELECT event_day, COUNT(DISTINCT a.user_id) AS total FROM product_activity a
            JOIN app_user u ON u.id = a.user_id
            WHERE u.platform_role = 'USER' AND a.event_name <> 'ORDER_EXECUTED'
                AND a.event_day >= ? AND a.event_day <= ?
                AND a.first_at >= ? AND a.first_at < ?
            GROUP BY event_day ORDER BY event_day
            """, (rs, row) -> new DailyActive(rs.getDate("event_day").toLocalDate(), rs.getLong("total")),
            utcDate(start), utcDate(end), Timestamp.from(start), Timestamp.from(end));
    }

    public Activation activation(Instant start, Instant end, Instant coverage, Instant now) {
        return jdbc.queryForObject("""
            WITH cohort AS (
                SELECT id, created_at FROM app_user
                WHERE platform_role = 'USER' AND created_at >= ? AND created_at < ?
            ), measured AS (
                SELECT c.id, c.created_at,
                    MIN(a.first_at) FILTER (WHERE event_name = 'STOCK_OPENED') AS research,
                    MIN(a.first_at) FILTER (WHERE event_name = 'WATCHLIST_ADDED') AS watchlist,
                    MIN(a.first_at) FILTER (WHERE event_name = 'ORDER_SUBMITTED') AS submitted,
                    MIN(a.first_at) FILTER (WHERE event_name = 'ORDER_EXECUTED') AS executed
                FROM cohort c LEFT JOIN product_activity a ON a.user_id = c.id
                    AND a.first_at >= c.created_at AND a.first_at < c.created_at + interval '7 days'
                    AND a.first_at < ?
                WHERE c.created_at >= ? AND c.created_at + interval '7 days' <= ?
                GROUP BY c.id, c.created_at
            )
            SELECT COUNT(*) AS eligible,
                (SELECT COUNT(*) FROM cohort WHERE created_at >= ? AND created_at + interval '7 days' > ?) AS immature,
                (SELECT COUNT(*) FROM cohort WHERE created_at < ?) AS excluded,
                COUNT(*) FILTER (WHERE research IS NOT NULL) AS researched,
                COUNT(*) FILTER (WHERE research IS NOT NULL AND watchlist IS NOT NULL) AS watchlisted,
                COUNT(*) FILTER (WHERE research IS NOT NULL AND watchlist IS NOT NULL AND submitted IS NOT NULL) AS activated,
                COUNT(*) FILTER (WHERE research IS NOT NULL AND watchlist IS NOT NULL AND submitted IS NOT NULL AND executed IS NOT NULL) AS executed,
                percentile_cont(0.5) WITHIN GROUP (ORDER BY EXTRACT(EPOCH FROM
                    (GREATEST(research, watchlist, submitted) - created_at)) / 3600.0)
                    FILTER (WHERE research IS NOT NULL AND watchlist IS NOT NULL AND submitted IS NOT NULL) AS median_hours
            FROM measured
            """, (rs, row) -> {
                long eligible = rs.getLong("eligible"), activated = rs.getLong("activated");
                return new Activation(eligible, rs.getLong("immature"), rs.getLong("excluded"),
                    rs.getLong("researched"), rs.getLong("watchlisted"), activated, rs.getLong("executed"),
                    eligible == 0 ? null : 100.0 * activated / eligible, (Double) rs.getObject("median_hours"));
            }, Timestamp.from(start), Timestamp.from(end), Timestamp.from(now), Timestamp.from(coverage),
            Timestamp.from(now), Timestamp.from(coverage), Timestamp.from(now), Timestamp.from(coverage));
    }

    public Retention retention(int day, Instant start, Instant end, Instant coverage, LocalDate today) {
        return jdbc.queryForObject("""
            WITH cohort AS (
                SELECT id, created_at, (created_at AT TIME ZONE 'UTC')::date + ? AS return_day
                FROM app_user WHERE platform_role = 'USER' AND created_at >= ? AND created_at < ?
            )
            SELECT COUNT(*) FILTER (WHERE created_at >= ? AND return_day < ?) AS eligible,
                COUNT(*) FILTER (WHERE created_at >= ? AND return_day >= ?) AS immature,
                COUNT(*) FILTER (WHERE created_at < ?) AS excluded,
                COUNT(*) FILTER (WHERE created_at >= ? AND return_day < ? AND EXISTS (
                    SELECT 1 FROM product_activity a WHERE a.user_id = cohort.id
                    AND a.event_day = cohort.return_day AND a.event_name <> 'ORDER_EXECUTED'
                )) AS returned
            FROM cohort
            """, (rs, row) -> {
                long eligible = rs.getLong("eligible"), returned = rs.getLong("returned");
                return new Retention(day, eligible, returned, rs.getLong("immature"), rs.getLong("excluded"),
                    eligible == 0 ? null : 100.0 * returned / eligible);
            }, day, Timestamp.from(start), Timestamp.from(end), Timestamp.from(coverage), Date.valueOf(today),
            Timestamp.from(coverage), Date.valueOf(today), Timestamp.from(coverage), Timestamp.from(coverage), Date.valueOf(today));
    }
}
