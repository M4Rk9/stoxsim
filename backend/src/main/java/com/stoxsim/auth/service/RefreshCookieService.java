package com.stoxsim.auth.service;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.stoxsim.auth.config.AuthProperties;

@Service
public class RefreshCookieService {

    public static final String COOKIE_NAME = "stoxsim_refresh";

    private final AuthProperties properties;

    public RefreshCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie issue(String refreshToken) {
        return cookie(refreshToken)
            .maxAge(Duration.ofDays(properties.getRefreshTokenDays()))
            .build();
    }

    public ResponseCookie clear() {
        return cookie("")
            .maxAge(Duration.ZERO)
            .build();
    }

    private ResponseCookie.ResponseCookieBuilder cookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(properties.isCookieSecure())
            .sameSite("Strict")
            .path("/api/v1/auth");
    }
}
