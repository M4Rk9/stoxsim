package com.stoxsim.common.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

class MetricsScrapeFilterTest {

    private static final String SCRAPE_TOKEN = "a-strong-test-scrape-token";

    @Test
    void rejectsMissingScrapeToken() throws Exception {
        var filter = new MetricsScrapeFilter(SCRAPE_TOKEN);
        var request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void rejectsBearerSchemeToAvoidJwtAuthenticationCollision() throws Exception {
        var filter = new MetricsScrapeFilter(SCRAPE_TOKEN);
        var request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader("Authorization", "Bearer " + SCRAPE_TOKEN);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void acceptsMatchingMetricsToken() throws Exception {
        var filter = new MetricsScrapeFilter(SCRAPE_TOKEN);
        var request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader("Authorization", "Metrics " + SCRAPE_TOKEN);
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }

    @Test
    void metricsSchemeIsIgnoredBySpringBearerTokenResolver() {
        var request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader("Authorization", "Metrics " + SCRAPE_TOKEN);

        assertNull(new DefaultBearerTokenResolver().resolve(request));
    }
}
