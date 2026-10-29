package com.stoxsim.auth.google;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import com.stoxsim.common.error.UnauthorizedException;

@Component
@ConditionalOnProperty(name = "stoxsim.auth.google.enabled", havingValue = "true")
public class GoogleIdentityVerifier {
    private final JwtDecoder decoder;
    private final String clientId;
    private final List<String> androidClientIds;

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleIdentityVerifier(@Value("${stoxsim.auth.google.client-id:}") String clientId,
            @Value("${stoxsim.auth.google.android-client-ids:}") String androidClientIds) {
        this(clientId, java.util.Arrays.stream(androidClientIds.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList(),
            NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build());
    }
    public GoogleIdentityVerifier(String clientId) { this(clientId, ""); }

    GoogleIdentityVerifier(String clientId, NimbusJwtDecoder decoder) { this(clientId, List.of(), decoder); }
    GoogleIdentityVerifier(String clientId, List<String> androidClientIds, NimbusJwtDecoder decoder) {
        if (clientId == null || !clientId.endsWith(".apps.googleusercontent.com")) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID must be a Google web client ID when Google sign-in is enabled");
        }
        this.clientId = clientId;
        if (androidClientIds.stream().anyMatch(id -> !id.endsWith(".apps.googleusercontent.com"))) {
            throw new IllegalStateException("GOOGLE_ANDROID_CLIENT_IDS must contain Google Android OAuth client IDs");
        }
        this.androidClientIds = List.copyOf(androidClientIds);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), jwt ->
            validClaims(jwt, clientId, androidClientIds) ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        this.decoder = decoder;
    }

    static boolean validClaims(Jwt jwt, String clientId) { return validClaims(jwt, clientId, List.of()); }
    private static boolean validClaims(Jwt jwt, String clientId, List<String> androidIds) {
        String issuer = jwt.getClaimAsString("iss");
        String subject = jwt.getSubject();
        String azp = jwt.getClaimAsString("azp");
        return List.of("https://accounts.google.com", "accounts.google.com").contains(issuer == null ? "" : issuer)
            && jwt.getAudience() != null && jwt.getAudience().contains(clientId)
            && (azp == null ? jwt.getAudience().size() == 1 : (clientId.equals(azp) || androidIds.contains(azp)))
            && subject != null && !subject.isBlank() && subject.length() <= 255
            && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
            && !jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(60));
    }

    public String verify(String credential, String nonce) { return identity(credential, nonce, false).subject(); }
    public Identity identity(String credential, String nonce, boolean nativeClient) {
        if (nativeClient && !nativeEnabled()) throw new UnauthorizedException("Google sign-in is not configured for Android");
        try {
            Jwt jwt = decoder.decode(credential);
            if (nonce == null || !nonce.equals(jwt.getClaimAsString("nonce"))) throw new UnauthorizedException("Google sign-in expired. Please try again.");
            if (!nativeClient && !validClaims(jwt, clientId)) throw new UnauthorizedException("Invalid Google client");
            String email = jwt.getClaimAsString("email");
            boolean verified = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));
            String domain = jwt.getClaimAsString("hd");
            boolean authoritative = verified && email != null && (email.toLowerCase(java.util.Locale.ROOT).endsWith("@gmail.com")
                || (domain != null && !domain.isBlank()));
            return new Identity(jwt.getSubject(), email, authoritative);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new UnauthorizedException("Google sign-in could not be verified");
        }
    }

    public record Identity(String subject, String email, boolean authoritativeEmail) {}
    public boolean nativeEnabled() { return !androidClientIds.isEmpty(); }
    public String clientId() { return clientId; }
}
