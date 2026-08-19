package com.stoxsim.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import com.stoxsim.auth.config.AuthProperties;

class RefreshCookieServiceTest {

    @Test
    void issuesAHostOnlySecureStrictHttpOnlyCookie() {
        AuthProperties properties = new AuthProperties();
        properties.setCookieSecure(true);
        properties.setRefreshTokenDays(30);
        RefreshCookieService service = new RefreshCookieService(properties);

        ResponseCookie cookie = service.issue("refresh-token");

        assertEquals(RefreshCookieService.COOKIE_NAME, cookie.getName());
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("Strict", cookie.getSameSite());
        assertEquals("/api/v1/auth", cookie.getPath());
        assertEquals(Duration.ofDays(30), cookie.getMaxAge());
        assertNull(cookie.getDomain());
    }

    @Test
    void clearsTheCookieWithTheSameSecurityAttributes() {
        AuthProperties properties = new AuthProperties();
        properties.setCookieSecure(true);
        RefreshCookieService service = new RefreshCookieService(properties);

        ResponseCookie cookie = service.clear();

        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("Strict", cookie.getSameSite());
        assertEquals(Duration.ZERO, cookie.getMaxAge());
    }
}
