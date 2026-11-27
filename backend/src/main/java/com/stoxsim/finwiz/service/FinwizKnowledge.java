package com.stoxsim.finwiz.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FinwizKnowledge {
    private static final Duration CACHE_TTL = Duration.ofHours(6);
    private static final String ANSWER_SQL = """
        e.basic_answer
        || CASE WHEN ? IN ('EXPANDED','FULL') THEN chr(10) || chr(10) || e.expanded_addendum ELSE '' END
        || CASE WHEN ? = 'FULL' THEN chr(10) || chr(10) || e.full_addendum ELSE '' END
        """;

    private final JdbcTemplate db;
    private final StringRedisTemplate redis;
    private final MeterRegistry metrics;
    private final Object[] cacheLocks = new Object[16];

    public FinwizKnowledge(JdbcTemplate db, StringRedisTemplate redis, MeterRegistry metrics) {
        this.db = db;
        this.redis = redis;
        this.metrics = metrics;
        java.util.Arrays.setAll(cacheLocks, ignored -> new Object());
    }

    record Lesson(String slug, int revision, String title, String sourceLabel, String sourceUrl,
                  LocalDate reviewedOn, String digest) { }

    Optional<Lesson> exact(String question, FinwizDepth depth) {
        String phrase = normalize(question);
        if (phrase.isEmpty() || phrase.length() > 180) return Optional.empty();
        return select("""
            FROM finwiz_knowledge_alias a
            JOIN finwiz_knowledge_edition e USING (slug, revision)
            WHERE a.phrase=? AND e.review_status='SOURCE_CHECKED'
            """, phrase, depth);
    }

    Optional<Lesson> reference(String slug, FinwizDepth depth) {
        if (slug == null || !slug.matches("[a-z0-9-]{1,64}")) return Optional.empty();
        return select("""
            FROM finwiz_knowledge_edition e
            WHERE e.slug=? AND e.review_status='SOURCE_CHECKED'
              AND EXISTS (SELECT 1 FROM finwiz_knowledge_alias a WHERE (a.slug,a.revision)=(e.slug,e.revision))
            ORDER BY e.revision DESC LIMIT 1
            """, slug, depth);
    }

    private Optional<Lesson> select(String from, String key, FinwizDepth depth) {
        try {
            var rows = db.query("""
                SELECT e.slug,e.revision,e.title,e.source_label,e.source_url,e.reviewed_on,
                       encode(sha256(convert_to((%s),'UTF8')),'hex') AS digest
                %s
                """.formatted(ANSWER_SQL, from), (rs, row) -> new Lesson(rs.getString("slug"),
                rs.getInt("revision"), rs.getString("title"), rs.getString("source_label"), rs.getString("source_url"),
                rs.getObject("reviewed_on", LocalDate.class), rs.getString("digest")), depth.name(), depth.name(), key);
            return rows.stream().findFirst();
        } catch (DataAccessException e) {
            metrics.counter("stoxsim.finwiz.knowledge", "result", "database_unavailable").increment();
            return Optional.empty();
        }
    }

    Optional<String> answer(Lesson lesson, FinwizDepth depth) {
        String key = "finwiz:lesson:v1:" + lesson.slug() + ":" + lesson.revision() + ":" + depth + ":" + lesson.digest();
        Object lock = cacheLocks[Math.floorMod(key.hashCode(), cacheLocks.length)];
        synchronized (lock) {
            try {
                String cached = redis.opsForValue().get(key);
                if (matches(cached, lesson)) {
                    metrics.counter("stoxsim.finwiz.knowledge.cache", "result", "hit").increment();
                    return Optional.of(cached);
                }
                metrics.counter("stoxsim.finwiz.knowledge.cache", "result", cached == null ? "miss" : "invalid").increment();
            } catch (DataAccessException e) {
                metrics.counter("stoxsim.finwiz.knowledge.cache", "result", "read_error").increment();
            }
            try {
                var rows = db.query("""
                    SELECT %s AS answer FROM finwiz_knowledge_edition e
                    WHERE e.slug=? AND e.revision=? AND e.review_status='SOURCE_CHECKED'
                    """.formatted(ANSWER_SQL), (rs, row) -> rs.getString("answer"),
                    depth.name(), depth.name(), lesson.slug(), lesson.revision());
                Optional<String> answer = rows.stream().filter(value -> matches(value, lesson)).findFirst();
                answer.ifPresent(value -> save(key, value));
                return answer;
            } catch (DataAccessException e) {
                metrics.counter("stoxsim.finwiz.knowledge", "result", "database_unavailable").increment();
                return Optional.empty();
            }
        }
    }

    private void save(String key, String value) {
        try {
            redis.opsForValue().set(key, value, CACHE_TTL);
        } catch (DataAccessException e) {
            metrics.counter("stoxsim.finwiz.knowledge.cache", "result", "write_error").increment();
        }
    }

    private static boolean matches(String body, Lesson lesson) {
        if (body == null || body.isBlank() || body.length() > 6000) return false;
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).equals(lesson.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace('’', '\'').strip().toLowerCase(Locale.ROOT)
            .replaceAll("[\\s?!.]+$", "").replaceAll("\\s+", " ");
    }
}
