package com.stoxsim.common.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MetricsScrapeFilterTest {

    @Test
    void rejectsMissingScrapeToken() throws Exception {
        var filter = new MetricsScrapeFilter("a-strong-test-scrape-token");
        var request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    void acceptsMatchingBearerToken() throws Exception {
        var filter = new MetricsScrapeFilter("a-strong-test-scrape-token");
        var request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader("Authorization", "Bearer a-strong-test-scrape-token");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
    }
}
