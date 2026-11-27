package com.stoxsim.finwiz.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import com.stoxsim.finwiz.config.FinwizProperties;
import com.stoxsim.finwiz.config.FinwizSpendProperties;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** INR millionths. A missing usage breakdown retains the reservation as a liability. */
@Service
@EnableConfigurationProperties(FinwizSpendProperties.class)
public class FinwizSpendBudget {
    private final JdbcTemplate db;
    private final FinwizSpendProperties settings;
    private final MeterRegistry metrics;
    private final Clock clock;
    private final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(getClass());

    public FinwizSpendBudget(JdbcTemplate db, FinwizSpendProperties settings, FinwizProperties finwiz, MeterRegistry metrics, Clock clock) {
        settings.validate(finwiz.getApiKey());
        this.db = db;
        this.settings = settings;
        this.metrics = metrics;
        this.clock = clock;
    }

    FinwizSpendProperties settings() { return settings; }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public UUID reserve(FinwizUsageBudget.RequestReservation request, FinwizDepth depth, int estimatedInput, int output) {
        if (!settings.enabled() || depth == FinwizDepth.BASIC || lock()) return null;
        LocalDate day = request.day();
        if (!day.equals(LocalDate.now(clock.withZone(ZoneOffset.UTC)))) return null;
        Date date = Date.valueOf(day);
        String plan = depth == FinwizDepth.EXPANDED ? "PLUS" : "PRO";
        if (!Boolean.TRUE.equals(db.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM user_subscription s JOIN finwiz_request_reservation r USING(user_id)
                WHERE r.id=? AND r.user_id=? AND r.usage_day=? AND r.depth=?
                  AND r.state='PENDING' AND r.expires_at>now()
                  AND s.plan=? AND s.subscription_status='ACTIVE' AND s.billing_access_until>now()
                  AND s.billing_provider IN ('RAZORPAY','GOOGLE_PLAY')
            )
            """,Boolean.class,request.id(),request.user(),date,depth.name(),plan))) return null;
        Date month = Date.valueOf(day.withDayOfMonth(1));
        Date nextMonth = Date.valueOf(day.withDayOfMonth(1).plusMonths(1));
        long estimate = Math.max(1, cost(estimatedInput, output, output));
        long tierLimit = depth == FinwizDepth.EXPANDED ? settings.plusMonthlyMicros() : settings.proMonthlyMicros();
        var used = db.queryForMap("""
            SELECT coalesce(sum(liability) FILTER (WHERE usage_day=?),0) AS day_total,
                   coalesce(sum(liability),0) AS month_total,
                   coalesce(sum(liability) FILTER (WHERE depth=?),0) AS tier_total,
                   coalesce(sum(liability) FILTER (WHERE usage_day=? AND user_id=?),0) AS user_total,
                   bool_or(overrun) AS estimate_overrun
            FROM (SELECT usage_day,depth,user_id,coalesce(reported_micros,reserved_micros) AS liability,
                         reported_micros > reserved_micros AS overrun
                  FROM finwiz_spend_attempt WHERE usage_day>=? AND usage_day<?) totals
            """,date,depth.name(),date,request.user(),month,nextMonth);
        if (Boolean.TRUE.equals(used.get("estimate_overrun"))) return null;
        if (!within("day", (Number) used.get("day_total"), estimate, settings.dailyMicros())
            || !within("month", (Number) used.get("month_total"), estimate, settings.monthlyMicros())
            || !within(depth.name(), (Number) used.get("tier_total"), estimate, tierLimit)
            || !within("user", (Number) used.get("user_total"), estimate, settings.userDailyMicros())) return null;
        UUID id = UUID.randomUUID();
        db.update("""
            INSERT INTO finwiz_spend_attempt(id,user_id,usage_day,provider,model,depth,reserved_micros)
            VALUES (?,?,?,'GEMINI_PAID',?,?,?)
            """, id, request.user(), date, settings.model(), depth.name(), estimate);
        return id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 5)
    public void settle(UUID id, JsonNode usage) {
        lock();
        Long input = tokens(usage,"promptTokenCount");
        Long output = tokens(usage,"candidatesTokenCount");
        Long reasoning = tokens(usage,"thoughtsTokenCount");
        Long total = tokens(usage,"totalTokenCount");
        if (input != null && output != null && reasoning == null && total != null && input + output == total) reasoning = 0L;
        if (input == null || output == null || reasoning == null || total == null || input + output + reasoning != total) {
            if (db.update("UPDATE finwiz_spend_attempt SET state='UNKNOWN' WHERE id=? AND state='PENDING'", id) == 1)
                metrics.counter("stoxsim.finwiz.spend.unknown").increment();
            return;
        }
        long reported = cost(input,output,reasoning);
        if (db.update("""
            UPDATE finwiz_spend_attempt SET reported_micros=?,input_tokens=?,output_tokens=?,reasoning_tokens=?,state='REPORTED'
            WHERE id=? AND state IN ('PENDING','UNKNOWN')
            """, reported,input,output,reasoning,id) != 1) return;
        long reserved = db.queryForObject("SELECT reserved_micros FROM finwiz_spend_attempt WHERE id=?", Long.class, id);
        if (reported > reserved) {
            metrics.counter("stoxsim.finwiz.spend.estimate_overrun").increment();
            logger.warn("Finwiz paid inference reported usage above its reservation");
        }
    }

    private boolean lock() {
        return Boolean.TRUE.equals(db.queryForObject("SELECT paused FROM finwiz_spend_guard WHERE id=1 FOR UPDATE", Boolean.class));
    }

    private boolean within(String scope, Number used, long estimate, long maximum) {
        boolean allowed = new BigDecimal(used.toString()).add(BigDecimal.valueOf(estimate))
            .compareTo(BigDecimal.valueOf(maximum)) <= 0;
        if (!allowed) {
            metrics.counter("stoxsim.finwiz.spend.rejected","scope",scope).increment();
            if (!scope.equals("user")) logger.warn("Finwiz paid inference spending limit reached for {}",scope);
        }
        return allowed;
    }

    private Long tokens(JsonNode usage, String field) {
        if (usage == null) return null;
        var node = usage.path(field);
        return node.isIntegralNumber() && node.canConvertToLong() && node.asLong() >= 0 && node.asLong() <= 10_000_000
            ? node.asLong() : null;
    }

    private long cost(long input, long output, long reasoning) {
        return BigDecimal.valueOf(settings.inputMicrosPerMillion()).multiply(BigDecimal.valueOf(input))
            .add(BigDecimal.valueOf(settings.outputMicrosPerMillion()).multiply(BigDecimal.valueOf(output)))
            .add(BigDecimal.valueOf(settings.reasoningMicrosPerMillion()).multiply(BigDecimal.valueOf(reasoning)))
            .movePointLeft(6).setScale(0,RoundingMode.CEILING).longValueExact();
    }

    public record SpendLine(String provider, String model, String depth, long attempts,
        BigDecimal reservedMicros, BigDecimal reportedMicros, long unknown, long pending,
        BigDecimal inputTokens, BigDecimal outputTokens, BigDecimal reasoningTokens) {}

    public List<SpendLine> report(LocalDate start, LocalDate end) {
        return db.query("""
            SELECT provider,model,depth,count(*) AS attempts,sum(reserved_micros) AS reserved,
                   sum(reported_micros) AS reported,count(*) FILTER (WHERE state='UNKNOWN') AS unknown,
                   count(*) FILTER (WHERE state='PENDING') AS pending,
                   sum(input_tokens) AS input,sum(output_tokens) AS output,sum(reasoning_tokens) AS reasoning
            FROM finwiz_spend_attempt WHERE usage_day BETWEEN ? AND ?
            GROUP BY provider,model,depth ORDER BY provider,model,depth
            """, (rs, n) -> new SpendLine(rs.getString("provider"), rs.getString("model"), rs.getString("depth"),
            rs.getLong("attempts"), rs.getBigDecimal("reserved"), rs.getBigDecimal("reported"),
            rs.getLong("unknown"),rs.getLong("pending"),rs.getBigDecimal("input"),rs.getBigDecimal("output"),
            rs.getBigDecimal("reasoning")), Date.valueOf(start), Date.valueOf(end));
    }

    @Scheduled(fixedDelay = 3600000, initialDelay = 3600000)
    @Transactional(timeout = 10)
    public void expireUnresolved() {
        db.update("UPDATE finwiz_spend_attempt SET state='UNKNOWN' WHERE state='PENDING' AND created_at < now()-interval '10 minutes'");
    }
}
