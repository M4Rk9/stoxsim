package com.stoxsim.auth.api;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.auth.api.dto.AuthResponse;
import com.stoxsim.auth.api.dto.LoginRequest;
import com.stoxsim.auth.api.dto.PasswordUpdateRequest;
import com.stoxsim.auth.api.dto.ProfileUpdateRequest;
import com.stoxsim.auth.api.dto.RefreshTokenRequest;
import com.stoxsim.auth.api.dto.RegisterRequest;
import com.stoxsim.auth.api.dto.UserResponse;
import com.stoxsim.auth.service.AuthenticationService;
import com.stoxsim.auth.service.RefreshCookieService;
import com.stoxsim.common.error.UnauthorizedException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final RefreshCookieService refreshCookieService;

    public AuthController(
        AuthenticationService authenticationService,
        RefreshCookieService refreshCookieService
    ) {
        this.authenticationService = authenticationService;
        this.refreshCookieService = refreshCookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return authenticated(HttpStatus.CREATED, authenticationService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return authenticated(HttpStatus.OK, authenticationService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
        @CookieValue(name = RefreshCookieService.COOKIE_NAME, required = false) String cookieToken,
        @Valid @RequestBody(required = false) RefreshTokenRequest request
    ) {
        AuthResponse response = authenticationService.refresh(requireRefreshToken(cookieToken, request));
        return authenticated(HttpStatus.OK, response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
        @CookieValue(name = RefreshCookieService.COOKIE_NAME, required = false) String cookieToken,
        @Valid @RequestBody(required = false) RefreshTokenRequest request
    ) {
        String refreshToken = resolveRefreshToken(cookieToken, request);
        if (StringUtils.hasText(refreshToken)) {
            authenticationService.logout(refreshToken);
        }
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, refreshCookieService.clear().toString())
            .build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authenticationService.currentUser(UUID.fromString(jwt.getSubject()));
    }

    @PatchMapping("/me")
    public UserResponse updateProfile(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody ProfileUpdateRequest request
    ) {
        return authenticationService.updateProfile(
            UUID.fromString(jwt.getSubject()),
            request
        );
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> updatePassword(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody PasswordUpdateRequest request
    ) {
        authenticationService.updatePassword(
            UUID.fromString(jwt.getSubject()),
            request
        );
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<AuthResponse> authenticated(HttpStatus status, AuthResponse response) {
        return ResponseEntity.status(status)
            .header(
                HttpHeaders.SET_COOKIE,
                refreshCookieService.issue(response.refreshToken()).toString()
            )
            .body(response);
    }

    private String requireRefreshToken(String cookieToken, RefreshTokenRequest request) {
        String refreshToken = resolveRefreshToken(cookieToken, request);
        if (!StringUtils.hasText(refreshToken)) {
            throw new UnauthorizedException("Refresh token is required");
        }
        return refreshToken;
    }

    private String resolveRefreshToken(String cookieToken, RefreshTokenRequest request) {
        if (StringUtils.hasText(cookieToken)) {
            return cookieToken;
        }
        return request == null ? null : request.refreshToken();
    }
}
