package com.stoxsim.common.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void preservesSafeCallerRequestIdAndClearsMdc() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/system/status");
        request.addHeader(RequestCorrelationFilter.HEADER_NAME, "release-check-1234");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("release-check-1234", response.getHeader(RequestCorrelationFilter.HEADER_NAME));
        assertEquals(null, MDC.get("requestId"));
    }

    @Test
    void replacesUnsafeRequestId() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/system/status");
        request.addHeader(RequestCorrelationFilter.HEADER_NAME, "bad value\nforged");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNotNull(response.getHeader(RequestCorrelationFilter.HEADER_NAME));
    }
}
