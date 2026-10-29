package com.stoxsim.auth.google;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.stoxsim.auth.api.dto.AuthResponse;
import com.stoxsim.auth.config.AuthProperties;
import com.stoxsim.auth.service.RefreshCookieService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/auth/google")
@ConditionalOnProperty(name = "stoxsim.auth.google.enabled", havingValue = "true")
public class GoogleAuthController {
    private final GoogleLoginChallenge challenges;
    private final GoogleIdentityVerifier verifier;
    private final GoogleAccountService accounts;
    private final RefreshCookieService refreshCookies;
    private final AuthProperties properties;
    private final String frontendUrl;
    public GoogleAuthController(GoogleLoginChallenge challenges, GoogleIdentityVerifier verifier, GoogleAccountService accounts,
            RefreshCookieService refreshCookies, AuthProperties properties, @Value("${stoxsim.frontend-url}") String frontendUrl) {
        this.challenges = challenges; this.verifier = verifier; this.accounts = accounts;
        this.refreshCookies = refreshCookies; this.properties = properties; this.frontendUrl = frontendUrl;
    }

    @PostMapping("/config")
    public Map<String, String> config() { return Map.of("clientId", verifier.clientId()); }

    @PostMapping("/challenge")
    public ResponseEntity<Map<String, String>> challenge(@RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin) {
        requireOrigin(origin);
        var challenge = challenges.create();
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header(HttpHeaders.SET_COOKIE, cookie(challenge.binding(), GoogleLoginChallenge.TTL).toString())
            .body(Map.of("nonce", challenge.nonce()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody Credential request,
            @CookieValue(name = GoogleLoginChallenge.COOKIE, required = false) String binding,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
            @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        requireOrigin(origin);
        String subject = verifier.verify(request.credential(), challenges.consume(binding));
        AuthResponse response = accounts.login(subject, userAgent);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
            .header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString(), refreshCookies.issue(response.refreshToken()).toString())
            .body(response);
    }

    @GetMapping("/link")
    public Map<String, Boolean> linked(@AuthenticationPrincipal Jwt jwt) { return Map.of("linked", accounts.linked(userId(jwt))); }

    @PostMapping("/link")
    public ResponseEntity<Void> link(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Link request,
            @CookieValue(name = GoogleLoginChallenge.COOKIE, required = false) String binding,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin) {
        requireOrigin(origin);
        accounts.link(userId(jwt), request.password(), verifier.verify(request.credential(), challenges.consume(binding)));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString()).build();
    }

    @PostMapping("/unlink")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Password request,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin) {
        requireOrigin(origin);
        accounts.unlink(userId(jwt), request.password());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString()).build();
    }

    private void requireOrigin(String origin) {
        if (!frontendUrl.equals(origin)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Untrusted sign-in origin");
    }
    private UUID userId(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private ResponseCookie cookie(String value, Duration age) {
        return ResponseCookie.from(GoogleLoginChallenge.COOKIE, value).httpOnly(true).secure(properties.isCookieSecure())
            .sameSite("Strict").path("/api/v1/auth/google").maxAge(age).build();
    }
    public record Credential(@NotBlank @Size(max = 16384) String credential) {}
    public record Link(@NotBlank @Size(max = 16384) String credential, @NotBlank @Size(max = 1000) String password) {}
    public record Password(@NotBlank @Size(max = 1000) String password) {}
}
