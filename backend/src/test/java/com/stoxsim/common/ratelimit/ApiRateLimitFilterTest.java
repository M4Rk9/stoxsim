package com.stoxsim.common.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiRateLimitFilterTest {

    @Test
    void rejectsRequestsThatExceedTheEndpointPolicy() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setAuthPerMinute(2);
        ApiRateLimitFilter filter = filterReturning(3, properties);
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.setRemoteAddr("192.0.2.10");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(429, response.getStatus());
        assertEquals("2", response.getHeader("X-RateLimit-Limit"));
        assertEquals("60", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("RATE_LIMITED"));
    }

    @Test
    void allowsRequestsWithinTheEndpointPolicy() throws Exception {
        RateLimitProperties properties = new RateLimitProperties();
        properties.setGeneralPerMinute(5);
        ApiRateLimitFilter filter = filterReturning(1, properties);
        var request = new MockHttpServletRequest("GET", "/api/v1/portfolio");
        request.setRemoteAddr("192.0.2.11");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        assertEquals("5", response.getHeader("X-RateLimit-Limit"));
        assertEquals("4", response.getHeader("X-RateLimit-Remaining"));
    }

    private ApiRateLimitFilter filterReturning(
        long count,
        RateLimitProperties properties
    ) {
        return new ApiRateLimitFilter(null, properties) {
            @Override
            long increment(String key) {
                return count;
            }
        };
    }
}
