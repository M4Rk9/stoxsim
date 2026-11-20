package com.stoxsim.analytics.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import com.stoxsim.analytics.api.CohortOverview.*;

@Repository
public class CohortRepository {
    public enum Group { WEEK, SOURCE, DEVICE }
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    public CohortRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; this.named = new NamedParameterJdbcTemplate(jdbc); }
    public Instant strategyStartedAt() {
        return jdbc.queryForObject("SELECT strategy_journey_started_at FROM analytics_coverage WHERE singleton", Timestamp.class).toInstant();
    }
    public Instant dimensionsStartedAt() {
        return jdbc.queryForObject("SELECT signup_dimensions_started_at FROM analytics_coverage WHERE singleton", Timestamp.class).toInstant();
    }
    public List<Row> rows(Group group, Instant start, Instant end, Instant activityCoverage, Instant journeyCoverage, Instant now, LocalDate today) {
        // Only closed enum-selected SQL fragments are interpolated, never request text.
        String dimension = switch (group) {
            case WEEK -> "to_char(date_trunc('week',u.created_at AT TIME ZONE 'UTC'),'YYYY-MM-DD')";
            case SOURCE -> "COALESCE(s.source,'UNKNOWN')";
            case DEVICE -> "COALESCE(s.device,'UNKNOWN')";
        };
        String sql = """
            WITH cohort AS (
                SELECT u.id,u.created_at,(u.created_at AT TIME ZONE 'UTC')::date AS signup_day,
                    %s AS cohort_key, u.created_at >= :activityCoverage AS activity_observed,
                    u.created_at >= :journeyCoverage AS journey_observed,
                    u.created_at + interval '14 days' <= :now AS mature
                FROM app_user u LEFT JOIN signup_attribution s ON s.user_id=u.id
                WHERE u.platform_role='USER' AND NOT u.is_test_account
                    AND u.created_at >= :start AND u.created_at < :end
            ), measured AS (
                SELECT c.*,
                    a.research,a.watchlist,a.strategy,a.trade,a.review,
                    COALESCE(a.d1,false) AS d1, COALESCE(a.d7,false) AS d7,
                    COALESCE(a.days1to7,false) AS days1to7, COALESCE(a.week2,false) AS week2
                FROM cohort c LEFT JOIN LATERAL (
                    SELECT MIN(first_at) FILTER (WHERE event_name='STOCK_OPENED' AND first_at<c.created_at+interval '14 days') AS research,
                        MIN(first_at) FILTER (WHERE event_name='WATCHLIST_ADDED' AND first_at<c.created_at+interval '14 days') AS watchlist,
                        MIN(first_at) FILTER (WHERE event_name='STRATEGY_CREATED' AND first_at<c.created_at+interval '14 days') AS strategy,
                        MIN(first_at) FILTER (WHERE event_name='ORDER_EXECUTED' AND first_at<c.created_at+interval '14 days') AS trade,
                        MIN(first_at) FILTER (WHERE event_name='REVIEW_COMPLETED' AND first_at<c.created_at+interval '14 days') AS review,
                        bool_or(event_day=c.signup_day+1) FILTER (WHERE event_name IN ('ACTIVE','STOCK_OPENED','WATCHLIST_ADDED','ORDER_SUBMITTED')) AS d1,
                        bool_or(event_day=c.signup_day+7) FILTER (WHERE event_name IN ('ACTIVE','STOCK_OPENED','WATCHLIST_ADDED','ORDER_SUBMITTED')) AS d7,
                        bool_or(event_day BETWEEN c.signup_day+1 AND c.signup_day+7) FILTER (WHERE event_name IN ('ACTIVE','STOCK_OPENED','WATCHLIST_ADDED','ORDER_SUBMITTED')) AS days1to7,
                        bool_or(event_day BETWEEN c.signup_day+8 AND c.signup_day+14) FILTER (WHERE event_name IN ('ACTIVE','STOCK_OPENED','WATCHLIST_ADDED','ORDER_SUBMITTED')) AS week2
                    FROM product_activity WHERE user_id=c.id AND first_at>=c.created_at AND first_at<:now
                        AND event_day BETWEEN c.signup_day AND c.signup_day+14
                ) a ON true
            ), outcomes AS (
                SELECT m.*, EXISTS (
                    SELECT 1 FROM decision_reflection r JOIN paper_order o ON o.id=r.order_id AND o.account_id=r.account_id
                    JOIN virtual_account v ON v.id=o.account_id AND v.user_id=r.user_id
                    WHERE r.user_id=m.id AND v.account_kind='STANDARD' AND o.status='EXECUTED'
                        AND r.strategy_created_at>=m.created_at AND r.strategy_created_at>=m.research
                        AND o.executed_at>=r.strategy_created_at AND r.reviewed_at>=o.executed_at
                        AND r.reviewed_at<m.created_at+interval '14 days' AND r.reviewed_at<:now
                ) AS completed
                FROM measured m
            )
            SELECT cohort_key,COUNT(*) AS registrations,
                COUNT(*) FILTER (WHERE journey_observed AND mature) AS journey_eligible,
                COUNT(*) FILTER (WHERE journey_observed AND NOT mature) AS journey_immature,
                COUNT(*) FILTER (WHERE NOT journey_observed) AS journey_excluded,
                COUNT(*) FILTER (WHERE journey_observed AND mature AND research IS NOT NULL) AS researched,
                COUNT(*) FILTER (WHERE journey_observed AND mature AND watchlist IS NOT NULL) AS watchlisted,
                COUNT(*) FILTER (WHERE journey_observed AND mature AND strategy IS NOT NULL) AS strategies,
                COUNT(*) FILTER (WHERE journey_observed AND mature AND trade IS NOT NULL) AS traded,
                COUNT(*) FILTER (WHERE journey_observed AND mature AND review IS NOT NULL) AS reviewed,
                COUNT(*) FILTER (WHERE journey_observed AND mature AND completed) AS completed,
                COUNT(*) FILTER (WHERE NOT activity_observed) AS retention_excluded,
                %s
            FROM outcomes GROUP BY cohort_key ORDER BY cohort_key
            """.formatted(dimension, java.util.stream.Stream.of(new String[]{"d1","1"},new String[]{"d7","7"},
                new String[]{"days1to7","7"},new String[]{"week2","14"}).map(window -> """
                COUNT(*) FILTER (WHERE activity_observed AND signup_day+%s<:today) AS %s_eligible,
                COUNT(*) FILTER (WHERE activity_observed AND signup_day+%s>=:today) AS %s_immature,
                COUNT(*) FILTER (WHERE activity_observed AND signup_day+%s<:today AND %s) AS %s_returned
                """.formatted(window[1],window[0],window[1],window[0],window[1],window[0],window[0]))
                .collect(java.util.stream.Collectors.joining(",")));
        return named.query(sql, Map.of("start",Timestamp.from(start),"end",Timestamp.from(end),
            "activityCoverage",Timestamp.from(activityCoverage),"journeyCoverage",Timestamp.from(journeyCoverage),
            "now",Timestamp.from(now),"today",java.sql.Date.valueOf(today)), (rs,n) -> new Row(rs.getString("cohort_key"),
                rs.getLong("registrations"),rs.getLong("journey_eligible"),rs.getLong("journey_immature"),rs.getLong("journey_excluded"),
                rs.getLong("researched"),rs.getLong("watchlisted"),rs.getLong("strategies"),rs.getLong("traded"),rs.getLong("reviewed"),rs.getLong("completed"),
                rate(rs,"d1"),rate(rs,"d7"),rate(rs,"days1to7"),rate(rs,"week2")));
    }
    private Rate rate(java.sql.ResultSet rs,String name) throws java.sql.SQLException {
        long eligible=rs.getLong(name+"_eligible"),returned=rs.getLong(name+"_returned");
        return new Rate(eligible,returned,rs.getLong(name+"_immature"),rs.getLong("retention_excluded"),
            eligible==0?null:100.0*returned/eligible);
    }
}
