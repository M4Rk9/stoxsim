package com.stoxsim.common.observability;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class MetricsScrapeFilter extends OncePerRequestFilter {

    private static final String METRICS_PATH = "/actuator/prometheus";
    private static final String METRICS_AUTHORIZATION_PREFIX = "Metrics ";
    private final String scrapeToken;

    public MetricsScrapeFilter(
        @Value("${stoxsim.monitoring.scrape-token:}") String scrapeToken
    ) {
        this.scrapeToken = scrapeToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals(METRICS_PATH);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String supplied = authorization != null && authorization.startsWith(METRICS_AUTHORIZATION_PREFIX)
            ? authorization.substring(METRICS_AUTHORIZATION_PREFIX.length())
            : "";

        if (!StringUtils.hasText(scrapeToken)
            || !MessageDigest.isEqual(
                scrapeToken.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8)
            )) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
