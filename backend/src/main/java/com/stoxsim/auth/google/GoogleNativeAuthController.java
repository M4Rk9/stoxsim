package com.stoxsim.auth.google;

import java.util.Map;
import java.util.UUID;
import com.stoxsim.auth.api.dto.AuthResponse;
import com.stoxsim.auth.service.RefreshCookieService;
import com.stoxsim.auth.validation.Utf8Password;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Native credentials never bypass the browser's cookie/origin checks. No Google token is persisted. */
@RestController
@RequestMapping(value = "/api/v1/auth/google/native", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnProperty(name = "stoxsim.auth.google.enabled", havingValue = "true")
public class GoogleNativeAuthController {
    private final GoogleLoginChallenge challenges;
    private final GoogleIdentityVerifier verifier;
    private final GoogleAccountService accounts;
    private final RefreshCookieService cookies;
    public GoogleNativeAuthController(GoogleLoginChallenge challenges, GoogleIdentityVerifier verifier,
            GoogleAccountService accounts, RefreshCookieService cookies) {
        this.challenges = challenges; this.verifier = verifier; this.accounts = accounts; this.cookies = cookies;
    }
    private void requireNative(String origin) {
        if (origin != null) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Use browser Google sign-in");
        if (!verifier.nativeEnabled()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Android Google sign-in is unavailable");
    }
    @PostMapping(value = "/config", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> config(@RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin) {
        requireNative(origin);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("clientId", verifier.clientId()));
    }
    @PostMapping(value = "/challenge", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GoogleLoginChallenge.Challenge> challenge(@RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin) {
        requireNative(origin);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(challenges.createNative());
    }
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody Credential request,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
            @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        requireNative(origin);
        String subject = identity(request.credential(), request.binding()).subject();
        return session(accounts.login(subject, userAgent), HttpStatus.OK);
    }
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody Registration request,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin,
            @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        requireNative(origin);
        return session(accounts.register(identity(request.credential(), request.binding()), request.displayName(), request.termsAccepted(), userAgent), HttpStatus.CREATED);
    }
    @PostMapping(value = "/password", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> password(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody InitialPassword request,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin) {
        requireNative(origin);
        accounts.setInitialPassword(UUID.fromString(jwt.getSubject()), identity(request.credential(), request.binding()).subject(), request.newPassword());
        return cleared();
    }
    @PostMapping(value = "/delete", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Deletion request,
            @RequestHeader(name = HttpHeaders.ORIGIN, required = false) String origin) {
        requireNative(origin);
        accounts.deleteWithGoogle(UUID.fromString(jwt.getSubject()), identity(request.credential(), request.binding()).subject());
        return cleared();
    }
    private GoogleIdentityVerifier.Identity identity(String credential, String binding) {
        return verifier.identity(credential, challenges.consumeNative(binding), true);
    }
    private ResponseEntity<AuthResponse> session(AuthResponse response, HttpStatus status) {
        // Android's existing secure-cookie parser stores only the rotating StoxSim refresh secret in Keystore.
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE, cookies.issue(response.refreshToken()).toString()).body(response);
    }
    private ResponseEntity<Void> cleared() {
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
    }
    public record Credential(@NotBlank @Size(max = 16384) String credential, @NotBlank @Size(min = 43, max = 43) String binding) {}
    public record Registration(@NotBlank @Size(max = 16384) String credential, @NotBlank @Size(min = 43, max = 43) String binding,
        @NotBlank @Size(min = 2, max = 100) String displayName,
        @AssertTrue(message = "You must accept the Terms and Privacy Notice") boolean termsAccepted) {}
    public record InitialPassword(@NotBlank @Size(max = 16384) String credential, @NotBlank @Size(min = 43, max = 43) String binding,
        @NotBlank @Size(min = 8, max = 72) @Utf8Password String newPassword) {}
    public record Deletion(@NotBlank @Size(max = 16384) String credential, @NotBlank @Size(min = 43, max = 43) String binding,
        @AssertTrue(message = "Confirm permanent account deletion") boolean confirmed) {}
}
