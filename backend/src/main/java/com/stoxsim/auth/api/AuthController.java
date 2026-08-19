package com.stoxsim.auth.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.stoxsim.auth.api.dto.AccountEventResponse;
import com.stoxsim.auth.api.dto.AuthResponse;
import com.stoxsim.auth.api.dto.DeleteAccountRequest;
import com.stoxsim.auth.api.dto.EmailRequest;
import com.stoxsim.auth.api.dto.LoginRequest;
import com.stoxsim.auth.api.dto.PasswordUpdateRequest;
import com.stoxsim.auth.api.dto.ProfileUpdateRequest;
import com.stoxsim.auth.api.dto.RefreshTokenRequest;
import com.stoxsim.auth.api.dto.RegisterRequest;
import com.stoxsim.auth.api.dto.ResetPasswordRequest;
import com.stoxsim.auth.api.dto.SessionResponse;
import com.stoxsim.auth.api.dto.TokenRequest;
import com.stoxsim.auth.api.dto.UserResponse;
import com.stoxsim.auth.service.AccountLifecycleService;
import com.stoxsim.auth.service.AuthenticationService;
import com.stoxsim.auth.service.RefreshCookieService;
import com.stoxsim.common.error.UnauthorizedException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final AccountLifecycleService lifecycleService;
    private final RefreshCookieService refreshCookieService;

    public AuthController(
        AuthenticationService authenticationService,
        AccountLifecycleService lifecycleService,
        RefreshCookieService refreshCookieService
    ) {
        this.authenticationService = authenticationService;
        this.lifecycleService = lifecycleService;
        this.refreshCookieService = refreshCookieService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
        @Valid @RequestBody RegisterRequest request,
        @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        return authenticated(
            HttpStatus.CREATED,
            authenticationService.register(request, userAgent)
        );
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
        @Valid @RequestBody LoginRequest request,
        @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        return authenticated(
            HttpStatus.OK,
            authenticationService.login(request, userAgent)
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
        @CookieValue(name = RefreshCookieService.COOKIE_NAME, required = false) String cookieToken,
        @Valid @RequestBody(required = false) RefreshTokenRequest request,
        @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent
    ) {
        AuthResponse response = authenticationService.refresh(
            requireRefreshToken(cookieToken, request),
            userAgent
        );
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
        return clearedCookie(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody EmailRequest request) {
        lifecycleService.requestPasswordReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> resetPassword(
        @Valid @RequestBody ResetPasswordRequest request
    ) {
        lifecycleService.resetPassword(request.token(), request.newPassword());
        return clearedCookie(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/email-verification/confirm")
    public ResponseEntity<Void> confirmEmail(@Valid @RequestBody TokenRequest request) {
        lifecycleService.verifyEmail(request.token());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email-verification/resend")
    public ResponseEntity<Void> resendVerification(@AuthenticationPrincipal Jwt jwt) {
        lifecycleService.sendVerification(userId(jwt));
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authenticationService.currentUser(userId(jwt));
    }

    @PatchMapping("/me")
    public UserResponse updateProfile(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody ProfileUpdateRequest request
    ) {
        return authenticationService.updateProfile(userId(jwt), request);
    }

    @PatchMapping("/me/password")
    public ResponseEntity<Void> updatePassword(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody PasswordUpdateRequest request
    ) {
        authenticationService.updatePassword(userId(jwt), request);
        return clearedCookie(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/sessions")
    public List<SessionResponse> sessions(
        @AuthenticationPrincipal Jwt jwt,
        @CookieValue(name = RefreshCookieService.COOKIE_NAME, required = false) String cookieToken
    ) {
        return lifecycleService.sessions(userId(jwt), cookieToken);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID sessionId
    ) {
        lifecycleService.revokeSession(userId(jwt), sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@AuthenticationPrincipal Jwt jwt) {
        lifecycleService.logoutAll(userId(jwt));
        return clearedCookie(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/events")
    public List<AccountEventResponse> events(@AuthenticationPrincipal Jwt jwt) {
        return lifecycleService.events(userId(jwt));
    }

    @PostMapping("/me/export")
    public ResponseEntity<Map<String, Object>> exportAccount(
        @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=stoxsim-account-export.json"
            )
            .body(lifecycleService.exportAccount(userId(jwt)));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody DeleteAccountRequest request
    ) {
        lifecycleService.deleteAccount(userId(jwt), request.password());
        return clearedCookie(HttpStatus.NO_CONTENT);
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private ResponseEntity<AuthResponse> authenticated(HttpStatus status, AuthResponse response) {
        return ResponseEntity.status(status)
            .header(
                HttpHeaders.SET_COOKIE,
                refreshCookieService.issue(response.refreshToken()).toString()
            )
            .body(response);
    }

    private ResponseEntity<Void> clearedCookie(HttpStatus status) {
        return ResponseEntity.status(status)
            .header(HttpHeaders.SET_COOKIE, refreshCookieService.clear().toString())
            .build();
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
