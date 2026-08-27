package com.stoxsim.common.ratelimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ApiRateLimitFilter extends OncePerRequestFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiRateLimitFilter.class);
    private static final int WINDOW_SECONDS = 60;
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>(
        "local count = redis.call('INCR', KEYS[1]); "
            + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
            + "return count;",
        Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private final MeterRegistry meterRegistry;

    public ApiRateLimitFilter(
        StringRedisTemplate redisTemplate,
        RateLimitProperties properties,
        MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.isEnabled()
            || HttpMethod.OPTIONS.matches(request.getMethod())
            || !request.getRequestURI().startsWith("/api/v1/")
            || request.getRequestURI().equals("/api/v1/system/status");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        RateLimitPolicy policy = policyFor(request.getMethod(), request.getRequestURI());
        long window = Instant.now().getEpochSecond() / WINDOW_SECONDS;
        String key = "stoxsim:rate-limit:" + policy.name() + ":"
            + actorHash(request) + ":" + window;

        long count;
        try {
            count = increment(key);
        } catch (RuntimeException exception) {
            meterRegistry.counter("stoxsim.rate_limit.storage_failures").increment();
            LOGGER.warn("Rate-limit storage is unavailable; allowing request", exception);
            filterChain.doFilter(request, response);
            return;
        }

        long remaining = Math.max(0, policy.limit() - count);
        long resetAt = (window + 1) * WINDOW_SECONDS;
        response.setHeader("X-RateLimit-Limit", String.valueOf(policy.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetAt));

        if (count > policy.limit()) {
            meterRegistry.counter(
                "stoxsim.rate_limit.rejections",
                "policy",
                policy.name()
            ).increment();
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(WINDOW_SECONDS));
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"code\":\"RATE_LIMITED\","
                    + "\"message\":\"Too many requests. Please try again shortly.\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    long increment(String key) {
        Long count = redisTemplate.execute(
            INCREMENT,
            List.of(key),
            String.valueOf(WINDOW_SECONDS + 5)
        );
        return count == null ? 1 : count;
    }

    RateLimitPolicy policyFor(String method, String path) {
        if (path.equals("/api/v1/auth/register")
            || path.equals("/api/v1/auth/login")
            || path.equals("/api/v1/auth/password/forgot")
            || path.equals("/api/v1/auth/password/reset")
            || path.startsWith("/api/v1/auth/email-verification/")) {
            return new RateLimitPolicy("auth", properties.getAuthPerMinute());
        }
        if (path.equals("/api/v1/auth/refresh") || path.equals("/api/v1/auth/logout")) {
            return new RateLimitPolicy("refresh", properties.getRefreshPerMinute());
        }
        if (path.equals("/api/v1/finwiz/ask")) {
            return new RateLimitPolicy("finwiz", properties.getFinwizPerMinute());
        }
        if (path.equals("/api/v1/reports/weekly/preview")) {
            return new RateLimitPolicy(
                "report_preview",
                properties.getFinwizPerMinute()
            );
        }
        if (HttpMethod.POST.matches(method)
            || HttpMethod.PUT.matches(method)
            || HttpMethod.PATCH.matches(method)
            || HttpMethod.DELETE.matches(method)) {
            return new RateLimitPolicy("write", properties.getWritesPerMinute());
        }
        return new RateLimitPolicy("general", properties.getGeneralPerMinute());
    }

    private String actorHash(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication != null
            && authentication.isAuthenticated()
            && StringUtils.hasText(authentication.getName())
                ? "user:" + authentication.getName()
                : "ip:" + request.getRemoteAddr();
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(actor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    record RateLimitPolicy(String name, int limit) {
    }
}
