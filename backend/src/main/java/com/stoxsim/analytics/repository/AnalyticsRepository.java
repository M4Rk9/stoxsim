package com.stoxsim.analytics.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.stoxsim.analytics.api.AnalyticsOverview.DailySignups;
import com.stoxsim.analytics.api.AnalyticsOverview.OrderCount;
import com.stoxsim.analytics.api.AnalyticsOverview.SignupCohort;
import com.stoxsim.analytics.api.AnalyticsOverview.UserTotals;

@Repository
public class AnalyticsRepository {
    private final JdbcTemplate jdbc;

    public AnalyticsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UserTotals users(Instant asOf) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) AS registered,
                   COUNT(*) FILTER (WHERE email_verified_at <= ?) AS verified
            FROM app_user WHERE platform_role = 'USER' AND created_at < ?
            """, (rs, row) -> new UserTotals(rs.getLong("registered"), rs.getLong("verified")),
            Timestamp.from(asOf), Timestamp.from(asOf));
    }

    public SignupCohort cohort(Instant start, Instant end, Instant asOf) {
        return jdbc.queryForObject("""
            SELECT COUNT(*) AS registered, COUNT(*) FILTER (WHERE EXISTS (
                SELECT 1 FROM virtual_account a
                JOIN paper_order o ON o.account_id = a.id
                WHERE a.user_id = u.id AND a.account_kind = 'STANDARD'
                  AND o.status = 'EXECUTED' AND o.executed_at < ?
            )) AS traded
            FROM app_user u
            WHERE u.platform_role = 'USER' AND u.created_at >= ? AND u.created_at < ?
            """, (rs, row) -> {
                long registered = rs.getLong("registered");
                long traded = rs.getLong("traded");
                return new SignupCohort(registered, traded,
                    registered == 0 ? null : 100.0 * traded / registered);
            }, Timestamp.from(asOf), Timestamp.from(start), Timestamp.from(end));
    }

    public List<DailySignups> signups(Instant start, Instant end) {
        return jdbc.query("""
            SELECT (created_at AT TIME ZONE 'UTC')::date AS day, COUNT(*) AS registered
            FROM app_user
            WHERE platform_role = 'USER' AND created_at >= ? AND created_at < ?
            GROUP BY day ORDER BY day
            """, (rs, row) -> new DailySignups(rs.getDate("day").toLocalDate(), rs.getLong("registered")),
            Timestamp.from(start), Timestamp.from(end));
    }

    public List<OrderCount> orders(Instant start, Instant end) {
        return jdbc.query("""
            SELECT a.market_region, o.status, COUNT(*) AS total
            FROM paper_order o
            JOIN virtual_account a ON a.id = o.account_id
            JOIN app_user u ON u.id = a.user_id
            WHERE u.platform_role = 'USER' AND a.account_kind = 'STANDARD'
              AND o.created_at >= ? AND o.created_at < ?
            GROUP BY a.market_region, o.status ORDER BY a.market_region, o.status
            """, (rs, row) -> new OrderCount(rs.getString("market_region"), rs.getString("status"),
                rs.getLong("total")), Timestamp.from(start), Timestamp.from(end));
    }
}
