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

    @org.springframework.beans.factory.annotation.Autowired
    public GoogleIdentityVerifier(@Value("${stoxsim.auth.google.client-id:}") String clientId) {
        this(clientId, NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs").build());
    }

    GoogleIdentityVerifier(String clientId, NimbusJwtDecoder decoder) {
        if (clientId == null || !clientId.endsWith(".apps.googleusercontent.com")) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID must be a Google web client ID when Google sign-in is enabled");
        }
        this.clientId = clientId;
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(), jwt ->
            validClaims(jwt, clientId) ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        this.decoder = decoder;
    }

    static boolean validClaims(Jwt jwt, String clientId) {
        String issuer = jwt.getClaimAsString("iss");
        String subject = jwt.getSubject();
        String azp = jwt.getClaimAsString("azp");
        return List.of("https://accounts.google.com", "accounts.google.com").contains(issuer == null ? "" : issuer)
            && jwt.getAudience() != null && jwt.getAudience().contains(clientId)
            && (azp == null ? jwt.getAudience().size() == 1 : clientId.equals(azp))
            && subject != null && !subject.isBlank() && subject.length() <= 255
            && jwt.getExpiresAt() != null && jwt.getIssuedAt() != null
            && !jwt.getIssuedAt().isAfter(Instant.now().plusSeconds(60));
    }

    public String verify(String credential, String nonce) {
        try {
            Jwt jwt = decoder.decode(credential);
            if (nonce == null || !nonce.equals(jwt.getClaimAsString("nonce"))) throw new UnauthorizedException("Google sign-in expired. Please try again.");
            return jwt.getSubject();
        } catch (JwtException | IllegalArgumentException exception) {
            throw new UnauthorizedException("Google sign-in could not be verified");
        }
    }

    public String clientId() { return clientId; }
}
